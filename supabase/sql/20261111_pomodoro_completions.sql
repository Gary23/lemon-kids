-- 柠檬任务番茄钟：一段完整专注一条完成记录，数量由服务端按日聚合。
-- 先部署本文件，再发布含番茄入口的任务端。仅新增，不修改任务、积分或旧计时数据。
begin;

create table if not exists public.pomodoro_completions (
    id uuid primary key default gen_random_uuid(),
    child_id uuid not null references public.users(uid) on delete cascade,
    family_id uuid not null references public.families(id) on delete cascade,
    session_id uuid not null,
    focus_minutes integer not null check (focus_minutes in (10, 15, 20, 25, 30, 35, 40)),
    break_minutes integer not null check (break_minutes in (3, 5, 10, 15)),
    started_at timestamptz not null,
    completed_at timestamptz not null,
    completed_date date not null,
    received_at timestamptz not null default now(),
    constraint pomodoro_completions_session_key unique (child_id, session_id),
    constraint pomodoro_completions_duration_check
        check (completed_at = started_at + make_interval(mins => focus_minutes))
);
create index if not exists pomodoro_completions_child_date_idx
    on public.pomodoro_completions(child_id, completed_date);

alter table public.pomodoro_completions enable row level security;
drop policy if exists pomodoro_completions_select_own on public.pomodoro_completions;
create policy pomodoro_completions_select_own on public.pomodoro_completions
    for select to authenticated using (
        child_id = auth.uid()
        and exists (
            select 1 from public.users u
            where u.uid = auth.uid() and u.role = 'child'
              and u.family_id = pomodoro_completions.family_id
        )
    );
revoke all on public.pomodoro_completions from anon, authenticated;
grant select on public.pomodoro_completions to authenticated;

create or replace function public.record_pomodoro_completion(
    p_session_id uuid,
    p_focus_minutes integer,
    p_break_minutes integer,
    p_started_at timestamptz,
    p_completed_at timestamptz
) returns table (session_id uuid, completed_date date, inserted boolean)
language plpgsql security definer set search_path = public, pg_temp as $$
#variable_conflict use_column
declare
    v_child_id uuid := auth.uid();
    v_family_id uuid;
    v_completed_date date;
    v_existing public.pomodoro_completions%rowtype;
begin
    if v_child_id is null then
        raise exception 'Pomodoro completion requires a signed-in account' using errcode = '42501';
    end if;
    select u.family_id into v_family_id
    from public.users u
    where u.uid = v_child_id and u.role = 'child';
    if v_family_id is null then
        raise exception 'Pomodoro completion requires a child account' using errcode = '42501';
    end if;
    if p_session_id is null then
        raise exception 'Pomodoro session id is required' using errcode = '22023';
    end if;
    if p_focus_minutes is null or p_focus_minutes not in (10, 15, 20, 25, 30, 35, 40) then
        raise exception 'Unsupported focus duration' using errcode = '22023';
    end if;
    if p_break_minutes is null or p_break_minutes not in (3, 5, 10, 15) then
        raise exception 'Unsupported break duration' using errcode = '22023';
    end if;
    if p_started_at is null or p_completed_at is null then
        raise exception 'Pomodoro start and completion time are required' using errcode = '22023';
    end if;
    if p_completed_at <> p_started_at + make_interval(mins => p_focus_minutes) then
        raise exception 'Pomodoro completion does not match focus duration' using errcode = '22023';
    end if;
    if p_completed_at > now() + interval '5 minutes' then
        raise exception 'Pomodoro completion cannot be in the future' using errcode = '22023';
    end if;

    v_completed_date := (p_completed_at at time zone 'Asia/Shanghai')::date;
    insert into public.pomodoro_completions(
        child_id, family_id, session_id, focus_minutes, break_minutes,
        started_at, completed_at, completed_date
    ) values (
        v_child_id, v_family_id, p_session_id, p_focus_minutes, p_break_minutes,
        p_started_at, p_completed_at, v_completed_date
    ) on conflict on constraint pomodoro_completions_session_key do nothing;

    if found then
        return query select p_session_id, v_completed_date, true;
        return;
    end if;

    select * into v_existing
    from public.pomodoro_completions c
    where c.child_id = v_child_id and c.session_id = p_session_id;
    if not found then
        raise exception 'Pomodoro completion conflict could not be resolved' using errcode = '40001';
    end if;
    if v_existing.family_id <> v_family_id
        or v_existing.focus_minutes <> p_focus_minutes
        or v_existing.break_minutes <> p_break_minutes
        or v_existing.started_at <> p_started_at
        or v_existing.completed_at <> p_completed_at then
        raise exception 'Pomodoro session id reused with different payload' using errcode = '23505';
    end if;
    return query select p_session_id, v_existing.completed_date, false;
end;
$$;

revoke all on function public.record_pomodoro_completion(uuid, integer, integer, timestamptz, timestamptz)
    from public, anon;
grant execute on function public.record_pomodoro_completion(uuid, integer, integer, timestamptz, timestamptz)
    to authenticated;

create or replace function public.get_pomodoro_daily_counts(
    p_from_date date,
    p_to_date date
) returns table (completed_date date, "count" integer)
language plpgsql stable security definer set search_path = public, pg_temp as $$
declare
    v_child_id uuid := auth.uid();
begin
    if v_child_id is null or not exists (
        select 1 from public.users u where u.uid = v_child_id and u.role = 'child'
    ) then
        raise exception 'Pomodoro counts require a child account' using errcode = '42501';
    end if;
    if p_from_date is null or p_to_date is null or p_to_date < p_from_date then
        raise exception 'Invalid pomodoro date range' using errcode = '22023';
    end if;
    if p_to_date - p_from_date > 62 then
        raise exception 'Pomodoro date range exceeds 62 days' using errcode = '22023';
    end if;
    return query
    select c.completed_date, count(*)::integer
    from public.pomodoro_completions c
    where c.child_id = v_child_id
      and c.completed_date between p_from_date and p_to_date
    group by c.completed_date
    order by c.completed_date;
end;
$$;

revoke all on function public.get_pomodoro_daily_counts(date, date) from public, anon;
grant execute on function public.get_pomodoro_daily_counts(date, date) to authenticated;

notify pgrst, 'reload schema';
commit;
