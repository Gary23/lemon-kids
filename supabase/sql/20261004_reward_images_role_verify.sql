-- 在已执行迁移的测试 Supabase SQL Editor 运行。
-- 仅建立会话临时表，不修改奖励、Storage 对象或用户数据。
-- 自动查找：同家庭家长与孩子。另一个家庭的家长若存在则额外验证。
-- 若缺少同家庭家长与孩子，会返回 parent_child_fixture_available=false。
CREATE TEMP TABLE IF NOT EXISTS reward_image_role_results (
    scenario TEXT NOT NULL,
    actual BOOLEAN NOT NULL,
    expected BOOLEAN NOT NULL
);
TRUNCATE TABLE reward_image_role_results;

DO $$
DECLARE
    parent_a UUID;
    child_a UUID;
    parent_b UUID;
    family_a UUID;
    family_b UUID;
    path_a TEXT;
    path_b TEXT;
BEGIN
    SELECT p.uid, c.uid, p.family_id
    INTO parent_a, child_a, family_a
    FROM public.users p
    JOIN LATERAL (
        SELECT u.uid FROM public.users u
        WHERE u.family_id = p.family_id AND u.role = 'child' LIMIT 1
    ) c ON true
    WHERE p.role = 'parent' AND p.family_id IS NOT NULL
    LIMIT 1;

    IF parent_a IS NULL THEN
        INSERT INTO reward_image_role_results VALUES ('parent_child_fixture_available', false, true);
        RETURN;
    END IF;
    INSERT INTO reward_image_role_results VALUES ('parent_child_fixture_available', true, true);
    SELECT u.uid, u.family_id INTO parent_b, family_b
    FROM public.users u WHERE u.role = 'parent' AND u.family_id <> family_a LIMIT 1;
    IF family_b IS NULL THEN
        family_b := CASE WHEN family_a = '00000000-0000-0000-0000-000000000001'::uuid
            THEN '00000000-0000-0000-0000-000000000002'::uuid
            ELSE '00000000-0000-0000-0000-000000000001'::uuid END;
    END IF;
    path_a := family_a::text || '/00000000-0000-4000-8000-000000000001.jpg';
    path_b := family_b::text || '/00000000-0000-4000-8000-000000000002.jpg';

    PERFORM set_config('request.jwt.claim.role', 'authenticated', true);
    PERFORM set_config('request.jwt.claim.sub', parent_a::text, true);
    INSERT INTO reward_image_role_results SELECT 'parent_a_read_own', public.can_access_reward_image(path_a, false), true;
    INSERT INTO reward_image_role_results SELECT 'parent_a_write_own', public.can_access_reward_image(path_a, true), true;
    INSERT INTO reward_image_role_results SELECT 'parent_a_delete_unreferenced', public.can_delete_reward_image(path_a), true;
    INSERT INTO reward_image_role_results SELECT 'parent_a_read_other_family_path', public.can_access_reward_image(path_b, false), false;
    INSERT INTO reward_image_role_results SELECT 'parent_a_write_other_family_path', public.can_access_reward_image(path_b, true), false;

    PERFORM set_config('request.jwt.claim.sub', child_a::text, true);
    INSERT INTO reward_image_role_results SELECT 'child_a_read_own', public.can_access_reward_image(path_a, false), true;
    INSERT INTO reward_image_role_results SELECT 'child_a_write_own', public.can_access_reward_image(path_a, true), false;
    INSERT INTO reward_image_role_results SELECT 'child_a_delete_own', public.can_delete_reward_image(path_a), false;

    IF parent_b IS NOT NULL THEN
        PERFORM set_config('request.jwt.claim.sub', parent_b::text, true);
        INSERT INTO reward_image_role_results SELECT 'parent_b_read_family_a', public.can_access_reward_image(path_a, false), false;
        INSERT INTO reward_image_role_results SELECT 'parent_b_write_family_a', public.can_access_reward_image(path_a, true), false;
    END IF;

    PERFORM set_config('request.jwt.claim.role', 'anon', true);
    PERFORM set_config('request.jwt.claim.sub', '', true);
    INSERT INTO reward_image_role_results SELECT 'anonymous_read', public.can_access_reward_image(path_a, false), false;
    INSERT INTO reward_image_role_results SELECT 'anonymous_write', public.can_access_reward_image(path_a, true), false;
END $$;

SELECT scenario, actual, expected, actual = expected AS passed,
    (SELECT count(*) FROM public.users WHERE role = 'parent' AND family_id IS NOT NULL) AS parent_count,
    (SELECT count(*) FROM public.users WHERE role = 'child' AND family_id IS NOT NULL) AS child_count,
    (SELECT count(DISTINCT family_id) FROM public.users WHERE role = 'parent' AND family_id IS NOT NULL) AS parent_family_count
FROM reward_image_role_results ORDER BY scenario;
