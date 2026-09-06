-- 分类任务包新版：任务模板可属于多个分类；分类或单个任务均可作为排程来源。
-- 本版本不保留旧任务域数据：会清空孩子积分、积分流水、任务、任务模板和分类。
-- 家庭、账号、奖励及其他业务数据不受影响。

begin;

-- 旧任务历史触发器只允许受控删除；本迁移已获得用户授权清空任务域。
select set_config('app.task_history_change_authorized', 'true', true);
delete from public.point_records where child_id in (select uid from public.users where role = 'child');
delete from public.tasks;
update public.users set total_points = 0 where role = 'child';
delete from public.task_templates;
delete from public.categories;

alter table public.tasks drop constraint if exists tasks_category_check;
alter table public.tasks
    add column if not exists source_category_id uuid references public.categories(id) on delete set null,
    add column if not exists source_template_id uuid references public.task_templates(id) on delete set null;

alter table public.task_templates drop column if exists category;

create table if not exists public.category_task_templates (
    category_id uuid not null references public.categories(id) on delete cascade,
    template_id uuid not null references public.task_templates(id) on delete cascade,
    family_id uuid not null references public.families(id) on delete cascade,
    sort_order integer not null default 0 check (sort_order >= 0),
    created_at timestamptz not null default now(),
    primary key (category_id, template_id)
);
create index if not exists idx_category_task_templates_family
    on public.category_task_templates(family_id, category_id, sort_order);
create unique index if not exists idx_tasks_active_child_date_template
    on public.tasks(child_id, due_date, source_template_id)
    where deleted_at is null and source_template_id is not null;
create index if not exists idx_tasks_source_category_pending
    on public.tasks(source_category_id, due_date)
    where deleted_at is null and status = 'pending';

alter table public.category_task_templates enable row level security;
drop policy if exists "category_task_templates_family_access" on public.category_task_templates;
create policy "category_task_templates_family_access" on public.category_task_templates
    for all using (family_id in (select family_id from public.users where uid = auth.uid()))
    with check (family_id in (select family_id from public.users where uid = auth.uid()));

-- 配置分类成员：由数据库检查家长身份、家庭归属，并一次性替换全部关联及排序。
create or replace function public.set_category_task_templates(
    p_category_id uuid,
    p_template_ids uuid[]
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_family_id uuid;
    v_template_count integer;
begin
    select family_id into v_family_id from users where uid = auth.uid() and role = 'parent';
    if v_family_id is null then raise exception 'Only parents can configure category tasks'; end if;
    if not exists (select 1 from categories where id = p_category_id and family_id = v_family_id) then
        raise exception 'Category is outside current family';
    end if;
    select count(*) into v_template_count
      from task_templates
     where family_id = v_family_id
       and id = any(coalesce(p_template_ids, '{}'::uuid[]));
    if v_template_count <> cardinality(coalesce(p_template_ids, '{}'::uuid[])) then
        raise exception 'Task template is outside current family';
    end if;

    delete from category_task_templates where category_id = p_category_id;
    insert into category_task_templates(category_id, template_id, family_id, sort_order)
    select p_category_id, template_id, v_family_id, ordinality - 1
      from unnest(coalesce(p_template_ids, '{}'::uuid[])) with ordinality as item(template_id, ordinality);
end;
$$;

-- 分类名称是未完成日程的展示分组；改名时只更新今天及未来的待完成任务。
create or replace function public.rename_category(
    p_category_id uuid,
    p_name text,
    p_color text
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_family_id uuid;
begin
    select family_id into v_family_id from users where uid = auth.uid() and role = 'parent';
    if v_family_id is null then raise exception 'Only parents can rename categories'; end if;
    if p_name is null or btrim(p_name) = '' then raise exception 'Category name is required'; end if;
    if not exists (select 1 from categories where id = p_category_id and family_id = v_family_id) then
        raise exception 'Category is outside current family';
    end if;

    update categories set name = btrim(p_name), color = coalesce(nullif(p_color, ''), color)
     where id = p_category_id;
    update tasks
       set category = btrim(p_name)
     where source_category_id = p_category_id
       and status = 'pending'
       and deleted_at is null
       and due_date >= (now() at time zone 'Asia/Shanghai')::date;
end;
$$;

-- 从分类任务包或单个任务模板创建日程。两种来源必须二选一。
-- 同一孩子、同一日期、同一任务模板只保留一条任务；后创建来源覆盖其展示分类。
create or replace function public.create_tasks_from_selection(
    p_child_id uuid,
    p_category_id uuid,
    p_template_id uuid,
    p_start_date date,
    p_end_date date,
    p_recurrence_type text,
    p_recurrence_weekdays integer[] default '{}'
)
returns setof public.tasks
language plpgsql
security definer
set search_path = public
as $$
declare
    v_family_id uuid;
    v_category_name text := '其他';
    v_template record;
    v_task public.tasks%rowtype;
    v_date date;
    v_series_id uuid;
    v_template_count integer;
    v_date_count integer;
begin
    if (p_category_id is null) = (p_template_id is null) then
        raise exception 'Choose exactly one category or task template';
    end if;
    if p_start_date is null or p_end_date is null or p_end_date < p_start_date then
        raise exception 'Invalid task date range';
    end if;
    if p_recurrence_type not in ('none', 'daily', 'weekdays', 'weekly') then
        raise exception 'Invalid recurrence type';
    end if;
    if p_recurrence_type = 'weekly' and cardinality(coalesce(p_recurrence_weekdays, '{}'::integer[])) = 0 then
        raise exception 'Weekly recurrence requires weekdays';
    end if;

    select family_id into v_family_id from users where uid = auth.uid() and role = 'parent';
    if v_family_id is null then raise exception 'Only parents can create tasks'; end if;
    if not exists (select 1 from users where uid = p_child_id and family_id = v_family_id and role = 'child') then
        raise exception 'Child is outside current family';
    end if;

    if p_category_id is not null then
        select name into v_category_name from categories where id = p_category_id and family_id = v_family_id;
        if v_category_name is null then raise exception 'Category is outside current family'; end if;
        select count(*) into v_template_count from category_task_templates where category_id = p_category_id;
    else
        if not exists (select 1 from task_templates where id = p_template_id and family_id = v_family_id) then
            raise exception 'Task template is outside current family';
        end if;
        v_template_count := 1;
    end if;
    if v_template_count = 0 then raise exception 'Category has no tasks'; end if;

    select count(*) into v_date_count
      from generate_series(p_start_date, p_end_date, interval '1 day') as day_value
     where p_recurrence_type = 'none'
        or p_recurrence_type = 'daily'
        or (p_recurrence_type = 'weekdays' and extract(isodow from day_value) between 1 and 5)
        or (p_recurrence_type = 'weekly' and extract(isodow from day_value)::integer = any(p_recurrence_weekdays));
    if v_date_count * v_template_count > 500 then
        raise exception 'At most 500 tasks can be created at once';
    end if;
    v_series_id := case when p_recurrence_type = 'none' then null else gen_random_uuid() end;

    for v_date in
        select day_value::date from generate_series(p_start_date, p_end_date, interval '1 day') as day_value
         where p_recurrence_type = 'none'
            or p_recurrence_type = 'daily'
            or (p_recurrence_type = 'weekdays' and extract(isodow from day_value) between 1 and 5)
            or (p_recurrence_type = 'weekly' and extract(isodow from day_value)::integer = any(p_recurrence_weekdays))
    loop
        for v_template in
            select t.*
              from task_templates t
             where (p_category_id is not null and exists (
                        select 1 from category_task_templates ctt
                         where ctt.category_id = p_category_id and ctt.template_id = t.id
                    ))
                or (p_template_id is not null and t.id = p_template_id)
             order by t.created_at, t.id
        loop
            insert into tasks(
                family_id, title, description, child_id, created_by, status, category,
                source_category_id, source_template_id, due_date, due_time,
                reward_points, penalty_points, recurrence_series_id, recurrence_type,
                recurrence_weekdays, recurrence_end_date
            ) values (
                v_family_id, v_template.title, v_template.description, p_child_id, auth.uid(), 'pending', v_category_name,
                p_category_id, v_template.id, v_date, null,
                v_template.reward_points, v_template.penalty_points, v_series_id, p_recurrence_type,
                coalesce(p_recurrence_weekdays, '{}'::integer[]),
                case when p_recurrence_type = 'none' then null else p_end_date end
            )
            on conflict (child_id, due_date, source_template_id) where deleted_at is null and source_template_id is not null
            do update set
                category = excluded.category,
                source_category_id = excluded.source_category_id
            returning * into v_task;
            return next v_task;
        end loop;
    end loop;
end;
$$;

revoke all on function public.set_category_task_templates(uuid, uuid[]) from public, anon;
revoke all on function public.rename_category(uuid, text, text) from public, anon;
revoke all on function public.create_tasks_from_selection(uuid, uuid, uuid, date, date, text, integer[]) from public, anon;
grant execute on function public.set_category_task_templates(uuid, uuid[]) to authenticated;
grant execute on function public.rename_category(uuid, text, text) to authenticated;
grant execute on function public.create_tasks_from_selection(uuid, uuid, uuid, date, date, text, integer[]) to authenticated;

commit;
