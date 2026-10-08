-- 前置：先运行同目录 20261004_reward_images_preflight.sql 并核对现有列、bucket 与 Storage 策略。
-- 奖励图片只保存家庭路径；匿名及其他家庭不得通过旧的宽泛 Storage 策略绕过授权。
BEGIN;

ALTER TABLE public.rewards ADD COLUMN IF NOT EXISTS image_path TEXT;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.rewards
        WHERE image_path IS NOT NULL AND image_path !~
            ('^' || family_id::text || '/[0-9a-fA-F-]{36}\.jpg$')
    ) THEN
        RAISE EXCEPTION 'rewards.image_path 已有不符合家庭路径的记录，请先核对';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.rewards'::regclass AND conname = 'rewards_image_path_family'
    ) THEN
        ALTER TABLE public.rewards ADD CONSTRAINT rewards_image_path_family
            CHECK (image_path IS NULL OR image_path ~
                ('^' || family_id::text || '/[0-9a-fA-F-]{36}\.jpg$'));
    END IF;
END $$;

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('reward-images', 'reward-images', false, 5242880, ARRAY['image/jpeg'])
ON CONFLICT (id) DO UPDATE SET
    public = false,
    file_size_limit = EXCLUDED.file_size_limit,
    allowed_mime_types = EXCLUDED.allowed_mime_types;

CREATE OR REPLACE FUNCTION public.can_access_reward_image(p_path TEXT, p_write BOOLEAN)
RETURNS BOOLEAN LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
    SELECT p_path ~ '^[0-9a-fA-F-]{36}/[0-9a-fA-F-]{36}\.jpg$'
       AND EXISTS (
           SELECT 1 FROM public.users u
           WHERE u.uid = auth.uid()
             AND u.family_id::text = split_part(p_path, '/', 1)
             AND (NOT p_write OR u.role = 'parent')
       );
$$;
REVOKE ALL ON FUNCTION public.can_access_reward_image(TEXT, BOOLEAN) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.can_access_reward_image(TEXT, BOOLEAN) TO authenticated;

CREATE OR REPLACE FUNCTION public.can_delete_reward_image(p_path TEXT)
RETURNS BOOLEAN LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
    SELECT public.can_access_reward_image(p_path, true)
       AND NOT EXISTS (SELECT 1 FROM public.rewards r WHERE r.image_path = p_path);
$$;
REVOKE ALL ON FUNCTION public.can_delete_reward_image(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.can_delete_reward_image(TEXT) TO authenticated;

-- 限制性策略与现存任意宽泛 permissive 策略取交集，仅约束本 bucket。
DROP POLICY IF EXISTS reward_images_read_guard ON storage.objects;
CREATE POLICY reward_images_read_guard ON storage.objects AS RESTRICTIVE
    FOR SELECT TO PUBLIC USING (
        bucket_id <> 'reward-images' OR
        (auth.role() = 'authenticated' AND public.can_access_reward_image(name, false))
    );
DROP POLICY IF EXISTS reward_images_insert_guard ON storage.objects;
CREATE POLICY reward_images_insert_guard ON storage.objects AS RESTRICTIVE
    FOR INSERT TO PUBLIC WITH CHECK (
        bucket_id <> 'reward-images' OR
        (auth.role() = 'authenticated' AND public.can_access_reward_image(name, true))
    );
DROP POLICY IF EXISTS reward_images_update_guard ON storage.objects;
CREATE POLICY reward_images_update_guard ON storage.objects AS RESTRICTIVE
    FOR UPDATE TO PUBLIC USING (bucket_id <> 'reward-images')
    WITH CHECK (bucket_id <> 'reward-images');
DROP POLICY IF EXISTS reward_images_delete_guard ON storage.objects;
CREATE POLICY reward_images_delete_guard ON storage.objects AS RESTRICTIVE
    FOR DELETE TO PUBLIC USING (
        bucket_id <> 'reward-images' OR
        (auth.role() = 'authenticated' AND public.can_delete_reward_image(name))
    );

DROP POLICY IF EXISTS reward_images_family_read ON storage.objects;
CREATE POLICY reward_images_family_read ON storage.objects FOR SELECT TO authenticated
    USING (bucket_id = 'reward-images' AND public.can_access_reward_image(name, false));
DROP POLICY IF EXISTS reward_images_parent_insert ON storage.objects;
CREATE POLICY reward_images_parent_insert ON storage.objects FOR INSERT TO authenticated
    WITH CHECK (bucket_id = 'reward-images' AND public.can_access_reward_image(name, true));
DROP POLICY IF EXISTS reward_images_parent_delete ON storage.objects;
CREATE POLICY reward_images_parent_delete ON storage.objects FOR DELETE TO authenticated
    USING (bucket_id = 'reward-images' AND public.can_delete_reward_image(name));

COMMIT;
