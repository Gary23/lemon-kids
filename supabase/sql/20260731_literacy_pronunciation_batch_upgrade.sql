-- 认字评测批量调用升级脚本
-- 仅用于：已经执行过 20260731_literacy_pronunciation_evaluation.sql 旧版的人。
-- 请在 Supabase Dashboard -> SQL Editor 中执行一次。
--
-- 本脚本不会删除评测记录：会先将 attempts.example_id 回填到新条目表，
-- 再删除旧的 example_id 列及其旧约束。

create table if not exists public.child_literacy_reading_attempt_items (
    id uuid primary key default gen_random_uuid(),
    attempt_id uuid not null references public.child_literacy_reading_attempts(id) on delete cascade,
    example_id uuid references public.child_literacy_examples(id) on delete set null,
    item_order integer not null check (item_order >= 0),
    target_text text not null,
    target_character_start integer not null default 0 check (target_character_start >= 0),
    target_character_count integer not null check (target_character_count > 0),
    created_at timestamptz not null default now(),
    unique (attempt_id, item_order),
    unique (attempt_id, target_character_start)
);

create index if not exists child_literacy_reading_attempt_items_attempt_idx
    on public.child_literacy_reading_attempt_items (attempt_id, item_order);

-- 旧版本的一次评测只关联一个 example_id；将它迁成一个 item。
-- character 类型没有 example_id，仍会生成一个 item，以便统一关联逐字结果。
insert into public.child_literacy_reading_attempt_items (
    attempt_id,
    example_id,
    item_order,
    target_text,
    target_character_start,
    target_character_count
)
select
    a.id,
    a.example_id,
    0,
    a.target_text,
    0,
    greatest(1, char_length(regexp_replace(a.target_text, '[^一-龥]', '', 'g')))
from public.child_literacy_reading_attempts a
on conflict (attempt_id, item_order) do nothing;

-- 先给既有逐字结果关联回填出的条目。尚无历史评测结果时，该 update 不会影响任何行。
alter table public.child_literacy_reading_character_results
    add column if not exists attempt_item_id uuid;

update public.child_literacy_reading_character_results result
set attempt_item_id = item.id
from public.child_literacy_reading_attempt_items item
where item.attempt_id = result.attempt_id
  and item.item_order = 0
  and result.attempt_item_id is null;

-- 旧结构的 example_id 只支持“一次评测一个词/句”。现在已完成回填，可删除该列。
-- 删除列时 PostgreSQL 会一并移除依赖它的旧 target_type/example_id 检查约束。
alter table public.child_literacy_reading_attempts
    drop column if exists example_id;

-- 旧版的 target_type 为 character / word / sentence；将历史 word 记录归为单条目的 word_group。
alter table public.child_literacy_reading_attempts
    drop constraint if exists child_literacy_reading_attempts_target_type_check;

update public.child_literacy_reading_attempts
set target_type = 'word_group'
where target_type = 'word';

alter table public.child_literacy_reading_attempts
    add constraint child_literacy_reading_attempts_target_type_check
    check (target_type in ('character', 'word_group', 'sentence'));

-- 逐字结果改为关联所属条目，而不是只以“整次请求内下标”关联。
-- 若此处报 attempt_item_id 仍有 NULL，停止执行并检查是否存在异常的历史 attempts 数据。
alter table public.child_literacy_reading_character_results
    alter column attempt_item_id set not null;

alter table public.child_literacy_reading_character_results
    add constraint child_literacy_reading_character_results_attempt_item_id_fkey
    foreign key (attempt_item_id)
    references public.child_literacy_reading_attempt_items(id)
    on delete cascade;

alter table public.child_literacy_reading_character_results
    drop constraint if exists child_literacy_reading_character_results_attempt_id_character_index_key;

alter table public.child_literacy_reading_character_results
    add constraint child_literacy_reading_character_results_attempt_item_id_character_index_key
    unique (attempt_item_id, character_index);

drop index if exists public.child_literacy_reading_character_results_attempt_idx;
create index child_literacy_reading_character_results_attempt_idx
    on public.child_literacy_reading_character_results (attempt_id, attempt_item_id, character_index);

alter table public.child_literacy_reading_attempt_items enable row level security;

drop policy if exists "child reads own literacy reading attempt items" on public.child_literacy_reading_attempt_items;
create policy "child reads own literacy reading attempt items" on public.child_literacy_reading_attempt_items
for select using (
    exists (
        select 1 from public.child_literacy_reading_attempts a
        where a.id = attempt_id and a.child_id = auth.uid()
    )
);

-- 验证：应没有 NULL；且每条历史 attempt 至少存在一个 item。
-- select count(*) as results_without_item
-- from public.child_literacy_reading_character_results
-- where attempt_item_id is null;
--
-- select count(*) as attempts_without_item
-- from public.child_literacy_reading_attempts a
-- left join public.child_literacy_reading_attempt_items i on i.attempt_id = a.id
-- where i.id is null;
