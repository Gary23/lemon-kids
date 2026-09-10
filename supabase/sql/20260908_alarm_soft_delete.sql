-- 远程闹钟软删除：保留墓碑直到离线 Pad 获取新版本并撤销本地 AlarmManager 记录。
-- 请在已执行 20260906_remote_alarms.sql、20260907_alarm_time_range.sql 的同一 Supabase 项目 SQL Editor 中执行。

ALTER TABLE alarms ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS idx_alarms_deleted_at ON alarms(deleted_at) WHERE deleted_at IS NOT NULL;

-- 删除和关闭都会重新建立投递回执；deleted_at 必须纳入触发字段，不能让墓碑漏下发。
DROP TRIGGER IF EXISTS trg_sync_alarm_delivery ON alarms;
CREATE TRIGGER trg_sync_alarm_delivery
AFTER INSERT OR UPDATE OF revision, target_device_id, trigger_at, end_at, timezone, title, message,
    enabled, deleted_at, requires_confirmation
ON alarms FOR EACH ROW EXECUTE FUNCTION sync_alarm_delivery();

-- 明确区分“已部署”与“Pad 已移除”。旧约束名称由 PostgreSQL 默认命名；先删除以便兼容
-- 已执行过早期迁移的项目。
ALTER TABLE alarm_deliveries DROP CONSTRAINT IF EXISTS alarm_deliveries_status_check;
ALTER TABLE alarm_deliveries ADD CONSTRAINT alarm_deliveries_status_check CHECK (status IN (
    'pending', 'deployed', 'removed', 'exact_alarm_denied', 'notification_denied',
    'full_screen_denied', 'ringing', 'dismissed', 'missed'
));

ALTER TABLE alarm_events DROP CONSTRAINT IF EXISTS alarm_events_event_type_check;
ALTER TABLE alarm_events ADD CONSTRAINT alarm_events_event_type_check CHECK (event_type IN (
    'deployed', 'removed', 'permission_denied', 'ringing', 'dismissed', 'missed'
));

-- 仅在所有目标 Pad（当前数据模型为一台）完成“已移除”回执且达到保留期后，才可由受控
-- 后台任务物理清理 deleted_at 非空的记录；客户端不得直接 DELETE alarms。
