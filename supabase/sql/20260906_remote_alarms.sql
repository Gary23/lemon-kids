-- 远程闹钟：家长端创建、监控 Pad 下发与执行回执
-- 前置：families、users、binding_codes 与 monitor 绑定码/RPC 已存在。
-- 此迁移仅创建闹钟域，不会修改或清空既有业务数据。

CREATE TABLE IF NOT EXISTS alarms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), family_id UUID NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    child_id UUID NOT NULL REFERENCES users(uid) ON DELETE CASCADE, target_device_id TEXT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision > 0), trigger_at TIMESTAMPTZ NOT NULL,
    timezone TEXT NOT NULL DEFAULT 'Asia/Shanghai', title TEXT NOT NULL CHECK (char_length(title) BETWEEN 1 AND 80),
    message TEXT NOT NULL DEFAULT '' CHECK (char_length(message) <= 300), enabled BOOLEAN NOT NULL DEFAULT true,
    requires_confirmation BOOLEAN NOT NULL DEFAULT true, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_alarms_device_trigger ON alarms(target_device_id, trigger_at) WHERE enabled;
CREATE INDEX IF NOT EXISTS idx_alarms_child_trigger ON alarms(child_id, trigger_at);

CREATE TABLE IF NOT EXISTS alarm_deliveries (
    alarm_id UUID NOT NULL REFERENCES alarms(id) ON DELETE CASCADE, device_id TEXT NOT NULL,
    child_id UUID NOT NULL REFERENCES users(uid) ON DELETE CASCADE, revision BIGINT NOT NULL CHECK (revision > 0),
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','deployed','exact_alarm_denied','notification_denied','full_screen_denied','ringing','dismissed','missed')),
    error_code TEXT, ack_at TIMESTAMPTZ, updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY (alarm_id, device_id)
);
CREATE INDEX IF NOT EXISTS idx_alarm_deliveries_child ON alarm_deliveries(child_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS alarm_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), alarm_id UUID NOT NULL REFERENCES alarms(id) ON DELETE CASCADE,
    device_id TEXT NOT NULL, revision BIGINT NOT NULL CHECK (revision > 0),
    event_type TEXT NOT NULL CHECK (event_type IN ('deployed','permission_denied','ringing','dismissed','missed')),
    detail TEXT NOT NULL DEFAULT '', occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_alarm_events_alarm_time ON alarm_events(alarm_id, occurred_at DESC);

-- 每个新版本都会将目标 Pad 的确认状态重置为 pending；函数以 definer 身份运行，
-- 不会被父端对 alarm_deliveries 的最小权限策略阻断。
CREATE OR REPLACE FUNCTION sync_alarm_delivery() RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
    INSERT INTO alarm_deliveries (alarm_id, device_id, child_id, revision, status, error_code, ack_at, updated_at)
    VALUES (NEW.id, NEW.target_device_id, NEW.child_id, NEW.revision, 'pending', NULL, NULL, now())
    ON CONFLICT (alarm_id, device_id) DO UPDATE SET child_id = EXCLUDED.child_id, revision = EXCLUDED.revision,
        status = 'pending', error_code = NULL, ack_at = NULL, updated_at = now();
    RETURN NEW;
END; $$;
DROP TRIGGER IF EXISTS trg_sync_alarm_delivery ON alarms;
CREATE TRIGGER trg_sync_alarm_delivery AFTER INSERT OR UPDATE OF revision, target_device_id, enabled, trigger_at, title, message, requires_confirmation ON alarms FOR EACH ROW EXECUTE FUNCTION sync_alarm_delivery();

CREATE OR REPLACE FUNCTION set_alarm_updated_at() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN NEW.updated_at = now(); RETURN NEW; END; $$;
DROP TRIGGER IF EXISTS trg_alarms_updated_at ON alarms;
CREATE TRIGGER trg_alarms_updated_at BEFORE UPDATE ON alarms FOR EACH ROW EXECUTE FUNCTION set_alarm_updated_at();

ALTER TABLE alarms ENABLE ROW LEVEL SECURITY;
ALTER TABLE alarm_deliveries ENABLE ROW LEVEL SECURITY;
ALTER TABLE alarm_events ENABLE ROW LEVEL SECURITY;

-- target_device_id 必须是该孩子当前的 monitor 绑定设备；task 绑定码不会用于闹钟路由。
CREATE OR REPLACE FUNCTION is_current_monitor_device(p_child_id UUID, p_device_id TEXT) RETURNS BOOLEAN LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
    SELECT EXISTS (SELECT 1 FROM binding_codes bc WHERE bc.child_uid = p_child_id AND bc.type = 'monitor'
        AND bc.device_id = p_device_id AND bc.status IN ('active', 'used'));
$$;
CREATE OR REPLACE FUNCTION is_child_in_family(p_child_id UUID, p_family_id UUID) RETURNS BOOLEAN LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
    SELECT EXISTS (SELECT 1 FROM users u WHERE u.uid = p_child_id AND u.family_id = p_family_id AND u.role = 'child');
$$;

CREATE POLICY "alarms_parent_manage" ON alarms FOR ALL
    USING (family_id IN (SELECT family_id FROM users WHERE uid = auth.uid() AND role = 'parent'))
    WITH CHECK (family_id IN (SELECT family_id FROM users WHERE uid = auth.uid() AND role = 'parent')
        AND is_child_in_family(child_id, alarms.family_id)
        AND is_current_monitor_device(child_id, target_device_id));
CREATE POLICY "alarms_target_monitor_read" ON alarms FOR SELECT
    USING (auth.uid() = child_id AND is_current_monitor_device(child_id, target_device_id));

CREATE POLICY "alarm_deliveries_parent_read" ON alarm_deliveries FOR SELECT USING (EXISTS (
    SELECT 1 FROM alarms a WHERE a.id = alarm_deliveries.alarm_id
      AND a.family_id IN (SELECT family_id FROM users WHERE uid = auth.uid() AND role = 'parent')));
CREATE POLICY "alarm_deliveries_target_monitor_read" ON alarm_deliveries FOR SELECT
    USING (auth.uid() = child_id AND is_current_monitor_device(child_id, device_id));
CREATE POLICY "alarm_deliveries_target_monitor_update" ON alarm_deliveries FOR UPDATE
    USING (auth.uid() = child_id AND is_current_monitor_device(child_id, device_id))
    WITH CHECK (auth.uid() = child_id AND is_current_monitor_device(child_id, device_id));

CREATE POLICY "alarm_events_parent_read" ON alarm_events FOR SELECT USING (EXISTS (
    SELECT 1 FROM alarms a JOIN users parent_user ON parent_user.uid = auth.uid()
    WHERE a.id = alarm_events.alarm_id AND parent_user.role = 'parent' AND parent_user.family_id = a.family_id));
CREATE POLICY "alarm_events_target_monitor_insert" ON alarm_events FOR INSERT WITH CHECK (EXISTS (
    SELECT 1 FROM alarms a WHERE a.id = alarm_events.alarm_id AND a.child_id = auth.uid()
        AND a.target_device_id = alarm_events.device_id AND a.revision = alarm_events.revision
        AND is_current_monitor_device(a.child_id, alarm_events.device_id)));
CREATE POLICY "alarm_events_target_monitor_read" ON alarm_events FOR SELECT USING (EXISTS (
    SELECT 1 FROM alarms a WHERE a.id = alarm_events.alarm_id AND a.child_id = auth.uid()
        AND a.target_device_id = alarm_events.device_id AND is_current_monitor_device(a.child_id, alarm_events.device_id)));

-- 家长端只通过此 RPC 读取 monitor 设备；不向客户端开放 binding_codes 表。
CREATE OR REPLACE FUNCTION get_monitor_devices(p_family_id UUID, p_child_id UUID)
RETURNS TABLE(device_id TEXT, child_id UUID, child_name TEXT) LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM users WHERE uid = auth.uid() AND role = 'parent' AND family_id = p_family_id) THEN RAISE EXCEPTION '无权读取监控设备'; END IF;
    RETURN QUERY SELECT DISTINCT ON (bc.device_id) bc.device_id::TEXT, bc.child_uid, u.name
    FROM binding_codes bc JOIN users u ON u.uid = bc.child_uid
    WHERE bc.family_id = p_family_id AND bc.child_uid = p_child_id AND bc.type = 'monitor'
      AND bc.device_id IS NOT NULL AND bc.status IN ('active', 'used') ORDER BY bc.device_id, bc.created_at DESC;
END; $$;
REVOKE ALL ON FUNCTION is_current_monitor_device(UUID, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION is_current_monitor_device(UUID, TEXT) TO authenticated;
REVOKE ALL ON FUNCTION is_child_in_family(UUID, UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION is_child_in_family(UUID, UUID) TO authenticated;
REVOKE ALL ON FUNCTION get_monitor_devices(UUID, UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION get_monitor_devices(UUID, UUID) TO authenticated;

-- Dashboard → Database → Replication 中为 alarms 开启 Realtime。Android 正式保障路径仍是
-- 启动/解锁立即对账 + 15 分钟 WorkManager 兜底，Realtime 不能作为唯一投递机制。
-- 部署后核验：SELECT policyname, tablename FROM pg_policies WHERE tablename IN ('alarms','alarm_deliveries','alarm_events');
