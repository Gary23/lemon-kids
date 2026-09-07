-- 认字首页：跨 Pad 共享的当天待认识任务快照。
--
-- 前置：child_literacy_characters 已存在，且孩子端以自身 auth.uid() 登录。
-- 在 Supabase Dashboard -> SQL Editor 人工审查并执行。
--
-- 每个孩子在北京时间的每一天只有一个快照；items 中的任务即使当天完成、
-- learned_at 被写入，也不会从快照移除。次日才会按最新未完成任务重新选字。

create table if not exists public.child_literacy_daily_task_snapshots (
    id uuid primary key default gen_random_uuid(),
    child_id uuid not null references auth.users(id) on delete cascade,
    snapshot_date date not null,
    created_at timestamptz not null default now(),
    unique (child_id, snapshot_date)
);

create table if not exists public.child_literacy_daily_task_snapshot_items (
    snapshot_id uuid not null references public.child_literacy_daily_task_snapshots(id) on delete cascade,
    literacy_character_id uuid not null references public.child_literacy_characters(id) on delete cascade,
    position smallint not null check (position between 0 and 5),
    primary key (snapshot_id, position),
    unique (snapshot_id, literacy_character_id)
);

create index if not exists child_literacy_daily_task_snapshot_items_character_idx
    on public.child_literacy_daily_task_snapshot_items (literacy_character_id);

alter table public.child_literacy_daily_task_snapshots enable row level security;
alter table public.child_literacy_daily_task_snapshot_items enable row level security;

-- 客户端只需要调用下方 RPC；保留自己的只读权限，便于受限诊断和日后查询。
drop policy if exists "child reads own daily literacy snapshots" on public.child_literacy_daily_task_snapshots;
create policy "child reads own daily literacy snapshots"
on public.child_literacy_daily_task_snapshots for select
using (child_id = auth.uid());

drop policy if exists "child reads own daily literacy snapshot items" on public.child_literacy_daily_task_snapshot_items;
create policy "child reads own daily literacy snapshot items"
on public.child_literacy_daily_task_snapshot_items for select
using (
    exists (
        select 1
          from public.child_literacy_daily_task_snapshots snapshot
         where snapshot.id = snapshot_id
           and snapshot.child_id = auth.uid()
    )
);

create or replace function public.get_or_create_today_literacy_tasks(
    p_child_id uuid,
    p_preferred_literacy_character_ids uuid[] default array[]::uuid[]
)
returns setof public.child_literacy_characters
language plpgsql
security definer
set search_path = public
as $$
declare
    today_in_china date := (timezone('Asia/Shanghai', now()))::date;
    today_start_in_china timestamptz := timezone(
        'Asia/Shanghai',
        ((timezone('Asia/Shanghai', now()))::date)::timestamp
    );
    v_snapshot_id uuid;
    selected_task_ids uuid[];
    completed_today_task_ids uuid[];
    should_rebuild_snapshot boolean := false;
begin
    -- 此 RPC 供孩子端直接调用；不能借 child_id 读取或创建其他孩子的快照。
    if auth.uid() is null or auth.uid() <> p_child_id then
        raise exception '只能读取自己的当天认字任务';
    end if;

    -- 同一孩子同一天的首次请求串行化，避免两台 Pad 并发选出两批不同任务。
    perform pg_advisory_xact_lock(hashtext('literacy-daily-snapshot:' || p_child_id::text || ':' || today_in_china::text));

    -- 兼容上线前已在当天完成学习的旧客户端：那时只有本机快照，若本机没有
    -- 可迁入的记录，不能把这些任务当成“下一批”。按北京时间取得当天完成的
    -- 历史任务，后面会与未完成任务按原排序合并为同一批最多 6 个字。
    select array_agg(completed.id order by completed.sort_order, completed.character, completed.id)
      into completed_today_task_ids
      from (
          select id, sort_order, character
            from public.child_literacy_characters
           where child_id = p_child_id
             and learned_at >= today_start_in_china
             and learned_at < today_start_in_china + interval '1 day'
           order by sort_order asc, character asc, id asc
           limit 6
      ) completed;

    select id into v_snapshot_id
      from public.child_literacy_daily_task_snapshots
     where child_id = p_child_id and snapshot_date = today_in_china;

    -- 若刚上线时已经错误地把“下一批”写成当天快照，而当天完成任务完全不在
    -- 该快照中，则以今天已完成的历史任务恢复原批次。正常运行时，任务完成后
    -- 会仍留在已有快照内，因此不会触发这段修复。
    if v_snapshot_id is not null
       and coalesce(cardinality(completed_today_task_ids), 0) > 0
       and not exists (
           select 1
             from public.child_literacy_daily_task_snapshot_items item
             join public.child_literacy_characters task on task.id = item.literacy_character_id
            where item.snapshot_id = v_snapshot_id
              and task.id = any(completed_today_task_ids)
       ) then
        delete from public.child_literacy_daily_task_snapshot_items
         where snapshot_id = v_snapshot_id;
        should_rebuild_snapshot := true;
    end if;

    if v_snapshot_id is null or should_rebuild_snapshot then
        -- 升级当日，本机已有快照可优先迁入；只能使用当前孩子的任务，最多 6 条，
        -- 且保持本机原有顺序。之后任何设备都只能读取这一服务端结果。
        -- 修复已错误生成的服务端快照时必须忽略该错误快照的本机副本，优先恢复
        -- 今天已经完成的原任务。
        if not should_rebuild_snapshot
           and coalesce(cardinality(completed_today_task_ids), 0) = 0
           and coalesce(cardinality(p_preferred_literacy_character_ids), 0) > 0 then
            select array_agg(preferred.id order by preferred.position)
              into selected_task_ids
              from (
                  select distinct on (requested.literacy_character_id)
                      requested.literacy_character_id as id,
                      requested.position
                    from unnest(p_preferred_literacy_character_ids)
                         with ordinality as requested(literacy_character_id, position)
                    join public.child_literacy_characters task
                      on task.id = requested.literacy_character_id
                     and task.child_id = p_child_id
                   order by requested.literacy_character_id, requested.position
                   limit 6
              ) preferred;
        end if;

        if coalesce(cardinality(selected_task_ids), 0) = 0 then
            select array_agg(candidate.id order by candidate.sort_order, candidate.character, candidate.id)
              into selected_task_ids
              from (
                  select id, sort_order, character
                    from public.child_literacy_characters
                   where child_id = p_child_id
                     and (
                         learned_at is null
                         or id = any(coalesce(completed_today_task_ids, array[]::uuid[]))
                     )
                   order by sort_order asc, character asc, id asc
                   limit 6
                   for update
              ) candidate;
        end if;

        -- 没有待认识字时不建立空快照，家长当天新增任务后仍可正常生成。
        if coalesce(cardinality(selected_task_ids), 0) = 0 then
            return;
        end if;

        if v_snapshot_id is null then
            insert into public.child_literacy_daily_task_snapshots (child_id, snapshot_date)
            values (p_child_id, today_in_china)
            returning id into v_snapshot_id;
        end if;

        insert into public.child_literacy_daily_task_snapshot_items (
            snapshot_id, literacy_character_id, position
        )
        select v_snapshot_id, selected.id, selected.position - 1
          from unnest(selected_task_ids) with ordinality as selected(id, position);
    end if;

    return query
    select task.*
      from public.child_literacy_daily_task_snapshot_items item
      join public.child_literacy_characters task on task.id = item.literacy_character_id
     where item.snapshot_id = v_snapshot_id
     order by item.position asc;
end;
$$;

revoke all on function public.get_or_create_today_literacy_tasks(uuid, uuid[])
    from public, anon, authenticated;
grant execute on function public.get_or_create_today_literacy_tasks(uuid, uuid[])
    to authenticated;

-- 部署后核验：同一 child_id、snapshot_date 应只有一条快照，且每条最多 6 个任务。
-- select snapshot.child_id, snapshot.snapshot_date,
--        array_agg(task.character order by item.position) as characters
--   from public.child_literacy_daily_task_snapshots snapshot
--   join public.child_literacy_daily_task_snapshot_items item on item.snapshot_id = snapshot.id
--   join public.child_literacy_characters task on task.id = item.literacy_character_id
--  group by snapshot.child_id, snapshot.snapshot_date
--  order by snapshot.snapshot_date desc;
