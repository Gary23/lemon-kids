-- 家庭自定义远程闹钟音乐：仅供本家庭家长上传，已绑定监控 Pad 只读下载。
-- 前置：20260914_alarm_background_music_storage.sql 已执行。

-- 旧迁移只允许两首内置曲目的 CHECK；动态运营/家庭目录已由下方触发器校验，必须先移除旧白名单。
ALTER TABLE alarms DROP CONSTRAINT IF EXISTS alarms_background_music_id_check;

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('alarm-family-background-music', 'alarm-family-background-music', false, 5242880, ARRAY['audio/ogg', 'audio/mpeg'])
ON CONFLICT (id) DO UPDATE SET public = false, file_size_limit = 5242880,
    allowed_mime_types = ARRAY['audio/ogg', 'audio/mpeg'];

CREATE TABLE IF NOT EXISTS alarm_family_background_music (
    id TEXT PRIMARY KEY CHECK (id ~ '^family_[a-z0-9]{32}$'),
    family_id UUID NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    name TEXT NOT NULL CHECK (char_length(trim(name)) BETWEEN 1 AND 80),
    version TEXT NOT NULL DEFAULT '1' CHECK (version ~ '^[A-Za-z0-9._-]{1,40}$'),
    object_path TEXT NOT NULL UNIQUE CHECK (object_path ~ '^custom/[0-9a-fA-F-]{36}/family_[a-z0-9]{32}/v[A-Za-z0-9._-]+/background\.(ogg|mp3)$'),
    mime_type TEXT NOT NULL CHECK (mime_type IN ('audio/ogg', 'audio/mpeg')),
    size_bytes BIGINT NOT NULL CHECK (size_bytes BETWEEN 1 AND 5242880),
    sha256 TEXT NOT NULL CHECK (sha256 ~ '^[0-9a-fA-F]{64}$'),
    duration_ms INTEGER NOT NULL CHECK (duration_ms BETWEEN 30000 AND 90000),
    status TEXT NOT NULL DEFAULT 'published' CHECK (status = 'published'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (family_id, id)
);
CREATE INDEX IF NOT EXISTS idx_alarm_family_background_music_family
    ON alarm_family_background_music (family_id, created_at);

CREATE OR REPLACE FUNCTION set_alarm_family_background_music_updated_at() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN NEW.updated_at = now(); RETURN NEW; END;
$$;
DROP TRIGGER IF EXISTS trg_alarm_family_background_music_updated_at ON alarm_family_background_music;
CREATE TRIGGER trg_alarm_family_background_music_updated_at BEFORE UPDATE ON alarm_family_background_music
FOR EACH ROW EXECUTE FUNCTION set_alarm_family_background_music_updated_at();

CREATE OR REPLACE FUNCTION can_read_family_alarm_background_music(p_family_id UUID) RETURNS BOOLEAN
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
    SELECT EXISTS (SELECT 1 FROM users u WHERE u.uid = auth.uid() AND u.family_id = p_family_id AND u.role = 'parent')
        OR EXISTS (SELECT 1 FROM users u WHERE u.uid = auth.uid() AND u.family_id = p_family_id AND u.role = 'child'
            AND EXISTS (SELECT 1 FROM binding_codes bc WHERE bc.child_uid = u.uid AND bc.type = 'monitor'
                AND bc.status IN ('active', 'used') AND bc.device_id IS NOT NULL));
$$;

ALTER TABLE alarm_family_background_music ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "alarm_family_background_music_read" ON alarm_family_background_music;
CREATE POLICY "alarm_family_background_music_read" ON alarm_family_background_music FOR SELECT
    USING (status = 'published' AND can_read_family_alarm_background_music(family_id));
DROP POLICY IF EXISTS "alarm_family_background_music_parent_insert" ON alarm_family_background_music;
CREATE POLICY "alarm_family_background_music_parent_insert" ON alarm_family_background_music FOR INSERT TO authenticated
    WITH CHECK (
        EXISTS (SELECT 1 FROM users u WHERE u.uid = auth.uid() AND u.role = 'parent' AND u.family_id = family_id)
        AND object_path = 'custom/' || family_id::text || '/' || id || '/v' || version ||
            CASE WHEN mime_type = 'audio/ogg' THEN '/background.ogg' ELSE '/background.mp3' END
    );

-- 家长上传只能写本人家庭目录；monitor 和家长读取时都必须有对应可见元数据。
DROP POLICY IF EXISTS "alarm_family_background_music_object_read" ON storage.objects;
CREATE POLICY "alarm_family_background_music_object_read" ON storage.objects FOR SELECT TO authenticated
    USING (bucket_id = 'alarm-family-background-music' AND EXISTS (
        SELECT 1 FROM alarm_family_background_music m
        WHERE m.object_path = name AND can_read_family_alarm_background_music(m.family_id)
    ));
DROP POLICY IF EXISTS "alarm_family_background_music_parent_insert" ON storage.objects;
CREATE POLICY "alarm_family_background_music_parent_insert" ON storage.objects FOR INSERT TO authenticated
    WITH CHECK (bucket_id = 'alarm-family-background-music' AND EXISTS (
        SELECT 1 FROM users u WHERE u.uid = auth.uid() AND u.role = 'parent'
          AND name LIKE 'custom/' || u.family_id::text || '/%'
    ));

-- 仍允许历史闹钟保留已下架运营曲目；新建或改选时可引用运营已发布曲目，或同家庭自定义曲目。
CREATE OR REPLACE FUNCTION validate_alarm_background_music() RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
    IF TG_OP = 'INSERT' OR NEW.background_music_id IS DISTINCT FROM OLD.background_music_id THEN
        IF NOT EXISTS (SELECT 1 FROM alarm_background_music m WHERE m.id = NEW.background_music_id AND m.status = 'published')
           AND NOT EXISTS (SELECT 1 FROM alarm_family_background_music m
                           WHERE m.id = NEW.background_music_id AND m.family_id = NEW.family_id AND m.status = 'published') THEN
            RAISE EXCEPTION '背景音乐不可选择或不属于当前家庭';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION can_read_family_alarm_background_music(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION can_read_family_alarm_background_music(UUID) TO authenticated;

-- Dashboard SQL Editor 执行后核验：
-- SELECT id, family_id, name, object_path FROM alarm_family_background_music;
-- SELECT policyname, tablename FROM pg_policies WHERE tablename = 'alarm_family_background_music';
