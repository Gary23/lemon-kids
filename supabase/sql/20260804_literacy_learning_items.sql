-- 认字学习状态改为按“字 / 每个词 / 每个句”独立记录。
-- 执行后，三星评测会向 child_literacy_learning_items 写入一行；
-- 当一个汉字的全部学习项均完成时，child_literacy_characters.learned_at
-- 会记录为该汉字首次整体学会的时间。

alter table public.child_literacy_characters
    add column if not exists learned_at timestamptz;

create table if not exists public.child_literacy_learning_items (
    id uuid primary key default gen_random_uuid(),
    child_id uuid not null references auth.users(id) on delete cascade,
    literacy_character_id uuid not null references public.child_literacy_characters(id) on delete cascade,
    item_type text not null check (item_type in ('character', 'word', 'sentence')),
    -- 对应 words / sentences JSON 数组的下标；主字固定为 0。
    item_order integer not null check (item_order >= 0),
    item_text text not null check (char_length(btrim(item_text)) > 0),
    -- 仅在首次三星时写入，重复三星不覆盖首次学习时间。
    learned_at timestamptz not null default now(),
    unique (child_id, literacy_character_id, item_type, item_order)
);

create index if not exists child_literacy_learning_items_child_character_idx
    on public.child_literacy_learning_items (child_id, literacy_character_id);

alter table public.child_literacy_learning_items enable row level security;

drop policy if exists "child reads own literacy learning items" on public.child_literacy_learning_items;
create policy "child reads own literacy learning items" on public.child_literacy_learning_items
for select using (child_id = auth.uid());

-- 学习结果由 evaluate-reading 云函数使用 service_role 写入，客户端不开放写权限。
