-- 孩子端“我的成长”只读快照。先部署家长端 badge_progress 和奖励兑换表。
begin;

create index if not exists tasks_growth_child_time_idx
    on public.tasks(child_id, completed_at desc)
    where deleted_at is null and status in ('done', 'verified');
create index if not exists point_records_growth_task_idx
    on public.point_records(child_id, related_task_id, timestamp, id)
    where type = 'task_complete' and amount > 0;

create or replace function public.growth_snapshot()
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
    v_child_id uuid := auth.uid();
    v_result jsonb;
begin
    if v_child_id is null or not exists (
        select 1 from public.users where uid = v_child_id and role = 'child'
    ) then
        raise exception 'Growth snapshot requires a child account' using errcode = '42501';
    end if;

    with valid_tasks as (
        select t.id, t.title, t.completed_at, t.family_id
        from public.tasks t
        where t.child_id = v_child_id and t.deleted_at is null
          and t.status in ('done', 'verified')
    ),
    task_points as (
        -- 一条任务只认最早的一笔有效正向到账，避免重复流水叠加 EXP。
        select distinct on (p.related_task_id) p.related_task_id, p.amount, p.timestamp, p.id
        from public.point_records p join valid_tasks t
          on t.id = p.related_task_id and t.family_id = p.family_id
        where p.child_id = v_child_id and p.type = 'task_complete' and p.amount > 0
        order by p.related_task_id, p.timestamp nulls last, p.id
    ),
    task_days as (
        select (completed_at at time zone 'Asia/Shanghai')::date as day,
          count(*)::int as count, min(completed_at) as first_at,
          (array_agg(completed_at order by completed_at))[5] as fifth_at
        from valid_tasks where completed_at is not null
        group by 1
    ),
    day_totals as (
        select day, count, first_at, fifth_at,
          sum((count >= 1)::int) over (order by day)::int as checkin_days,
          sum((count >= 5)::int) over (order by day)::int as duty_days
        from task_days
    ),
    point_totals as (
        select p.related_task_id, p.amount, p.timestamp, p.id,
          sum(p.amount::bigint) over (order by p.timestamp, p.id) as total_exp
        from task_points p where p.timestamp is not null
    ),
    level_thresholds as (
        select n, (select sum(least(700, 250 + 25 * (step - 2)))
                   from generate_series(2, n) step)::bigint as threshold
        from generate_series(2, 85) n
    ),
    habit_thresholds as (
        select n, (array[1,25,60,105,160,225,300,385,480,580,680,780,890,1000,1100])[n] as threshold
        from generate_series(1,15) n
    ),
    events as (
        select 'task'::text as kind, t.completed_at as occurred_at, t.title as subject,
          coalesce(p.amount,0)::bigint as value, null::text as badge_key, null::text as segment,
          null::int as level
        from valid_tasks t left join task_points p on p.related_task_id = t.id
        where t.completed_at is not null
        union all
        select 'badge', a.changed_at, a.badge_key, a.new_value, a.badge_key, a.segment, null::int
        from public.badge_progress_audit a where a.child_id = v_child_id
        union all
        select 'reward', r.used_at, r.title_snapshot, 0, null, null, null::int
        from public.reward_redemptions r where r.child_id = v_child_id and r.status = 'used' and r.used_at is not null
        union all
        select 'level', p.timestamp, '', p.total_exp, null, null, l.n
        from point_totals p join level_thresholds l
          on p.total_exp >= l.threshold and p.total_exp - p.amount < l.threshold
        union all
        select 'habit', d.fifth_at, 'task_duty', d.duty_days, 'task_duty', '', h.n
        from day_totals d join habit_thresholds h on d.duty_days = h.threshold and d.count >= 5
        union all
        select 'habit', d.first_at, 'checkin', d.checkin_days, 'checkin', '', h.n
        from day_totals d join habit_thresholds h on d.checkin_days = h.threshold
    ),
    recent_events as (
        select * from events where occurred_at is not null
        order by occurred_at desc, kind limit 10
    )
    select jsonb_build_object(
        'child_id', v_child_id,
        'as_of', now(),
        'completed_tasks', (select count(*)::int from valid_tasks),
        'checkin_days', (select count(*)::int from task_days),
        'duty_days', (select count(*)::int from task_days where count >= 5),
        'total_exp', (select coalesce(sum(amount::bigint),0) from task_points),
        'badge_progress', (select coalesce(jsonb_agg(jsonb_build_object(
            'badge_key', badge_key, 'segment', segment, 'value', value, 'updated_at', updated_at)
            order by badge_key, segment), '[]'::jsonb)
            from public.badge_progress where child_id = v_child_id),
        'events', (select coalesce(jsonb_agg(jsonb_build_object(
            'kind', kind, 'occurred_at', occurred_at, 'subject', subject,
            'value', value, 'badge_key', badge_key, 'segment', segment, 'level', level)
            order by occurred_at desc, kind), '[]'::jsonb) from recent_events)
    ) into v_result;
    return v_result;
end;
$$;

revoke all on function public.growth_snapshot() from public, anon;
grant execute on function public.growth_snapshot() to authenticated;
commit;
