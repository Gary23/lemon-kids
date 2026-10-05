-- 在已部署主迁移的数据库运行；所有测试数据位于同一事务，末尾回滚。
-- 需要至少一个已有家长与同家庭孩子；不输出任何家庭数据。
begin;

do $$
begin
    perform set_config('request.jwt.claim.sub', (
        select p.uid::text from public.users p
        where p.role = 'parent' and exists (
            select 1 from public.users c where c.family_id = p.family_id and c.role = 'child'
        ) limit 1
    ), true);
end;
$$;

set local role authenticated;

do $$
declare
    v_family uuid;
    v_child uuid;
    v_template uuid;
    v_created uuid;
    v_old uuid;
    v_old_client uuid;
    v_category_a uuid;
    v_category_b uuid;
    v_count integer;
    v_completed integer;
    v_child_points integer;
    v_completed_at timestamptz;
    v_denied boolean;
begin
    select family_id into v_family from public.users where uid = auth.uid() and role = 'parent';
    select uid, total_points into v_child, v_child_points from public.users
        where family_id = v_family and role = 'child' limit 1;
    if v_family is null or v_child is null then raise exception 'Test family unavailable'; end if;

    insert into public.task_templates(family_id, title, description, reward_points, penalty_points, growth_domain)
        values(v_family, '__growth_domain_verify__', '', 5, 2, 'reading') returning id into v_template;
    select id into v_created from public.create_tasks_from_selection(
        v_child, null, v_template, date '2099-12-31', date '2099-12-31', 'none', '{}'::integer[]
    );
    if v_created is null or not exists(select 1 from public.tasks
        where id = v_created and growth_domain = 'reading') then
        raise exception 'Template domain was not copied to a new task';
    end if;

    update public.task_templates set growth_domain = 'calculation' where id = v_template;
    if not exists(select 1 from public.tasks where id = v_created and growth_domain = 'reading') then
        raise exception 'Template edit changed an existing task snapshot';
    end if;
    perform * from public.create_tasks_from_selection(
        v_child, null, v_template, date '2099-12-31', date '2099-12-31', 'none', '{}'::integer[]
    );
    if not exists(select 1 from public.tasks where id = v_created and growth_domain = 'reading') then
        raise exception 'Deduplication overwrote an existing task snapshot';
    end if;

    insert into public.categories(family_id, name)
        values(v_family, '__growth_verify_a_' || gen_random_uuid()::text) returning id into v_category_a;
    insert into public.categories(family_id, name)
        values(v_family, '__growth_verify_b_' || gen_random_uuid()::text) returning id into v_category_b;
    insert into public.category_task_templates(category_id, template_id, family_id, sort_order)
        values(v_category_a, v_template, v_family, 0), (v_category_b, v_template, v_family, 0);
    perform * from public.create_tasks_from_selection(
        v_child, v_category_a, null, date '2099-12-28', date '2099-12-28', 'none', '{}'::integer[]
    );
    perform * from public.create_tasks_from_selection(
        v_child, v_category_b, null, date '2099-12-28', date '2099-12-28', 'none', '{}'::integer[]
    );
    if (select count(*) from public.tasks where source_template_id = v_template and due_date = date '2099-12-28') <> 1
        or not exists(select 1 from public.tasks where source_template_id = v_template
            and due_date = date '2099-12-28' and growth_domain = 'calculation') then
        raise exception 'Multiple categories changed the template domain snapshot';
    end if;

    -- 模拟旧客户端：插入时完全不传成长领域字段，应使用数据库默认 other。
    insert into public.tasks(family_id, title, description, child_id, created_by, status,
        category, source_template_id, due_date, reward_points, penalty_points)
    values(v_family, '__growth_domain_verify_legacy__', '', v_child, auth.uid(), 'pending',
        '其他', v_template, date '2099-12-29', 5, 2) returning id into v_old_client;
    if not exists(select 1 from public.tasks where id = v_old_client and growth_domain = 'other') then
        raise exception 'Legacy task insert did not default to other';
    end if;

    insert into public.tasks(family_id, title, description, child_id, created_by, status,
        category, source_template_id, growth_domain, due_date, reward_points, penalty_points, completed_at)
    values(v_family, '__growth_domain_verify_old__', '', v_child, auth.uid(), 'done',
        '其他', v_template, null, date '2099-12-30', 5, 2, now())
    returning id, completed_at into v_old, v_completed_at;

    select total_count, completed_count into v_count, v_completed
        from public.preview_task_growth_domain_backfill(v_template);
    if v_count <> 1 or v_completed <> 1 then raise exception 'Backfill preview count is incorrect'; end if;
    select public.backfill_task_growth_domain(v_template, 'calculation') into v_count;
    if v_count <> 1 then raise exception 'Backfill did not update exactly one unset task'; end if;
    select public.backfill_task_growth_domain(v_template, 'calculation') into v_count;
    if v_count <> 0 then raise exception 'Repeated backfill was not idempotent'; end if;
    if not exists(select 1 from public.tasks where id = v_old and growth_domain = 'calculation'
        and status = 'done' and completed_at = v_completed_at and reward_points = 5)
        or not exists(select 1 from public.task_growth_domain_audit
            where task_id = v_old and old_domain is null and new_domain = 'calculation'
                and operation = 'backfill' and changed_by = auth.uid()) then
        raise exception 'Backfill changed history or failed to write audit';
    end if;

    v_denied := false;
    begin
        update public.tasks set growth_domain = 'english' where id = v_old;
    exception when others then v_denied := true;
    end;
    if not v_denied then raise exception 'Direct completed task edit bypassed audit'; end if;

    perform public.correct_task_growth_domain(v_old, 'english');
    if not exists(select 1 from public.task_growth_domain_audit
        where task_id = v_old and old_domain = 'calculation' and new_domain = 'english'
            and operation = 'correction' and changed_by = auth.uid())
        or not exists(select 1 from public.tasks where id = v_old and growth_domain = 'english'
            and status = 'done' and completed_at = v_completed_at and reward_points = 5) then
        raise exception 'Correction did not preserve history and write audit';
    end if;

    v_denied := false;
    begin
        perform * from public.preview_task_growth_domain_backfill(gen_random_uuid());
    exception when others then v_denied := true;
    end;
    if not v_denied then raise exception 'Outside-family template preview was allowed'; end if;

    perform set_config('request.jwt.claim.sub', v_child::text, true);
    v_denied := false;
    begin
        update public.tasks set growth_domain = 'math_thinking' where id = v_created;
    exception when others then v_denied := true;
    end;
    if not v_denied then raise exception 'Child changed task growth domain'; end if;
    v_denied := false;
    begin
        update public.task_templates set growth_domain = 'life' where id = v_template;
    exception when others then v_denied := true;
    end;
    if not v_denied then raise exception 'Child changed template growth domain'; end if;

    if (select total_points from public.users where uid = v_child) is distinct from v_child_points then
        raise exception 'Domain operations changed child points';
    end if;
end;
$$;

reset role;
select 'growth domain verification passed' as result;
rollback;
