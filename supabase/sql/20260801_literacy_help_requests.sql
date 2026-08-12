-- 认字：记录孩子主动请求朗读词或句的事件。
-- 依赖：child_literacy_characters 已存在。由 evaluate-reading 云函数以 service_role 写入；
-- 客户端不开放写权限，避免伪造求助记录。
--
-- 不记录腾讯评测的原始响应、逐字分数或红绿结果。旧评测表仅为兼容已部署环境保留，
-- 新版云函数不会再向其中写入数据。

create table if not exists public.child_literacy_character_help_requests (
    id uuid primary key default gen_random_uuid(),
    family_id uuid not null,
    child_id uuid not null references auth.users(id) on delete cascade,
    literacy_character_id uuid not null references public.child_literacy_characters(id) on delete cascade,
    target_type text not null check (target_type in ('character', 'word_group', 'sentence')),
    target_text text not null,
    created_at timestamptz not null default now()
);

create index if not exists child_literacy_help_requests_child_created_idx
    on public.child_literacy_character_help_requests (child_id, created_at desc);

create index if not exists child_literacy_help_requests_content_created_idx
    on public.child_literacy_character_help_requests (literacy_character_id, target_type, created_at desc);

alter table public.child_literacy_character_help_requests enable row level security;

drop policy if exists "child reads own literacy help requests" on public.child_literacy_character_help_requests;
create policy "child reads own literacy help requests" on public.child_literacy_character_help_requests
for select using (child_id = auth.uid());

-- 验证：执行一次点读后应出现对应行。
-- select target_type, target_text, count(*) as request_count, max(created_at) as last_requested_at
-- from public.child_literacy_character_help_requests
-- where child_id = '<孩子 UUID>'
-- group by target_type, target_text
-- order by last_requested_at desc;
