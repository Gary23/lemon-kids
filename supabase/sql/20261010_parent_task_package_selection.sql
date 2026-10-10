-- 家长端任务包按所选模板原子创建，保留旧入口。
begin;

create or replace function public.create_tasks_from_selection(
    p_child_id uuid, p_category_id uuid, p_template_id uuid,
    p_start_date date, p_end_date date, p_recurrence_type text,
    p_recurrence_weekdays integer[], p_selected_template_ids uuid[]
)
returns setof public.tasks language plpgsql security definer set search_path = public as $$
declare
    v_family_id uuid; v_category_name text := '其他'; v_template record;
    v_task public.tasks%rowtype; v_date date; v_series_id uuid;
    v_template_count integer; v_date_count integer;
begin
    if (p_category_id is null) = (p_template_id is null) then raise exception 'Choose exactly one category or task template'; end if;
    if p_start_date is null or p_end_date is null or p_end_date < p_start_date then raise exception 'Invalid task date range'; end if;
    if p_recurrence_type not in ('none','daily','weekdays','weekly') then raise exception 'Invalid recurrence type'; end if;
    if p_recurrence_type = 'weekly' and cardinality(coalesce(p_recurrence_weekdays, '{}'::integer[])) = 0 then
        raise exception 'Weekly recurrence requires weekdays';
    end if;
    select family_id into v_family_id from public.users where uid = auth.uid() and role = 'parent';
    if v_family_id is null then raise exception 'Only parents can create tasks'; end if;
    if not exists(select 1 from public.users where uid = p_child_id and family_id = v_family_id and role = 'child') then
        raise exception 'Child is outside current family';
    end if;
    if p_category_id is not null then
        select name into v_category_name from public.categories where id = p_category_id and family_id = v_family_id;
        if v_category_name is null then raise exception 'Category is outside current family'; end if;
        if p_selected_template_ids is not null then
            if cardinality(p_selected_template_ids) = 0
               or array_position(p_selected_template_ids, null) is not null
               or cardinality(p_selected_template_ids) !=
                  (select count(distinct selected.id) from unnest(p_selected_template_ids) as selected(id)) then
                raise exception 'Select at least one distinct task template';
            end if;
            select count(*) into v_template_count
            from public.category_task_templates ctt
            join public.task_templates t on t.id = ctt.template_id and t.family_id = v_family_id
            where ctt.category_id = p_category_id and ctt.family_id = v_family_id
              and ctt.template_id = any(p_selected_template_ids);
            if v_template_count != cardinality(p_selected_template_ids) then
                raise exception 'Selected task template is outside category or family';
            end if;
        else
            select count(*) into v_template_count from public.category_task_templates
                where category_id = p_category_id and family_id = v_family_id;
        end if;
    else
        if p_selected_template_ids is not null then
            raise exception 'Selected templates require a category';
        end if;
        if not exists(select 1 from public.task_templates where id = p_template_id and family_id = v_family_id) then
            raise exception 'Task template is outside current family';
        end if;
        v_template_count := 1;
    end if;
    if v_template_count = 0 then raise exception 'Category has no tasks'; end if;
    select count(*) into v_date_count
    from generate_series(p_start_date, p_end_date, interval '1 day') as day_value
    where p_recurrence_type in ('none','daily')
        or (p_recurrence_type = 'weekdays' and extract(isodow from day_value) between 1 and 5)
        or (p_recurrence_type = 'weekly' and extract(isodow from day_value)::integer = any(p_recurrence_weekdays));
    if v_date_count * v_template_count > 500 then raise exception 'At most 500 tasks can be created at once'; end if;
    v_series_id := case when p_recurrence_type = 'none' then null else gen_random_uuid() end;
    for v_date in
        select day_value::date from generate_series(p_start_date, p_end_date, interval '1 day') as day_value
        where p_recurrence_type in ('none','daily')
            or (p_recurrence_type = 'weekdays' and extract(isodow from day_value) between 1 and 5)
            or (p_recurrence_type = 'weekly' and extract(isodow from day_value)::integer = any(p_recurrence_weekdays))
    loop
        for v_template in
            select t.* from public.task_templates t where t.family_id = v_family_id and (
                (p_category_id is not null and exists(select 1 from public.category_task_templates ctt
                    where ctt.category_id = p_category_id and ctt.template_id = t.id and ctt.family_id = v_family_id)
                    and (p_selected_template_ids is null or t.id = any(p_selected_template_ids)))
                or (p_template_id is not null and t.id = p_template_id))
            order by t.created_at, t.id
        loop
            insert into public.tasks(
                family_id, title, description, child_id, created_by, status, category,
                source_category_id, source_template_id, growth_domain, due_date, due_time,
                reward_points, penalty_points, recurrence_series_id, recurrence_type,
                recurrence_weekdays, recurrence_end_date
            ) values (
                v_family_id, v_template.title, '', p_child_id, auth.uid(), 'pending', v_category_name,
                p_category_id, v_template.id, coalesce(v_template.growth_domain, 'other'), v_date, null,
                v_template.reward_points, v_template.penalty_points, v_series_id, p_recurrence_type,
                coalesce(p_recurrence_weekdays, '{}'::integer[]),
                case when p_recurrence_type = 'none' then null else p_end_date end
            )
            on conflict (child_id, due_date, source_template_id) where deleted_at is null and source_template_id is not null
            do update set category = excluded.category, source_category_id = excluded.source_category_id
            returning * into v_task;
            return next v_task;
        end loop;
    end loop;
end;
$$;

-- 旧版七参数调用仍按任务包全选处理。新客户端必须传第八参数。
create or replace function public.create_tasks_from_selection(
    p_child_id uuid, p_category_id uuid, p_template_id uuid,
    p_start_date date, p_end_date date, p_recurrence_type text,
    p_recurrence_weekdays integer[] default '{}'
)
returns setof public.tasks language sql security definer set search_path = public as $$
    select * from public.create_tasks_from_selection(
        p_child_id, p_category_id, p_template_id, p_start_date, p_end_date,
        p_recurrence_type, p_recurrence_weekdays, null::uuid[]
    );
$$;

revoke all on function public.create_tasks_from_selection(uuid, uuid, uuid, date, date, text, integer[], uuid[]) from public, anon;
grant execute on function public.create_tasks_from_selection(uuid, uuid, uuid, date, date, text, integer[], uuid[]) to authenticated;
revoke all on function public.create_tasks_from_selection(uuid, uuid, uuid, date, date, text, integer[]) from public, anon;
grant execute on function public.create_tasks_from_selection(uuid, uuid, uuid, date, date, text, integer[]) to authenticated;

commit;
