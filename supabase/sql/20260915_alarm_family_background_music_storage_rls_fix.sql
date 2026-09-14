-- 修复家庭闹钟音乐上传被 Storage RLS 拒绝的问题。
-- 前置：已执行 20260914_alarm_background_music_storage.sql 和
--       20260914_alarm_family_background_music.sql。
--
-- Storage.objects 的 INSERT 发生在家庭音乐元数据写入之前，因此只按“当前 authenticated
-- 家长的 family_id 与对象路径第二段一致”授权。函数为 SECURITY DEFINER，以避免用户表
-- 的既有 RLS 组合意外使 Storage 策略查询不到当前家长记录。

CREATE OR REPLACE FUNCTION can_upload_family_alarm_background_music(p_object_path TEXT)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT p_object_path ~ '^custom/[0-9a-fA-F-]{36}/family_[a-z0-9]{32}/v[A-Za-z0-9._-]+/background\.(ogg|mp3)$'
       AND EXISTS (
            SELECT 1
            FROM public.users u
            WHERE u.uid = auth.uid()
              AND u.role = 'parent'
              AND split_part(p_object_path, '/', 2) = u.family_id::text
       );
$$;

REVOKE ALL ON FUNCTION can_upload_family_alarm_background_music(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION can_upload_family_alarm_background_music(TEXT) TO authenticated;

DROP POLICY IF EXISTS "alarm_family_background_music_parent_insert" ON storage.objects;
CREATE POLICY "alarm_family_background_music_parent_insert"
ON storage.objects
FOR INSERT
TO authenticated
WITH CHECK (
    bucket_id = 'alarm-family-background-music'
    AND can_upload_family_alarm_background_music(name)
);

-- SQL Editor 核验（应返回 true）：
-- SELECT can_upload_family_alarm_background_music(
--   'custom/<当前家庭 UUID>/family_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/v1/background.mp3'
-- );
