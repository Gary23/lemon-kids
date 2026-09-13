-- 远程闹钟日期区间：范围内每天在同一时间响铃，开始日和结束日均包含。
-- 请在已执行 20260906_remote_alarms.sql 的同一 Supabase 项目的 SQL Editor 中执行。

ALTER TABLE alarms ADD COLUMN IF NOT EXISTS end_at TIMESTAMPTZ;

-- 兼容历史单点闹钟：首日和末日相同，仍只响一次。
UPDATE alarms
SET end_at = trigger_at
WHERE end_at IS NULL
   OR (end_at > trigger_at AND end_at <= trigger_at + INTERVAL '1 hour');

ALTER TABLE alarms ALTER COLUMN end_at SET NOT NULL;

ALTER TABLE alarms DROP CONSTRAINT IF EXISTS alarms_end_after_trigger;
ALTER TABLE alarms ADD CONSTRAINT alarms_end_after_trigger
    CHECK (end_at >= trigger_at);

-- 即使今后调用方只更新结束时间，也必须刷新目标 Pad 的待确认投递状态。
DROP TRIGGER IF EXISTS trg_sync_alarm_delivery ON alarms;
CREATE TRIGGER trg_sync_alarm_delivery
AFTER INSERT OR UPDATE OF revision, target_device_id, trigger_at, end_at, timezone, title, message, enabled, requires_confirmation
ON alarms FOR EACH ROW EXECUTE FUNCTION sync_alarm_delivery();
