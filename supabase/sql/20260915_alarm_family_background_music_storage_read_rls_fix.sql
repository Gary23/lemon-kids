-- 修复家庭背景音乐对象读取策略：子查询中的未限定 name 会优先解析为
-- alarm_family_background_music.name，而不是外层 storage.objects.name，导致
-- 已绑定监控端虽可读元数据却始终无法看到实际音频对象。
--
-- 前置：已执行 20260914_alarm_family_background_music.sql。

CREATE OR REPLACE FUNCTION can_read_family_alarm_background_music_object(p_object_path TEXT)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM public.alarm_family_background_music AS music
        WHERE music.object_path = p_object_path
          AND public.can_read_family_alarm_background_music(music.family_id)
    );
$$;

REVOKE ALL ON FUNCTION can_read_family_alarm_background_music_object(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION can_read_family_alarm_background_music_object(TEXT) TO authenticated;

DROP POLICY IF EXISTS "alarm_family_background_music_object_read" ON storage.objects;
CREATE POLICY "alarm_family_background_music_object_read"
ON storage.objects
FOR SELECT
TO authenticated
USING (
    bucket_id = 'alarm-family-background-music'
    AND can_read_family_alarm_background_music_object(name)
);

-- SQL Editor 核验（以已绑定监控端会话或 Storage Explorer 确认）：
-- SELECT name
-- FROM storage.objects
-- WHERE bucket_id = 'alarm-family-background-music'
--   AND name LIKE 'custom/<家庭 UUID>/%';
