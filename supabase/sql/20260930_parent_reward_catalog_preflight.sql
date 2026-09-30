-- 只读预检：在执行 20260930_parent_reward_catalog.sql 之前运行并核对结果。
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'rewards'
ORDER BY ordinal_position;

SELECT
    count(*) AS reward_count,
    count(*) FILTER (WHERE btrim(title, E' \t\n\r') = '') AS blank_title_count,
    count(*) FILTER (WHERE cost <= 0) AS invalid_cost_count,
    count(*) FILTER (WHERE is_active = false) AS inactive_count,
    count(*) FILTER (WHERE repeatable = false) AS one_time_count
FROM public.rewards;

-- 旧版兑换可能曾把一次性奖励自动停用，需人工核对停用项及兑换流水。
SELECT r.id, r.title, r.is_active, r.repeatable,
       count(pr.id) FILTER (WHERE pr.type = 'reward_redeem') AS legacy_redemption_count
FROM public.rewards r
LEFT JOIN public.point_records pr ON pr.related_reward_id = r.id
GROUP BY r.id, r.title, r.is_active, r.repeatable
ORDER BY r.created_at DESC;

SELECT policyname, cmd, roles, qual, with_check
FROM pg_policies
WHERE schemaname = 'public' AND tablename = 'rewards'
ORDER BY policyname;

SELECT p.proname, p.prosecdef, p.proacl
FROM pg_proc p
WHERE p.oid = to_regprocedure('public.redeem_reward(uuid,uuid)');
