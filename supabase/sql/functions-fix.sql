-- 在 Supabase SQL Editor 中执行
-- 如果之前执行过，先删除旧函数
DROP FUNCTION IF EXISTS complete_task(UUID, UUID);
DROP FUNCTION IF EXISTS redeem_reward(UUID, UUID);

-- 完成任务 → 积分到账
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
$$ LANGUAGE plpgsql;

-- 兑换奖励 → 扣减积分
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
$$ LANGUAGE plpgsql;
