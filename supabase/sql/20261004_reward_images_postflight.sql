-- 只读核验：在测试 Supabase 执行 20261004_reward_images.sql 后运行。
-- 所有布尔值应为 true；权限角色实测还需单独验证。
SELECT
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'rewards'
          AND column_name = 'image_path' AND data_type = 'text' AND is_nullable = 'YES'
    ) AS image_column_ok,
    EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.rewards'::regclass AND conname = 'rewards_image_path_family'
    ) AS image_path_constraint_ok,
    EXISTS (
        SELECT 1 FROM storage.buckets
        WHERE id = 'reward-images' AND public = false AND file_size_limit = 5242880
          AND allowed_mime_types = ARRAY['image/jpeg']::text[]
    ) AS private_bucket_ok,
    (
        SELECT count(*) = 4 FROM pg_policies
        WHERE schemaname = 'storage' AND tablename = 'objects'
          AND policyname IN (
              'reward_images_read_guard', 'reward_images_insert_guard',
              'reward_images_update_guard', 'reward_images_delete_guard'
          ) AND permissive = 'RESTRICTIVE'
    ) AS restrictive_guards_ok,
    (
        SELECT count(*) = 3 FROM pg_policies
        WHERE schemaname = 'storage' AND tablename = 'objects'
          AND policyname IN (
              'reward_images_family_read', 'reward_images_parent_insert',
              'reward_images_parent_delete'
          ) AND permissive = 'PERMISSIVE'
    ) AS family_policies_ok,
    to_regprocedure('public.can_access_reward_image(text,boolean)') IS NOT NULL
        AND to_regprocedure('public.can_delete_reward_image(text)') IS NOT NULL
        AS permission_functions_ok;
