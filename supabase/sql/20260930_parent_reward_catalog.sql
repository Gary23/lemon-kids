-- 家长端家庭奖励目录。先于孩子端兑换记录与事务迁移执行。
-- 可重复执行；如旧数据违反新约束，明确报错，避免静默改写奖励。
BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.rewards WHERE btrim(title, E' \t\n\r') = '' OR cost <= 0) THEN
        RAISE EXCEPTION 'rewards 存在空标题或非正价格，请先核对旧数据';
    END IF;
END $$;

ALTER TABLE public.rewards
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS cover_key TEXT NOT NULL DEFAULT 'gift',
    ADD COLUMN IF NOT EXISTS is_featured BOOLEAN NOT NULL DEFAULT false;

UPDATE public.rewards SET repeatable = true WHERE repeatable IS NULL;
UPDATE public.rewards SET is_active = true WHERE is_active IS NULL;
UPDATE public.rewards SET cover_key = 'gift' WHERE cover_key IS NULL;
UPDATE public.rewards SET is_featured = false WHERE is_featured IS NULL;

ALTER TABLE public.rewards
    ALTER COLUMN repeatable SET DEFAULT true,
    ALTER COLUMN repeatable SET NOT NULL,
    ALTER COLUMN is_active SET DEFAULT true,
    ALTER COLUMN is_active SET NOT NULL,
    ALTER COLUMN cover_key SET DEFAULT 'gift',
    ALTER COLUMN cover_key SET NOT NULL,
    ALTER COLUMN is_featured SET DEFAULT false,
    ALTER COLUMN is_featured SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.rewards'::regclass AND conname = 'rewards_title_nonblank') THEN
        ALTER TABLE public.rewards ADD CONSTRAINT rewards_title_nonblank CHECK (length(btrim(title, E' \t\n\r')) > 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.rewards'::regclass AND conname = 'rewards_cost_positive') THEN
        ALTER TABLE public.rewards ADD CONSTRAINT rewards_cost_positive CHECK (cost > 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.rewards'::regclass AND conname = 'rewards_cover_key_valid') THEN
        ALTER TABLE public.rewards ADD CONSTRAINT rewards_cover_key_valid
            CHECK (cover_key IN ('gift', 'toy', 'book', 'outing', 'treat', 'wish'));
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS rewards_one_active_featured_per_family
    ON public.rewards (family_id) WHERE is_active AND is_featured;

ALTER TABLE public.rewards ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rewards_select_family ON public.rewards;
DROP POLICY IF EXISTS rewards_insert_parent ON public.rewards;
DROP POLICY IF EXISTS rewards_update_parent ON public.rewards;
DROP POLICY IF EXISTS rewards_delete_parent ON public.rewards;

CREATE POLICY rewards_select_family ON public.rewards FOR SELECT TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.users u
        WHERE u.uid = auth.uid() AND u.family_id = rewards.family_id
          AND (u.role = 'parent' OR rewards.is_active)
    ));

CREATE POLICY rewards_insert_parent ON public.rewards FOR INSERT TO authenticated
    WITH CHECK (EXISTS (
        SELECT 1 FROM public.users u
        WHERE u.uid = auth.uid() AND u.family_id = rewards.family_id AND u.role = 'parent'
    ));

CREATE POLICY rewards_update_parent ON public.rewards FOR UPDATE TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.users u
        WHERE u.uid = auth.uid() AND u.family_id = rewards.family_id AND u.role = 'parent'
    ))
    WITH CHECK (EXISTS (
        SELECT 1 FROM public.users u
        WHERE u.uid = auth.uid() AND u.family_id = rewards.family_id AND u.role = 'parent'
    ));

-- 旧兑换 RPC 会把一次性奖励的 is_active 改为 false；暂停其调用，
-- 孩子端事务迁移将替换此函数并重新授权。
DO $$
BEGIN
    IF to_regprocedure('public.redeem_reward(uuid,uuid)') IS NOT NULL THEN
        REVOKE EXECUTE ON FUNCTION public.redeem_reward(uuid, uuid) FROM PUBLIC, anon, authenticated;
    END IF;
END $$;

COMMIT;
