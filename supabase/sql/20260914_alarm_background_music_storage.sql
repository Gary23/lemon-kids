-- 远程闹钟运营背景音乐：私有 Storage、只读目录和受控短时下载。
-- 前置：已执行 20260906_remote_alarms.sql 与 20260914_alarm_voice_music.sql。
-- 本文件需先在临时 Supabase 项目以家长、绑定 monitor、匿名三类会话核验后再上线。

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('alarm-background-music', 'alarm-background-music', false, 5242880, ARRAY['audio/ogg', 'audio/mpeg'])
ON CONFLICT (id) DO UPDATE SET public = false, file_size_limit = 5242880,
    allowed_mime_types = ARRAY['audio/ogg', 'audio/mpeg'];

CREATE TABLE IF NOT EXISTS alarm_background_music (
    id TEXT PRIMARY KEY CHECK (id ~ '^[a-z0-9][a-z0-9_-]{0,63}$'),
    name TEXT NOT NULL CHECK (char_length(trim(name)) BETWEEN 1 AND 80),
    version TEXT NOT NULL CHECK (version ~ '^[A-Za-z0-9._-]{1,40}$'),
    object_path TEXT NOT NULL UNIQUE CHECK (object_path ~ '^published/[A-Za-z0-9_-]+/[A-Za-z0-9._-]+/background\.(ogg|mp3)$'),
    mime_type TEXT NOT NULL CHECK (mime_type IN ('audio/ogg', 'audio/mpeg')),
    size_bytes BIGINT NOT NULL CHECK (size_bytes BETWEEN 1 AND 5242880),
    sha256 TEXT NOT NULL CHECK (sha256 ~ '^[0-9a-fA-F]{64}$'),
    duration_ms INTEGER NOT NULL CHECK (duration_ms BETWEEN 30000 AND 90000),
    status TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'published', 'unpublished')),
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (id, version, sha256)
);
CREATE INDEX IF NOT EXISTS idx_alarm_background_music_published_sort
    ON alarm_background_music (sort_order, id) WHERE status = 'published';

CREATE OR REPLACE FUNCTION set_alarm_background_music_updated_at() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN NEW.updated_at = now(); RETURN NEW; END;
$$;
DROP TRIGGER IF EXISTS trg_alarm_background_music_updated_at ON alarm_background_music;
CREATE TRIGGER trg_alarm_background_music_updated_at BEFORE UPDATE ON alarm_background_music
FOR EACH ROW EXECUTE FUNCTION set_alarm_background_music_updated_at();

-- 允许历史闹钟继续保留已下架 ID，但任何新建或改选都只能引用当前 published 曲目。
CREATE OR REPLACE FUNCTION validate_alarm_background_music() RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
    IF TG_OP = 'INSERT' OR NEW.background_music_id IS DISTINCT FROM OLD.background_music_id THEN
        IF NOT EXISTS (SELECT 1 FROM alarm_background_music m WHERE m.id = NEW.background_music_id AND m.status = 'published') THEN
            RAISE EXCEPTION '背景音乐不可选择或已下架';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS trg_validate_alarm_background_music ON alarms;
CREATE TRIGGER trg_validate_alarm_background_music BEFORE INSERT OR UPDATE OF background_music_id ON alarms
FOR EACH ROW EXECUTE FUNCTION validate_alarm_background_music();

-- 背景音乐是全产品的运营曲库，已登录家长和已绑定监控端均可读取已发布元数据；匿名用户不可读。
CREATE OR REPLACE FUNCTION can_read_alarm_background_music() RETURNS BOOLEAN LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
    SELECT EXISTS (SELECT 1 FROM users u WHERE u.uid = auth.uid() AND u.role = 'parent')
        OR EXISTS (SELECT 1 FROM users u WHERE u.uid = auth.uid() AND u.role = 'child' AND EXISTS (
            SELECT 1 FROM binding_codes bc WHERE bc.child_uid = u.uid AND bc.type = 'monitor'
                AND bc.status IN ('active', 'used') AND bc.device_id IS NOT NULL));
$$;

ALTER TABLE alarm_background_music ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "alarm_background_music_published_read" ON alarm_background_music;
CREATE POLICY "alarm_background_music_published_read" ON alarm_background_music FOR SELECT
    USING (status = 'published' AND can_read_alarm_background_music());

-- 客户端只拥有本 bucket 的对象 SELECT（用于请求短时签名 URL），没有本 bucket 的写删策略。
DROP POLICY IF EXISTS "alarm_background_music_object_read" ON storage.objects;
CREATE POLICY "alarm_background_music_object_read" ON storage.objects FOR SELECT
    USING (bucket_id = 'alarm-background-music' AND can_read_alarm_background_music() AND EXISTS (
        SELECT 1 FROM alarm_background_music m WHERE m.object_path = name AND m.status = 'published'));
-- 以 restrictive 策略兜住可能存在的其他 bucket 宽泛写策略；service_role 不受 RLS 限制。
DROP POLICY IF EXISTS "alarm_background_music_no_client_insert" ON storage.objects;
DROP POLICY IF EXISTS "alarm_background_music_no_client_update" ON storage.objects;
DROP POLICY IF EXISTS "alarm_background_music_no_client_delete" ON storage.objects;
CREATE POLICY "alarm_background_music_no_client_insert" ON storage.objects AS RESTRICTIVE FOR INSERT TO anon, authenticated
    WITH CHECK (bucket_id <> 'alarm-background-music');
CREATE POLICY "alarm_background_music_no_client_update" ON storage.objects AS RESTRICTIVE FOR UPDATE TO anon, authenticated
    USING (bucket_id <> 'alarm-background-music') WITH CHECK (bucket_id <> 'alarm-background-music');
CREATE POLICY "alarm_background_music_no_client_delete" ON storage.objects AS RESTRICTIVE FOR DELETE TO anon, authenticated
    USING (bucket_id <> 'alarm-background-music');

-- SDK 先请求该 RPC，函数只返回相对路径；随后仍由 Storage SELECT RLS 签发 10 分钟 URL。
CREATE OR REPLACE FUNCTION get_alarm_background_music_download_target(p_music_id TEXT) RETURNS TEXT
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE v_path TEXT;
BEGIN
    IF NOT can_read_alarm_background_music() THEN RAISE EXCEPTION '无权下载背景音乐'; END IF;
    SELECT object_path INTO v_path FROM alarm_background_music WHERE id = p_music_id AND status = 'published';
    IF v_path IS NULL THEN RAISE EXCEPTION '曲目不可下载'; END IF;
    RETURN v_path;
END;
$$;
-- 运营方在受控后端/SQL Editor 以 service_role 调用；客户端没有曲目表写策略或执行权限。
CREATE OR REPLACE FUNCTION publish_alarm_background_music(p_music_id TEXT) RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
    IF auth.role() <> 'service_role' THEN RAISE EXCEPTION '仅受控运营服务可发布曲目'; END IF;
    UPDATE alarm_background_music SET status = 'published' WHERE id = p_music_id AND status = 'draft';
    IF NOT FOUND THEN RAISE EXCEPTION '曲目不存在或当前不可发布'; END IF;
END;
$$;
CREATE OR REPLACE FUNCTION unpublish_alarm_background_music(p_music_id TEXT) RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
    IF auth.role() <> 'service_role' THEN RAISE EXCEPTION '仅受控运营服务可下架曲目'; END IF;
    UPDATE alarm_background_music SET status = 'unpublished' WHERE id = p_music_id AND status = 'published';
    IF NOT FOUND THEN RAISE EXCEPTION '曲目不存在或未发布'; END IF;
END;
$$;
REVOKE ALL ON FUNCTION can_read_alarm_background_music() FROM PUBLIC;
REVOKE ALL ON FUNCTION get_alarm_background_music_download_target(TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION publish_alarm_background_music(TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION unpublish_alarm_background_music(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION can_read_alarm_background_music() TO authenticated;
GRANT EXECUTE ON FUNCTION get_alarm_background_music_download_target(TEXT) TO authenticated;

ALTER TABLE alarm_deliveries ADD COLUMN IF NOT EXISTS background_music_cache_state TEXT NOT NULL DEFAULT 'pending'
    CHECK (background_music_cache_state IN ('pending', 'ready', 'failed', 'not_required'));
ALTER TABLE alarm_deliveries ADD COLUMN IF NOT EXISTS background_music_cache_error TEXT;
ALTER TABLE alarm_events DROP CONSTRAINT IF EXISTS alarm_events_event_type_check;
-- 目标环境可能保留旧版未枚举的审计事件；不应为了扩展音乐事件而删除历史或中断迁移。
-- NOT VALID 不回扫旧行，但会约束本迁移之后的所有 INSERT / UPDATE。
ALTER TABLE alarm_events ADD CONSTRAINT alarm_events_event_type_check CHECK (event_type IN (
    'deployed','permission_denied','ringing','dismissed','missed','overlay_unavailable','music_cache_ready','music_cache_failed'
)) NOT VALID;

-- 在 Supabase Dashboard 上传前先使用以下对象路径：
-- published/{music_id}/{version}/background.{ogg|mp3}
-- 发布动作必须同时复核 MIME、大小、SHA-256、30~90 秒时长、循环试听和分发授权；不得覆盖已发布对象。
-- 核验：SELECT id, name, version, status FROM alarm_background_music ORDER BY sort_order, id;
-- 核验：SELECT policyname, tablename FROM pg_policies WHERE tablename = 'alarm_background_music';
-- 审计历史值（修复后应单独评估，不能在本迁移中删除）：
-- SELECT event_type, count(*) FROM alarm_events GROUP BY event_type ORDER BY event_type;
