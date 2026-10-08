-- 只读核对已部署的孩子本人快照；事务内模拟 JWT，末尾回滚。
begin;
do $verify$
declare
    v_child uuid;
    v_parent uuid;
    v_snapshot jsonb;
    v_tasks int;
    v_days int;
    v_duty int;
    v_exp bigint;
    v_badge_rows int;
begin
    select uid into v_child from public.users where role = 'child'
      order by (select count(*) from public.tasks where child_id = uid) desc limit 1;
    if v_child is null then raise exception '没有可验证的孩子账号'; end if;
    perform set_config('request.jwt.claim.sub', v_child::text, true);
    v_snapshot := public.growth_snapshot();
    if (v_snapshot->>'child_id')::uuid is distinct from v_child
       or jsonb_array_length(v_snapshot->'events') > 10 then
        raise exception '身份或足迹数量不符';
    end if;

    select count(*)::int, count(distinct (completed_at at time zone 'Asia/Shanghai')::date)::int
      into v_tasks, v_days from public.tasks
      where child_id = v_child and deleted_at is null and status in ('done','verified');
    select count(*)::int into v_duty from (
        select (completed_at at time zone 'Asia/Shanghai')::date
        from public.tasks where child_id = v_child and deleted_at is null
          and status in ('done','verified') and completed_at is not null
        group by 1 having count(*) >= 5
    ) days;
    select coalesce(sum(point.amount),0)::bigint into v_exp
      from public.tasks t
      left join lateral (
          select p.amount from public.point_records p
          where p.related_task_id = t.id and p.child_id = t.child_id and p.family_id = t.family_id
            and p.type = 'task_complete' and p.amount > 0
          order by p.timestamp nulls last, p.id limit 1
      ) point on true
      where t.child_id = v_child and t.deleted_at is null and t.status in ('done','verified');
    select count(*)::int into v_badge_rows from public.badge_progress where child_id = v_child;
    if (v_snapshot->>'completed_tasks')::int <> v_tasks
       or (v_snapshot->>'checkin_days')::int <> v_days
       or (v_snapshot->>'duty_days')::int <> v_duty
       or (v_snapshot->>'total_exp')::bigint <> v_exp
       or jsonb_array_length(v_snapshot->'badge_progress') <> v_badge_rows then
        raise exception '成长摘要与当前有效记录不符';
    end if;

    select uid into v_parent from public.users where role = 'parent' limit 1;
    if v_parent is not null then
        perform set_config('request.jwt.claim.sub', v_parent::text, true);
        begin
            perform public.growth_snapshot();
            raise exception '家长账号意外读取了孩子本人接口';
        exception when insufficient_privilege then null;
        end;
    end if;
    perform set_config('request.jwt.claim.sub', '', true);
    begin
        perform public.growth_snapshot();
        raise exception '匿名身份意外读取了孩子本人接口';
    exception when insufficient_privilege then null;
    end;
end;
$verify$;
rollback;
