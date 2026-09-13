-- 闹钟悬浮增强展示是可选能力：保留 deployed/ringing 回执，仅以事件和 error_code 标记降级原因。
ALTER TABLE alarm_events DROP CONSTRAINT IF EXISTS alarm_events_event_type_check;
ALTER TABLE alarm_events ADD CONSTRAINT alarm_events_event_type_check CHECK (event_type IN (
    'deployed', 'removed', 'permission_denied', 'ringing', 'dismissed', 'missed', 'overlay_unavailable'
));
