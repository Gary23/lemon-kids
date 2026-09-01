-- 清空所有家庭的任务与孩子积分。请在 Supabase SQL Editor 中一次性执行。
-- 同时清理积分流水，避免清零后的积分余额与历史流水不一致。
BEGIN;

DELETE FROM point_records
WHERE child_id IN (SELECT uid FROM users WHERE role = 'child');

DELETE FROM tasks;

UPDATE users
SET total_points = 0
WHERE role = 'child';

COMMIT;
