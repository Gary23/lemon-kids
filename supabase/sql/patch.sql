-- ============================================================
-- 柠檬小管家 Supabase 补丁 — 在 SQL Editor 中执行
-- ============================================================

-- 1. families 表添加 invite_code 邀请码字段
ALTER TABLE families ADD COLUMN IF NOT EXISTS invite_code TEXT;
UPDATE families SET invite_code = substring(md5(random()::text), 1, 6) WHERE invite_code IS NULL;
ALTER TABLE families ALTER COLUMN invite_code SET NOT NULL;
ALTER TABLE families ADD CONSTRAINT families_invite_code_unique UNIQUE (invite_code);

-- 2. 完成任务 → 积分到账 (事务函数)
CREATE OR REPLACE FUNCTION complete_task(p_task_id UUID, p_child_id UUID)
RETURNS void AS $$
DECLARE
    v_task tasks%ROWTYPE;
    v_user users%ROWTYPE;
    v_new_balance INT;
BEGIN
    SELECT * INTO v_task FROM tasks WHERE id = p_task_id FOR UPDATE;
    IF v_task.status != 'pending' THEN
        RAISE EXCEPTION 'Task status is not pending';
    END IF;

    SELECT * INTO v_user FROM users WHERE uid = p_child_id FOR UPDATE;
    v_new_balance := v_user.total_points + v_task.reward_points;

    UPDATE tasks SET status = 'done', completed_at = now()
        WHERE id = p_task_id;
    UPDATE users SET total_points = v_new_balance
        WHERE uid = p_child_id;
    INSERT INTO point_records (family_id, child_id, amount, balance, reason, type, related_task_id)
        VALUES (v_task.family_id, p_child_id, v_task.reward_points,
                v_new_balance, '完成任务：' || v_task.title,
                'task_complete', p_task_id);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 3. 兑换奖励 → 扣减积分 (事务函数)
CREATE OR REPLACE FUNCTION redeem_reward(p_reward_id UUID, p_child_id UUID)
RETURNS void AS $$
DECLARE
    v_reward rewards%ROWTYPE;
    v_user users%ROWTYPE;
    v_new_balance INT;
BEGIN
    SELECT * INTO v_reward FROM rewards WHERE id = p_reward_id FOR UPDATE;
    IF NOT v_reward.is_active THEN
        RAISE EXCEPTION 'Reward is not active';
    END IF;

    SELECT * INTO v_user FROM users WHERE uid = p_child_id FOR UPDATE;
    IF v_user.total_points < v_reward.cost THEN
        RAISE EXCEPTION 'Insufficient points';
    END IF;

    v_new_balance := v_user.total_points - v_reward.cost;
    UPDATE users SET total_points = v_new_balance WHERE uid = p_child_id;

    INSERT INTO point_records (family_id, child_id, amount, balance, reason, type, related_reward_id)
        VALUES (v_reward.family_id, p_child_id, -v_reward.cost,
                v_new_balance, '兑换奖励：' || v_reward.title,
                'reward_redeem', p_reward_id);

    IF NOT v_reward.repeatable THEN
        UPDATE rewards SET is_active = false WHERE id = p_reward_id;
    END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 4. 启用所有表的 RLS (如果尚未启用)
DO $$
BEGIN
    EXECUTE 'ALTER TABLE families ENABLE ROW LEVEL SECURITY';
    EXECUTE 'ALTER TABLE users ENABLE ROW LEVEL SECURITY';
    EXECUTE 'ALTER TABLE tasks ENABLE ROW LEVEL SECURITY';
    EXECUTE 'ALTER TABLE messages ENABLE ROW LEVEL SECURITY';
    EXECUTE 'ALTER TABLE point_records ENABLE ROW LEVEL SECURITY';
    EXECUTE 'ALTER TABLE rewards ENABLE ROW LEVEL SECURITY';
    EXECUTE 'ALTER TABLE app_usage ENABLE ROW LEVEL SECURITY';
    EXECUTE 'ALTER TABLE app_limits ENABLE ROW LEVEL SECURITY';
END $$;

-- 5. 删除旧的 RLS 策略（如果存在），重建正确的
DROP POLICY IF EXISTS "users_self_access" ON users;
DROP POLICY IF EXISTS "tasks_family_access" ON tasks;
DROP POLICY IF EXISTS "messages_family_access" ON messages;
DROP POLICY IF EXISTS "point_records_family_access" ON point_records;
DROP POLICY IF EXISTS "rewards_select_family" ON rewards;
DROP POLICY IF EXISTS "rewards_insert_parent" ON rewards;
DROP POLICY IF EXISTS "rewards_update_parent" ON rewards;
DROP POLICY IF EXISTS "app_usage_family_access" ON app_usage;
DROP POLICY IF EXISTS "app_limits_family_access" ON app_limits;
DROP POLICY IF EXISTS "families_access" ON families;

-- 用户：只能读写自己的记录
CREATE POLICY "users_self_access" ON users
    FOR ALL USING (uid = auth.uid());

-- 家庭数据：只能读取自己家庭的数据
CREATE POLICY "families_access" ON families
    FOR ALL USING (id IN (
        SELECT family_id FROM users WHERE uid = auth.uid()
    ));

-- 任务：按家庭隔离
CREATE POLICY "tasks_family_access" ON tasks
    FOR ALL USING (family_id IN (
        SELECT family_id FROM users WHERE uid = auth.uid()
    ));

-- 消息：按家庭隔离
CREATE POLICY "messages_family_access" ON messages
    FOR ALL USING (family_id IN (
        SELECT family_id FROM users WHERE uid = auth.uid()
    ));

-- 积分流水：按家庭隔离
CREATE POLICY "point_records_family_access" ON point_records
    FOR ALL USING (family_id IN (
        SELECT family_id FROM users WHERE uid = auth.uid()
    ));

-- 奖励：所有人可查看，只有家长可创建/修改
CREATE POLICY "rewards_select_family" ON rewards FOR SELECT
    USING (family_id IN (
        SELECT family_id FROM users WHERE uid = auth.uid()
    ));
CREATE POLICY "rewards_insert_parent" ON rewards FOR INSERT
    WITH CHECK ((SELECT role FROM users WHERE uid = auth.uid()) = 'parent');
CREATE POLICY "rewards_update_parent" ON rewards FOR UPDATE
    USING (family_id IN (
        SELECT family_id FROM users WHERE uid = auth.uid() AND role = 'parent'
    ));

-- 使用记录：按家庭隔离
CREATE POLICY "app_usage_family_access" ON app_usage
    FOR ALL USING (family_id IN (
        SELECT family_id FROM users WHERE uid = auth.uid()
    ));

-- 限时规则：按家庭隔离
CREATE POLICY "app_limits_family_access" ON app_limits
    FOR ALL USING (family_id IN (
        SELECT family_id FROM users WHERE uid = auth.uid()
    ));

-- 6. app_limits 表新增单次时长和冷却间隔字段
ALTER TABLE app_limits ADD COLUMN IF NOT EXISTS single_session_minutes INT DEFAULT 0;
ALTER TABLE app_limits ADD COLUMN IF NOT EXISTS cooldown_minutes INT DEFAULT 0;
ALTER TABLE app_limits ALTER COLUMN daily_limit_minutes SET DEFAULT 999;

-- 7. kid 端设备状态上报日志
CREATE TABLE IF NOT EXISTS device_status_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES families(id),
    child_id UUID NOT NULL REFERENCES users(uid),
    event_type TEXT NOT NULL DEFAULT 'heartbeat',
    accessibility_enabled BOOLEAN DEFAULT false,
    limit_service_running BOOLEAN DEFAULT false,
    app_process_alive BOOLEAN DEFAULT true,
    battery_ignoring_optimizations BOOLEAN DEFAULT false,
    message TEXT DEFAULT '',
    created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_device_status_child_time ON device_status_logs(child_id, created_at DESC);
ALTER TABLE device_status_logs ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "device_status_logs_family_access" ON device_status_logs;
CREATE POLICY "device_status_logs_family_access" ON device_status_logs
    FOR ALL USING (family_id IN (
        SELECT family_id FROM users WHERE uid = auth.uid()
    ));
