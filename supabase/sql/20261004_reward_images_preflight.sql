-- 只读预检：一次返回一行，便于 SQL Editor 完整复制结果。
-- 执行迁移前核对已有 image_path、reward-images bucket 和全部 Storage 策略。
SELECT
    (
        SELECT COALESCE(jsonb_agg(to_jsonb(c) ORDER BY c.column_name), '[]'::jsonb)
        FROM (
            SELECT column_name, data_type, is_nullable
            FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'rewards' AND column_name = 'image_path'
        ) c
    ) AS reward_image_columns,
    (
        SELECT COALESCE(jsonb_agg(to_jsonb(b) ORDER BY b.id), '[]'::jsonb)
        FROM (
            SELECT id, name, public, file_size_limit, allowed_mime_types
            FROM storage.buckets WHERE id = 'reward-images'
        ) b
    ) AS reward_image_bucket,
    (
        SELECT COALESCE(jsonb_agg(to_jsonb(p) ORDER BY p.policyname), '[]'::jsonb)
        FROM (
            SELECT policyname, cmd, permissive, roles, qual, with_check
            FROM pg_policies WHERE schemaname = 'storage' AND tablename = 'objects'
        ) p
    ) AS storage_object_policies,
    (
        SELECT COALESCE(jsonb_agg(to_jsonb(p) ORDER BY p.policyname), '[]'::jsonb)
        FROM (
            SELECT policyname, cmd, roles, qual, with_check
            FROM pg_policies WHERE schemaname = 'public' AND tablename = 'rewards'
        ) p
    ) AS reward_policies;
