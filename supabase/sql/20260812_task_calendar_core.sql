-- 电子日历核心：重复任务系列与家长驳回的事务性积分回滚。
-- 重复系列会在客户端创建为多个独立日任务，以保留每一天的完成历史。

ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS recurrence_series_id UUID,
    ADD COLUMN IF NOT EXISTS recurrence_type TEXT NOT NULL DEFAULT 'none'
        CHECK (recurrence_type IN ('none', 'daily', 'weekdays', 'weekly')),
    ADD COLUMN IF NOT EXISTS recurrence_weekdays INTEGER[] NOT NULL DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS recurrence_end_date DATE;

CREATE INDEX IF NOT EXISTS idx_tasks_recurrence_series
    ON tasks(recurrence_series_id, due_date);

ALTER TABLE point_records DROP CONSTRAINT IF EXISTS point_records_type_check;
ALTER TABLE point_records ADD CONSTRAINT point_records_type_check
    CHECK (type IN ('task_complete', 'task_expired', 'task_rejected', 'reward_redeem', 'manual'));

-- 默认自动通过：孩子完成后立即到账，状态直接成为 verified；家长仅在必要时驳回。
CREATE OR REPLACE FUNCTION complete_task(p_task_id UUID, p_child_id UUID)
RETURNS void AS $$
DECLARE
    v_task tasks%ROWTYPE;
    v_user users%ROWTYPE;
    v_new_balance INT;
BEGIN
    SELECT * INTO v_task FROM tasks WHERE id = p_task_id FOR UPDATE;
    IF v_task.status != 'pending' OR v_task.child_id != p_child_id THEN
        RAISE EXCEPTION 'Task is not available for completion';
    END IF;

    SELECT * INTO v_user FROM users WHERE uid = p_child_id FOR UPDATE;
    v_new_balance := v_user.total_points + v_task.reward_points;

    UPDATE tasks SET status = 'verified', completed_at = now(), verified_at = now()
        WHERE id = p_task_id;
    UPDATE users SET total_points = v_new_balance WHERE uid = p_child_id;
    INSERT INTO point_records (family_id, child_id, amount, balance, reason, type, related_task_id)
        VALUES (v_task.family_id, p_child_id, v_task.reward_points, v_new_balance,
                '完成任务：' || v_task.title, 'task_complete', p_task_id);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 任务完成默认即通过；只有家长驳回才回退积分。该操作必须通过事务执行。
CREATE OR REPLACE FUNCTION reject_task(p_task_id UUID)
RETURNS void AS $$
DECLARE
    v_task tasks%ROWTYPE;
    v_record point_records%ROWTYPE;
    v_balance INT;
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM users WHERE uid = auth.uid() AND role = 'parent'
    ) THEN
        RAISE EXCEPTION 'Only parents can reject tasks';
    END IF;

    SELECT * INTO v_task FROM tasks WHERE id = p_task_id FOR UPDATE;
    IF NOT EXISTS (
        SELECT 1 FROM users WHERE uid = auth.uid() AND family_id = v_task.family_id
    ) THEN
        RAISE EXCEPTION 'Task is outside current family';
    END IF;
    IF v_task.status NOT IN ('done', 'verified') THEN
        RAISE EXCEPTION 'Only completed tasks can be rejected';
    END IF;

    SELECT * INTO v_record FROM point_records
    WHERE related_task_id = p_task_id AND type = 'task_complete'
    ORDER BY timestamp DESC LIMIT 1 FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Task completion record not found';
    END IF;

    SELECT total_points INTO v_balance FROM users WHERE uid = v_task.child_id FOR UPDATE;
    v_balance := GREATEST(v_balance - v_record.amount, 0);

    UPDATE users SET total_points = v_balance WHERE uid = v_task.child_id;
    UPDATE tasks SET status = 'rejected', verified_at = now() WHERE id = p_task_id;
    INSERT INTO point_records (family_id, child_id, amount, balance, reason, type, related_task_id)
    VALUES (v_task.family_id, v_task.child_id, -v_record.amount, v_balance,
            '家长驳回任务：' || v_task.title, 'task_rejected', p_task_id);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
