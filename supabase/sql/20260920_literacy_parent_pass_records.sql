-- 家长在认字弹层点击“通过”的不可变审计记录。
-- 写入只允许 evaluate-reading 云函数使用 service_role 完成；孩子端只读取自己的记录。

create table if not exists public.literacy_parent_pass_records (
    id uuid primary key default gen_random_uuid(),
    family_id uuid not null,
    child_id uuid not null references auth.users(id) on delete cascade,
    -- 不设外键：待认识任务完成后可能迁移、已认识字也可能存入字库，审计记录必须保留。
    literacy_character_id uuid not null,
    content_source text not null check (content_source in ('task', 'recognized')),
    character text not null check (char_length(btrim(character)) = 1),
    -- { character: {text, earned, required}, words: [...], sentences: [...] }，保存点击前的星级。
    star_snapshot jsonb not null check (jsonb_typeof(star_snapshot) = 'object'),
    passed_at timestamptz not null default now(),
    -- 撤销不删除审计证据：保留原始通过记录，并标识其星级已按快照恢复。
    undone_at timestamptz
);

-- 兼容已执行过首版迁移的环境。
alter table public.literacy_parent_pass_records
    add column if not exists undone_at timestamptz;

create index if not exists literacy_parent_pass_records_child_passed_idx
    on public.literacy_parent_pass_records (child_id, passed_at desc);

alter table public.literacy_parent_pass_records enable row level security;

drop policy if exists "child reads own literacy parent pass records" on public.literacy_parent_pass_records;
create policy "child reads own literacy parent pass records"
on public.literacy_parent_pass_records
for select using (child_id = auth.uid());

-- 不创建 insert/update/delete 策略：记录由 evaluate-reading 云函数以 service_role 写入，
-- 并且不允许客户端篡改或删除。
