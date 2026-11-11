-- 番茄暂停兼容迁移；仅在原番茄迁移已部署后执行。
-- 只放宽真实经过时长，保留身份、允许时长、未来时间与 session_id 幂等校验。
-- 部署前需数据库操作授权。单事务；ALTER TABLE 会短暂获取表锁。
-- 回滚不要直接恢复等式：先检查暂停记录，否则旧 CHECK 会因历史记录失败。
begin;

alter table public.pomodoro_completions
    drop constraint pomodoro_completions_duration_check;
alter table public.pomodoro_completions
    add constraint pomodoro_completions_duration_check
    check (isfinite(started_at) and isfinite(completed_at)
        and completed_at >= started_at + make_interval(mins => focus_minutes));

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
    if not isfinite(p_started_at) or not isfinite(p_completed_at) then
        raise exception 'Pomodoro start and completion time must be finite' using errcode = '22023';
    end if;
    if p_completed_at < p_started_at + make_interval(mins => p_focus_minutes) then
        raise exception 'Pomodoro completion is shorter than focus duration' using errcode = '22023';
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

notify pgrst, 'reload schema';
commit;
