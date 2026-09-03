-- 认字首页当天字、词、句朗读进度的跨设备同步。
-- 客户端始终先本地落盘并在后台调用 evaluate-reading；写入仅允许 service_role 通过
-- 下方 RPC 完成，避免绑定码对应的孩子直接伪造其他学习项目。

create table if not exists public.child_literacy_practice_progress (
    child_id uuid not null references auth.users(id) on delete cascade,
    progress_date date not null,
    content_source text not null check (content_source in ('task', 'recognized')),
    literacy_character_id uuid not null,
    target_type text not null check (target_type in ('character', 'word', 'sentence')),
    item_order integer not null check (item_order >= 0),
    correct_readings smallint not null check (correct_readings between 0 and 3),
    updated_at timestamptz not null default now(),
    primary key (child_id, progress_date, content_source, literacy_character_id, target_type, item_order)
);

create index if not exists child_literacy_practice_progress_child_date_idx
    on public.child_literacy_practice_progress (child_id, progress_date);

alter table public.child_literacy_practice_progress enable row level security;

drop policy if exists "child reads own literacy practice progress" on public.child_literacy_practice_progress;
create policy "child reads own literacy practice progress" on public.child_literacy_practice_progress
for select using (child_id = auth.uid());

create or replace function public.record_literacy_practice_progress(
    p_child_id uuid,
    p_content_source text,
    p_literacy_character_id uuid,
    p_target_type text,
    p_item_order integer,
    p_correct_readings smallint
)
returns smallint
language plpgsql
security definer
set search_path = public
as $$
declare
    stored_count smallint;
begin
    if p_content_source not in ('task', 'recognized') then
        raise exception '无效的认字内容来源';
    end if;
    if p_target_type not in ('character', 'word', 'sentence') then
        raise exception '无效的认字内容类型';
    end if;
    if p_item_order < 0 or p_correct_readings not between 0 and 3 then
        raise exception '无效的认字朗读进度';
    end if;

    insert into public.child_literacy_practice_progress (
        child_id, progress_date, content_source, literacy_character_id,
        target_type, item_order, correct_readings
    ) values (
        p_child_id,
        (timezone('Asia/Shanghai', now()))::date,
        p_content_source,
        p_literacy_character_id,
        p_target_type,
        p_item_order,
        p_correct_readings
    )
    on conflict (child_id, progress_date, content_source, literacy_character_id, target_type, item_order)
    do update set
        correct_readings = greatest(
            public.child_literacy_practice_progress.correct_readings,
            excluded.correct_readings
        ),
        updated_at = now()
    returning correct_readings into stored_count;

    return stored_count;
end;
$$;

revoke all on function public.record_literacy_practice_progress(uuid, text, uuid, text, integer, smallint)
    from public, anon, authenticated;
grant execute on function public.record_literacy_practice_progress(uuid, text, uuid, text, integer, smallint)
    to service_role;

-- 部署后核验：
-- select child_id, progress_date, content_source, target_type, item_order, correct_readings
-- from public.child_literacy_practice_progress
-- order by updated_at desc;
