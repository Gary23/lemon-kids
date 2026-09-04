-- 柠檬小管家 Supabase 数据库初始化脚本
-- 在 Supabase Dashboard → SQL Editor 中执行全部内容

-- ============================================================
-- 一、建表
-- ============================================================

CREATE TABLE IF NOT EXISTS families (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    invite_code TEXT UNIQUE NOT NULL DEFAULT substring(md5(random()::text), 1, 6),
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS users (
    uid UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('child', 'parent')),
    family_id UUID REFERENCES families(id),
    avatar_url TEXT,
    total_points INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES families(id),
    title TEXT NOT NULL,
    description TEXT DEFAULT '',
    child_id UUID NOT NULL REFERENCES users(uid),
    created_by UUID NOT NULL REFERENCES users(uid),
    status TEXT NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'done', 'verified', 'expired', 'rejected')),
    category TEXT NOT NULL DEFAULT 'other'
        CHECK (category IN ('study', 'chore', 'reading', 'exercise', 'other')),
    due_date DATE NOT NULL,
    due_time TEXT,
    reward_points INT DEFAULT 5,
    penalty_points INT DEFAULT 2,
    require_photo BOOLEAN DEFAULT false,
    completed_at TIMESTAMPTZ,
    verified_at TIMESTAMPTZ,
    -- 已取消的未发生任务保留在回收站；历史任务永不写入此字段。
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_tasks_child_date ON tasks(child_id, due_date);
CREATE INDEX IF NOT EXISTS idx_tasks_family ON tasks(family_id);

CREATE TABLE IF NOT EXISTS task_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    category TEXT NOT NULL,
    reward_points INT NOT NULL DEFAULT 5 CHECK (reward_points > 0),
    penalty_points INT NOT NULL DEFAULT 2,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_task_templates_family ON task_templates(family_id);

CREATE TABLE IF NOT EXISTS messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES families(id),
    sender_id UUID NOT NULL REFERENCES users(uid),
    receiver_id UUID NOT NULL REFERENCES users(uid),
    type TEXT NOT NULL DEFAULT 'voice' CHECK (type IN ('voice', 'image')),
    voice_url TEXT DEFAULT '',
    voice_duration INT DEFAULT 0,
    is_read BOOLEAN DEFAULT false,
    is_played BOOLEAN DEFAULT false,
    timestamp TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_messages_family_time ON messages(family_id, timestamp DESC);

CREATE TABLE IF NOT EXISTS rewards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES families(id),
    title TEXT NOT NULL,
    cost INT NOT NULL,
    repeatable BOOLEAN DEFAULT true,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS point_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES families(id),
    child_id UUID NOT NULL REFERENCES users(uid),
    amount INT NOT NULL,
    balance INT NOT NULL,
    reason TEXT NOT NULL,
    type TEXT NOT NULL CHECK (type IN ('task_complete', 'task_expired', 'reward_redeem', 'manual')),
    related_task_id UUID REFERENCES tasks(id),
    related_reward_id UUID REFERENCES rewards(id),
    timestamp TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_pointrecords_child ON point_records(child_id);

CREATE TABLE IF NOT EXISTS app_usage (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES families(id),
    child_id UUID NOT NULL REFERENCES users(uid),
    package_name TEXT NOT NULL,
    app_name TEXT NOT NULL,
    duration_seconds BIGINT DEFAULT 0,
    date DATE NOT NULL,
    collected_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_appusage_child_date ON app_usage(child_id, date);

CREATE TABLE IF NOT EXISTS app_limits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES families(id),
    child_id UUID NOT NULL REFERENCES users(uid),
    package_name TEXT NOT NULL,
    app_name TEXT NOT NULL,
    daily_limit_minutes INT DEFAULT 30,
    is_active BOOLEAN DEFAULT true,
    updated_at TIMESTAMPTZ DEFAULT now()
);

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


-- ============================================================
-- 二、数据库函数（事务性操作）
-- ============================================================

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
$$ LANGUAGE plpgsql SECURITY DEFINER;

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
$$ LANGUAGE plpgsql SECURITY DEFINER;


-- ============================================================
-- 三、行级安全策略 (RLS)
-- ============================================================

ALTER TABLE families ENABLE ROW LEVEL SECURITY;
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE tasks ENABLE ROW LEVEL SECURITY;
ALTER TABLE task_templates ENABLE ROW LEVEL SECURITY;
ALTER TABLE messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE point_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE rewards ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_usage ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_limits ENABLE ROW LEVEL SECURITY;
ALTER TABLE device_status_logs ENABLE ROW LEVEL SECURITY;

CREATE POLICY "users_self_access" ON users
    FOR ALL USING (uid = auth.uid());

CREATE POLICY "tasks_family_access" ON tasks
    FOR ALL USING (family_id IN (
        SELECT family_id FROM users WHERE uid = auth.uid()
    ));

CREATE POLICY "task_templates_family_access" ON task_templates
    FOR ALL USING (family_id IN (
        SELECT family_id FROM users WHERE uid = auth.uid()
    ));

CREATE POLICY "messages_family_access" ON messages
    FOR ALL USING (family_id IN (
        SELECT family_id FROM users WHERE uid = auth.uid()
    ));

CREATE POLICY "point_records_family_access" ON point_records
    FOR ALL USING (family_id IN (
        SELECT family_id FROM users WHERE uid = auth.uid()
    ));

CREATE POLICY "rewards_select_family" ON rewards
    FOR SELECT USING (family_id IN (
        SELECT family_id FROM users WHERE uid = auth.uid()
    ));

CREATE POLICY "rewards_insert_parent" ON rewards
    FOR INSERT WITH CHECK (
        (SELECT role FROM users WHERE uid = auth.uid()) = 'parent'
    );

CREATE POLICY "rewards_update_parent" ON rewards
    FOR UPDATE USING (family_id IN (
        SELECT family_id FROM users WHERE uid = auth.uid() AND role = 'parent'
    ));

CREATE POLICY "app_usage_family_access" ON app_usage
    FOR ALL USING (family_id IN (
        SELECT family_id FROM users WHERE uid = auth.uid()
    ));

CREATE POLICY "app_limits_family_access" ON app_limits
    FOR ALL USING (family_id IN (
        SELECT family_id FROM users WHERE uid = auth.uid()
    ));

CREATE POLICY "device_status_logs_family_access" ON device_status_logs
    FOR ALL USING (family_id IN (
        SELECT family_id FROM users WHERE uid = auth.uid()
    ));

CREATE POLICY "families_access" ON families
    FOR ALL USING (id IN (
        SELECT family_id FROM users WHERE uid = auth.uid()
    ));


-- ============================================================
-- 四、Supabase Storage 配置
-- ============================================================
-- 注意：需要在 Supabase Dashboard → Storage 中先手动创建 buckets:
--   voices, photos, avatars
-- 然后执行以下 RLS 策略：

-- 允许上传语音文件到 voices bucket
CREATE POLICY "voices_allow_upload" ON storage.objects
    FOR INSERT
    WITH CHECK (bucket_id = 'voices');

-- 允许公开读取语音文件
CREATE POLICY "voices_allow_select" ON storage.objects
    FOR SELECT
    USING (bucket_id = 'voices');

-- 允许上传照片
CREATE POLICY "photos_allow_upload" ON storage.objects
    FOR INSERT
    WITH CHECK (bucket_id = 'photos');

-- 允许公开读取照片
CREATE POLICY "photos_allow_select" ON storage.objects
    FOR SELECT
    USING (bucket_id = 'photos');

-- 允许已认证用户上传头像
CREATE POLICY "avatars_allow_upload" ON storage.objects
    FOR INSERT
    WITH CHECK (bucket_id = 'avatars');

-- 允许公开读取头像
CREATE POLICY "avatars_allow_select" ON storage.objects
    FOR SELECT
    USING (bucket_id = 'avatars');


-- ============================================================
-- 五、Supabase Realtime 配置 (在 Dashboard 中手动开启)
-- ============================================================
-- Dashboard → Database → Replication:
--   开启 tables: tasks, messages, rewards, app_limits
