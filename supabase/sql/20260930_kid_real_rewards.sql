-- 依赖 20260930_parent_reward_catalog.sql。先在测试环境核对，再于线上执行。
BEGIN;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'rewards' AND column_name = 'is_featured') OR
     NOT EXISTS (SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'rewards' AND column_name = 'cover_key') THEN
    RAISE EXCEPTION '请先部署 20260930_parent_reward_catalog.sql';
  END IF;
END $$;

ALTER TABLE public.point_records DROP CONSTRAINT IF EXISTS point_records_type_check;
ALTER TABLE public.point_records ADD CONSTRAINT point_records_type_check
  CHECK (type IN ('task_complete', 'task_expired', 'task_rejected', 'reward_redeem', 'reward_refund', 'manual'));

CREATE TABLE IF NOT EXISTS public.reward_redemptions (
  id uuid PRIMARY KEY,
  family_id uuid NOT NULL REFERENCES public.families(id),
  child_id uuid NOT NULL REFERENCES public.users(uid),
  reward_id uuid NOT NULL REFERENCES public.rewards(id),
  title_snapshot text NOT NULL,
  cost_snapshot integer NOT NULL CHECK (cost_snapshot > 0),
  repeatable_snapshot boolean NOT NULL,
  status text NOT NULL DEFAULT 'held' CHECK (status IN ('held', 'used', 'cancelled')),
  redeemed_at timestamptz NOT NULL DEFAULT now(),
  used_at timestamptz,
  cancelled_at timestamptz,
  redeem_point_record_id uuid UNIQUE REFERENCES public.point_records(id),
  refund_point_record_id uuid UNIQUE REFERENCES public.point_records(id),
  CONSTRAINT reward_redemptions_state_times CHECK (
    (status = 'held' AND used_at IS NULL AND cancelled_at IS NULL) OR
    (status = 'used' AND used_at IS NOT NULL AND cancelled_at IS NULL) OR
    (status = 'cancelled' AND used_at IS NULL AND cancelled_at IS NOT NULL)
  )
);
CREATE INDEX IF NOT EXISTS reward_redemptions_child_time
  ON public.reward_redemptions(child_id, redeemed_at DESC);
CREATE INDEX IF NOT EXISTS reward_redemptions_reward_status
  ON public.reward_redemptions(reward_id, status);
CREATE UNIQUE INDEX IF NOT EXISTS reward_redemptions_one_time_claim
  ON public.reward_redemptions(reward_id)
  WHERE NOT repeatable_snapshot AND status <> 'cancelled';

ALTER TABLE public.point_records ADD COLUMN IF NOT EXISTS related_redemption_id uuid
  REFERENCES public.reward_redemptions(id);
CREATE UNIQUE INDEX IF NOT EXISTS point_records_one_refund_per_redemption
  ON public.point_records(related_redemption_id) WHERE type = 'reward_refund';

-- 旧 users/point_records 的通用 RLS 曾允许本人直接写积分。业务 RPC 以
-- SECURITY DEFINER 运行；客户端的 authenticated 角色不能绕过事务修改余额或奖励流水。
CREATE OR REPLACE FUNCTION public.guard_direct_reward_points()
RETURNS trigger LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  IF current_user IN ('authenticated', 'anon') AND
     NEW.total_points IS DISTINCT FROM OLD.total_points THEN
    RAISE EXCEPTION 'Points can only be changed by an approved operation';
  END IF;
  RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS users_guard_direct_reward_points ON public.users;
CREATE TRIGGER users_guard_direct_reward_points BEFORE UPDATE OF total_points ON public.users
  FOR EACH ROW EXECUTE FUNCTION public.guard_direct_reward_points();

CREATE OR REPLACE FUNCTION public.guard_reward_point_record_writes()
RETURNS trigger LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  IF current_user IN ('authenticated', 'anon') THEN
    IF TG_OP = 'INSERT' THEN
      IF NEW.type IN ('reward_redeem', 'reward_refund') OR NEW.related_redemption_id IS NOT NULL THEN
        RAISE EXCEPTION 'Reward point records can only be changed by an approved operation';
      END IF;
    ELSIF TG_OP = 'DELETE' THEN
      IF OLD.type IN ('reward_redeem', 'reward_refund') OR OLD.related_redemption_id IS NOT NULL THEN
        RAISE EXCEPTION 'Reward point records can only be changed by an approved operation';
      END IF;
    ELSE
      IF OLD.type IN ('reward_redeem', 'reward_refund') OR NEW.type IN ('reward_redeem', 'reward_refund') OR
         OLD.related_redemption_id IS NOT NULL OR NEW.related_redemption_id IS NOT NULL THEN
        RAISE EXCEPTION 'Reward point records can only be changed by an approved operation';
      END IF;
    END IF;
  END IF;
  IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
  RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS point_records_guard_reward_writes ON public.point_records;
CREATE TRIGGER point_records_guard_reward_writes
  BEFORE INSERT OR UPDATE OR DELETE ON public.point_records
  FOR EACH ROW EXECUTE FUNCTION public.guard_reward_point_record_writes();

ALTER TABLE public.reward_redemptions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS reward_redemptions_read_self ON public.reward_redemptions;
CREATE POLICY reward_redemptions_read_self ON public.reward_redemptions
  FOR SELECT TO authenticated USING (child_id = auth.uid());
REVOKE ALL ON public.reward_redemptions FROM PUBLIC, anon, authenticated;
GRANT SELECT ON public.reward_redemptions TO authenticated;

-- 孩子不能通过旧的家庭通用流水策略读取兄弟姐妹的兑换明细。
DROP POLICY IF EXISTS point_records_family_access ON public.point_records;
DROP POLICY IF EXISTS point_records_select_family ON public.point_records;
DROP POLICY IF EXISTS point_records_delete_parent ON public.point_records;
CREATE POLICY point_records_select_family ON public.point_records FOR SELECT TO authenticated
  USING (EXISTS (
    SELECT 1 FROM public.users u WHERE u.uid = auth.uid()
      AND u.family_id = point_records.family_id
      AND (u.role = 'parent' OR point_records.child_id = u.uid)
  ));
CREATE POLICY point_records_delete_parent ON public.point_records FOR DELETE TO authenticated
  USING (EXISTS (
    SELECT 1 FROM public.users u WHERE u.uid = auth.uid()
      AND u.family_id = point_records.family_id AND u.role = 'parent'
  ));

-- 奖励类型已有兑换后不可修改，包含旧版积分流水；状态仍由家长管理。
CREATE OR REPLACE FUNCTION public.guard_reward_repeatability()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF NEW.repeatable IS DISTINCT FROM OLD.repeatable AND
    (EXISTS (SELECT 1 FROM public.reward_redemptions WHERE reward_id = OLD.id) OR
     EXISTS (SELECT 1 FROM public.point_records WHERE related_reward_id = OLD.id AND type = 'reward_redeem')) THEN
    RAISE EXCEPTION '已有兑换记录，不能修改奖励类型';
  END IF;
  RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS rewards_guard_repeatability ON public.rewards;
CREATE TRIGGER rewards_guard_repeatability BEFORE UPDATE ON public.rewards
  FOR EACH ROW EXECUTE FUNCTION public.guard_reward_repeatability();

-- 旧的两参数函数无请求 ID，必须删除所有签名入口。
DROP FUNCTION IF EXISTS public.redeem_reward(uuid, uuid);
CREATE FUNCTION public.redeem_reward(p_reward_id uuid, p_child_id uuid, p_request_id uuid)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_reward public.rewards%ROWTYPE;
  v_user public.users%ROWTYPE;
  v_existing public.reward_redemptions%ROWTYPE;
  v_point_id uuid;
  v_balance integer;
BEGIN
  IF auth.uid() IS DISTINCT FROM p_child_id OR p_request_id IS NULL OR
    NOT EXISTS (SELECT 1 FROM public.users WHERE uid = auth.uid() AND role = 'child') THEN
    RAISE EXCEPTION 'Only the child can redeem';
  END IF;
  SELECT * INTO v_reward FROM public.rewards WHERE id = p_reward_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Reward not found'; END IF;
  SELECT * INTO v_existing FROM public.reward_redemptions WHERE id = p_request_id;
  IF FOUND THEN
    IF v_existing.child_id <> p_child_id OR v_existing.reward_id <> p_reward_id THEN
      RAISE EXCEPTION 'Request ID belongs to another redemption';
    END IF;
    RETURN v_existing.id;
  END IF;
  SELECT * INTO v_user FROM public.users WHERE uid = p_child_id FOR UPDATE;
  IF v_user.family_id IS DISTINCT FROM v_reward.family_id THEN
    RAISE EXCEPTION 'Reward is outside current family';
  END IF;
  IF NOT v_reward.is_active THEN RAISE EXCEPTION 'Reward is inactive'; END IF;
  IF NOT v_reward.repeatable AND (
    EXISTS (SELECT 1 FROM public.reward_redemptions
            WHERE reward_id = v_reward.id AND status <> 'cancelled') OR
    EXISTS (SELECT 1 FROM public.point_records
            WHERE related_reward_id = v_reward.id AND type = 'reward_redeem'
              AND related_redemption_id IS NULL)
  ) THEN RAISE EXCEPTION 'One-time reward already redeemed'; END IF;
  IF v_user.total_points < v_reward.cost THEN RAISE EXCEPTION 'Insufficient points'; END IF;
  v_balance := v_user.total_points - v_reward.cost;
  UPDATE public.users SET total_points = v_balance WHERE uid = p_child_id;
  INSERT INTO public.reward_redemptions
    (id, family_id, child_id, reward_id, title_snapshot, cost_snapshot, repeatable_snapshot)
  VALUES (p_request_id, v_reward.family_id, p_child_id, v_reward.id,
          v_reward.title, v_reward.cost, v_reward.repeatable);
  INSERT INTO public.point_records
    (family_id, child_id, amount, balance, reason, type, related_reward_id, related_redemption_id)
  VALUES (v_reward.family_id, p_child_id, -v_reward.cost, v_balance,
          '兑换奖励：' || v_reward.title, 'reward_redeem', v_reward.id, p_request_id)
  RETURNING id INTO v_point_id;
  UPDATE public.reward_redemptions SET redeem_point_record_id = v_point_id WHERE id = p_request_id;
  RETURN p_request_id;
END $$;

CREATE FUNCTION public.cancel_reward_redemption(p_redemption_id uuid, p_child_id uuid)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_reward_id uuid;
  v_redemption public.reward_redemptions%ROWTYPE;
  v_balance integer;
  v_point_id uuid;
BEGIN
  IF auth.uid() IS DISTINCT FROM p_child_id OR
    NOT EXISTS (SELECT 1 FROM public.users WHERE uid = auth.uid() AND role = 'child') THEN
    RAISE EXCEPTION 'Only the child can cancel a redemption';
  END IF;
  -- 与兑换采用相同的奖励行优先锁序，取消后一次性奖励才能安全释放名额。
  SELECT reward_id INTO v_reward_id FROM public.reward_redemptions
    WHERE id = p_redemption_id AND child_id = p_child_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'Redemption not found'; END IF;
  PERFORM 1 FROM public.rewards WHERE id = v_reward_id FOR UPDATE;
  SELECT * INTO v_redemption FROM public.reward_redemptions
    WHERE id = p_redemption_id AND child_id = p_child_id FOR UPDATE;
  IF v_redemption.status = 'cancelled' THEN RETURN v_redemption.id; END IF;
  IF v_redemption.status <> 'held' THEN RAISE EXCEPTION 'Used reward cannot be cancelled'; END IF;
  SELECT total_points INTO v_balance FROM public.users WHERE uid = p_child_id FOR UPDATE;
  v_balance := v_balance + v_redemption.cost_snapshot;
  UPDATE public.users SET total_points = v_balance WHERE uid = p_child_id;
  INSERT INTO public.point_records
    (family_id, child_id, amount, balance, reason, type, related_reward_id, related_redemption_id)
  VALUES (v_redemption.family_id, p_child_id, v_redemption.cost_snapshot, v_balance,
          '取消兑换：' || v_redemption.title_snapshot, 'reward_refund',
          v_redemption.reward_id, v_redemption.id)
  RETURNING id INTO v_point_id;
  UPDATE public.reward_redemptions
    SET status = 'cancelled', cancelled_at = now(), refund_point_record_id = v_point_id
    WHERE id = p_redemption_id;
  RETURN p_redemption_id;
END $$;

CREATE FUNCTION public.use_reward_redemption(p_redemption_id uuid, p_child_id uuid)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE v_redemption public.reward_redemptions%ROWTYPE;
BEGIN
  IF auth.uid() IS DISTINCT FROM p_child_id OR
    NOT EXISTS (SELECT 1 FROM public.users WHERE uid = auth.uid() AND role = 'child') THEN
    RAISE EXCEPTION 'Only the child can use a redemption';
  END IF;
  SELECT * INTO v_redemption FROM public.reward_redemptions
    WHERE id = p_redemption_id AND child_id = p_child_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Redemption not found'; END IF;
  IF v_redemption.status = 'used' THEN RETURN p_redemption_id; END IF;
  IF v_redemption.status <> 'held' THEN RAISE EXCEPTION 'Cancelled reward cannot be used'; END IF;
  UPDATE public.reward_redemptions SET status = 'used', used_at = now()
    WHERE id = p_redemption_id;
  RETURN p_redemption_id;
END $$;

CREATE FUNCTION public.get_monthly_task_points(p_child_id uuid)
RETURNS integer LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public AS $$
DECLARE v_total integer;
BEGIN
  IF auth.uid() IS DISTINCT FROM p_child_id OR
    NOT EXISTS (SELECT 1 FROM public.users WHERE uid = auth.uid() AND role = 'child') THEN
    RAISE EXCEPTION 'Only the child can read monthly points';
  END IF;
  SELECT COALESCE(sum(amount), 0)::integer INTO v_total FROM public.point_records
    WHERE child_id = p_child_id AND type = 'task_complete'
      AND timestamp >= date_trunc('month', now() AT TIME ZONE 'Asia/Shanghai') AT TIME ZONE 'Asia/Shanghai'
      AND timestamp < (date_trunc('month', now() AT TIME ZONE 'Asia/Shanghai') + interval '1 month') AT TIME ZONE 'Asia/Shanghai';
  RETURN v_total;
END $$;

CREATE FUNCTION public.get_unavailable_one_time_rewards(p_family_id uuid)
RETURNS TABLE(reward_id uuid) LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.users
    WHERE uid = auth.uid() AND role = 'child' AND family_id = p_family_id) THEN
    RAISE EXCEPTION 'Family is not available to current child';
  END IF;
  RETURN QUERY
    SELECT DISTINCT r.id FROM public.rewards r
    WHERE r.family_id = p_family_id AND NOT r.repeatable AND
      (EXISTS (SELECT 1 FROM public.reward_redemptions d
               WHERE d.reward_id = r.id AND d.status <> 'cancelled') OR
       EXISTS (SELECT 1 FROM public.point_records p
               WHERE p.related_reward_id = r.id AND p.type = 'reward_redeem'
                 AND p.related_redemption_id IS NULL));
END $$;

-- 花掉的积分不足时，任务撤销必须拒绝，不能将余额截断为零。
CREATE OR REPLACE FUNCTION public.undo_task_completion(p_task_id uuid, p_child_id uuid)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_task public.tasks%ROWTYPE;
  v_record public.point_records%ROWTYPE;
  v_balance integer;
BEGIN
  IF auth.uid() IS DISTINCT FROM p_child_id OR
     NOT EXISTS (SELECT 1 FROM public.users WHERE uid = auth.uid() AND role = 'child') THEN
    RAISE EXCEPTION 'Only the task owner can undo completion';
  END IF;
  SELECT * INTO v_task FROM public.tasks WHERE id = p_task_id AND deleted_at IS NULL FOR UPDATE;
  IF NOT FOUND OR v_task.child_id <> p_child_id OR v_task.status NOT IN ('done', 'verified') THEN
    RAISE EXCEPTION 'Task completion cannot be undone';
  END IF;
  SELECT * INTO v_record FROM public.point_records
    WHERE related_task_id = p_task_id AND child_id = p_child_id AND type = 'task_complete'
    ORDER BY timestamp DESC LIMIT 1 FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Task completion record not found'; END IF;
  SELECT total_points INTO v_balance FROM public.users WHERE uid = p_child_id FOR UPDATE;
  IF v_balance < v_record.amount THEN
    RAISE EXCEPTION '积分已用于兑换，余额不足以撤销该任务';
  END IF;
  UPDATE public.tasks SET status = 'pending', completed_at = NULL, verified_at = NULL WHERE id = p_task_id;
  UPDATE public.users SET total_points = v_balance - v_record.amount WHERE uid = p_child_id;
  DELETE FROM public.point_records WHERE id = v_record.id;
END $$;

REVOKE ALL ON FUNCTION public.redeem_reward(uuid, uuid, uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.cancel_reward_redemption(uuid, uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.use_reward_redemption(uuid, uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_monthly_task_points(uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_unavailable_one_time_rewards(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.redeem_reward(uuid, uuid, uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.cancel_reward_redemption(uuid, uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.use_reward_redemption(uuid, uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_monthly_task_points(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_unavailable_one_time_rewards(uuid) TO authenticated;

COMMIT;
