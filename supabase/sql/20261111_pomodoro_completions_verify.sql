-- 在已部署 20261111_pomodoro_completions.sql 的数据库执行。全部写入在末尾回滚。
begin;
do $$
begin
    perform set_config('request.jwt.claim.sub', (
        select u.uid::text from public.users u
        where u.role = 'child' and u.family_id is not null
        order by u.uid limit 1
    ), true);
end;
$$;
set local role authenticated;

do $$
declare
    v_child uuid := auth.uid();
    v_session uuid := gen_random_uuid();
    v_paused_session uuid := gen_random_uuid();
    v_started timestamptz := '2026-03-01 15:55:00+00';
    v_completed timestamptz := v_started + interval '25 minutes';
    v_date date;
    v_inserted boolean;
    v_count integer;
    v_denied boolean;
    v_row_count integer;
begin
    if v_child is null then raise exception 'No child account available'; end if;

    select completed_date, inserted into v_date, v_inserted
    from public.record_pomodoro_completion(v_session, 25, 5, v_started, v_completed);
    if not v_inserted or v_date <> date '2026-03-02' then
        raise exception 'Completion insert or Shanghai date attribution failed: %, %', v_date, v_inserted;
    end if;

    select completed_date, inserted into v_date, v_inserted
    from public.record_pomodoro_completion(v_session, 25, 5, v_started, v_completed);
    if v_inserted then raise exception 'Idempotent retry inserted a duplicate'; end if;
    select count(*) into v_row_count from public.pomodoro_completions
    where child_id = v_child and session_id = v_session;
    if v_row_count <> 1 then raise exception 'Duplicate completion rows: %', v_row_count; end if;

    -- 暂停跨上海午夜：有效专注25分钟，真实经过40分钟；不得伪造开始时间。
    select completed_date, inserted into v_date, v_inserted
    from public.record_pomodoro_completion(v_paused_session, 25, 5,
        '2026-03-01 15:30:00+00', '2026-03-01 16:10:00+00');
    if not v_inserted or v_date <> date '2026-03-02' then
        raise exception 'Paused completion or actual completion date attribution failed';
    end if;
    if not exists (
        select 1 from public.pomodoro_completions c
        where c.child_id = v_child and c.session_id = v_paused_session
            and c.started_at = timestamptz '2026-03-01 15:30:00+00'
            and c.completed_at = timestamptz '2026-03-01 16:10:00+00'
    ) then raise exception 'Paused completion real timestamps were altered'; end if;
    select inserted into v_inserted
    from public.record_pomodoro_completion(v_paused_session, 25, 5,
        '2026-03-01 15:30:00+00', '2026-03-01 16:10:00+00');
    if v_inserted then raise exception 'Paused retry inserted a duplicate'; end if;
    begin
        perform public.record_pomodoro_completion(v_paused_session, 25, 5,
            '2026-03-01 15:30:00+00', '2026-03-01 16:11:00+00');
        raise exception 'Paused session accepted a changed real completion time';
    exception when unique_violation then null;
    end;
    begin
        perform public.record_pomodoro_completion(gen_random_uuid(), 25, 5,
            v_started, v_completed - interval '1 millisecond');
        raise exception 'Short completion was accepted';
    exception when invalid_parameter_value then null;
    end;
    begin
        perform public.record_pomodoro_completion(gen_random_uuid(), 25, 5,
            '-infinity'::timestamptz, v_completed);
        raise exception 'Infinite start time was accepted';
    exception when invalid_parameter_value then null;
    end;

    v_denied := false;
    begin
        perform public.record_pomodoro_completion(v_session, 30, 5, v_started, v_started + interval '30 minutes');
    exception when others then v_denied := true;
    end;
    if not v_denied then raise exception 'Reused session id with different payload was accepted'; end if;

    v_denied := false;
    begin
        perform public.record_pomodoro_completion(gen_random_uuid(), 7, 5, v_started, v_started + interval '7 minutes');
    exception when others then v_denied := true;
    end;
    if not v_denied then raise exception 'Unsupported focus duration was accepted'; end if;

    v_denied := false;
    begin
        perform public.record_pomodoro_completion(gen_random_uuid(), 25, 5, now() + interval '1 hour', now() + interval '1 hour 25 minutes');
    exception when others then v_denied := true;
    end;
    if not v_denied then raise exception 'Future completion was accepted'; end if;

    v_denied := false;
    begin
        insert into public.pomodoro_completions(child_id, family_id, session_id, focus_minutes, break_minutes,
            started_at, completed_at, completed_date)
        select child_id, family_id, gen_random_uuid(), 25, 5, v_started, v_completed, v_date
        from public.pomodoro_completions where child_id = v_child limit 1;
    exception when others then v_denied := true;
    end;
    if not v_denied then raise exception 'Direct table insert bypassed the controlled RPC'; end if;

    select count into v_count from public.get_pomodoro_daily_counts(date '2026-03-01', date '2026-03-31')
    where completed_date = date '2026-03-02';
    if coalesce(v_count, 0) < 1 then raise exception 'Daily count aggregation missed the record'; end if;

    v_denied := false;
    begin
        perform public.get_pomodoro_daily_counts(date '2026-01-01', date '2026-06-01');
    exception when others then v_denied := true;
    end;
    if not v_denied then raise exception 'Date range over 62 days was accepted'; end if;

    v_denied := false;
    begin
        update public.pomodoro_completions set completed_date = current_date where child_id = v_child;
    exception when others then v_denied := true;
    end;
    if not v_denied then raise exception 'Direct table update was accepted'; end if;
end;
$$;

reset role;
set local role anon;
do $$
declare
    v_denied boolean := false;
begin
    begin
        perform 1 from public.pomodoro_completions limit 1;
    exception when others then v_denied := true;
    end;
    if not v_denied then raise exception 'Anonymous read was accepted'; end if;
end;
$$;
reset role;
rollback;
