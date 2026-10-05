-- 家长端任务成长领域：先部署此迁移，再发布家长端。
begin;

alter table public.task_templates add column if not exists growth_domain text;
-- 先加无默认列，旧行自然保持 NULL，再给以后写入的旧客户端设置默认值。
alter table public.tasks add column if not exists growth_domain text;
alter table public.tasks alter column growth_domain set default 'other';

alter table public.task_templates drop constraint if exists task_templates_growth_domain_check;
alter table public.task_templates add constraint task_templates_growth_domain_check
    check (growth_domain is null or growth_domain in
        ('reading','calculation','dictation','english','writing','math_thinking','life','other'));
alter table public.tasks drop constraint if exists tasks_growth_domain_check;
alter table public.tasks add constraint tasks_growth_domain_check
    check (growth_domain is null or growth_domain in
        ('reading','calculation','dictation','english','writing','math_thinking','life','other'));

-- 原有家庭 RLS 允许孩子更新家庭任务。领域写入必须额外校验角色；已完成任务只走受控 RPC。
create or replace function public.guard_task_growth_domain()
returns trigger language plpgsql set search_path = public as $$
declare v_is_parent boolean;
begin
    if tg_op = 'UPDATE' then
        if current_user not in ('postgres', 'service_role')
            and old.status in ('done','verified') and new.status = 'pending' then
            raise exception 'Completed task status must use the approved operation';
        end if;
        if new.growth_domain is not distinct from old.growth_domain then return new; end if;
        if current_user not in ('postgres', 'service_role')
            and (old.status <> 'pending' or new.status <> 'pending') then
            raise exception 'Completed task growth domain requires correction RPC';
        end if;
    end if;
    if current_user in ('postgres', 'service_role') then return new; end if;
    select exists(select 1 from public.users
        where uid = auth.uid() and family_id = new.family_id and role = 'parent') into v_is_parent;
    if not v_is_parent then raise exception 'Only a parent may set a task growth domain'; end if;
    return new;
end;
$$;
drop trigger if exists tasks_guard_growth_domain on public.tasks;
create trigger tasks_guard_growth_domain before insert or update of growth_domain, status on public.tasks
    for each row execute function public.guard_task_growth_domain();

create or replace function public.guard_template_growth_domain()
returns trigger language plpgsql set search_path = public as $$
begin
    if tg_op = 'UPDATE' then
        if new.growth_domain is not distinct from old.growth_domain then return new; end if;
    end if;
    if current_user in ('postgres', 'service_role') then return new; end if;
    if not exists(select 1 from public.users
        where uid = auth.uid() and family_id = new.family_id and role = 'parent') then
        raise exception 'Only a parent may set a template growth domain';
    end if;
    return new;
end;
$$;
drop trigger if exists templates_guard_growth_domain on public.task_templates;
create trigger templates_guard_growth_domain before insert or update of growth_domain on public.task_templates
    for each row execute function public.guard_template_growth_domain();

create table if not exists public.task_growth_domain_audit (
    id uuid primary key default gen_random_uuid(),
    task_id uuid not null references public.tasks(id) on delete cascade,
    family_id uuid not null references public.families(id),
    old_domain text,
    new_domain text not null,
    operation text not null check (operation in ('backfill','correction')),
    changed_by uuid not null references public.users(uid),
    changed_at timestamptz not null default now()
);
create index if not exists idx_task_growth_domain_audit_task on public.task_growth_domain_audit(task_id, changed_at desc);
alter table public.task_growth_domain_audit enable row level security;
drop policy if exists task_growth_domain_audit_parent_read on public.task_growth_domain_audit;
create policy task_growth_domain_audit_parent_read on public.task_growth_domain_audit for select to authenticated
    using (exists(select 1 from public.users where uid = auth.uid()
        and family_id = task_growth_domain_audit.family_id and role = 'parent'));
revoke all on public.task_growth_domain_audit from anon, authenticated;
grant select on public.task_growth_domain_audit to authenticated;

create or replace function public.preview_task_growth_domain_backfill(p_template_id uuid)
returns table(total_count integer, completed_count integer)
language plpgsql security definer set search_path = public as $$
declare v_family_id uuid;
begin
    select family_id into v_family_id from public.users where uid = auth.uid() and role = 'parent';
    if v_family_id is null or not exists(select 1 from public.task_templates
        where id = p_template_id and family_id = v_family_id) then
        raise exception 'Template is outside current parent family';
    end if;
    return query select count(*)::integer,
        count(*) filter (where status in ('done','verified'))::integer
    from public.tasks where family_id = v_family_id and source_template_id = p_template_id
        and growth_domain is null and deleted_at is null;
end;
$$;

create or replace function public.backfill_task_growth_domain(p_template_id uuid, p_growth_domain text)
returns integer language plpgsql security definer set search_path = public as $$
declare v_family_id uuid; v_count integer;
begin
    select family_id into v_family_id from public.users where uid = auth.uid() and role = 'parent';
    if v_family_id is null or not exists(select 1 from public.task_templates
        where id = p_template_id and family_id = v_family_id and growth_domain = p_growth_domain) then
        raise exception 'Template or domain is outside current parent family';
    end if;
    if p_growth_domain not in ('reading','calculation','dictation','english','writing','math_thinking','life','other') then
        raise exception 'Invalid growth domain';
    end if;
    with changed as (
        update public.tasks set growth_domain = p_growth_domain
        where family_id = v_family_id and source_template_id = p_template_id
            and growth_domain is null and deleted_at is null
        returning id, family_id
    )
    insert into public.task_growth_domain_audit(task_id, family_id, old_domain, new_domain, operation, changed_by)
        select id, family_id, null, p_growth_domain, 'backfill', auth.uid() from changed;
    get diagnostics v_count = row_count;
    return v_count;
end;
$$;

create or replace function public.correct_task_growth_domain(p_task_id uuid, p_growth_domain text)
returns void language plpgsql security definer set search_path = public as $$
declare v_family_id uuid; v_task public.tasks%rowtype;
begin
    select family_id into v_family_id from public.users where uid = auth.uid() and role = 'parent';
    if v_family_id is null then raise exception 'Only parents may correct growth domains'; end if;
    if p_growth_domain not in ('reading','calculation','dictation','english','writing','math_thinking','life','other') then
        raise exception 'Invalid growth domain';
    end if;
    select * into v_task from public.tasks where id = p_task_id and family_id = v_family_id
        and deleted_at is null for update;
    if not found then raise exception 'Task is outside current parent family'; end if;
    if v_task.growth_domain is not distinct from p_growth_domain then return; end if;
    update public.tasks set growth_domain = p_growth_domain where id = p_task_id;
    insert into public.task_growth_domain_audit(task_id, family_id, old_domain, new_domain, operation, changed_by)
        values(p_task_id, v_family_id, v_task.growth_domain, p_growth_domain, 'correction', auth.uid());
end;
$$;

revoke all on function public.preview_task_growth_domain_backfill(uuid) from public, anon;
revoke all on function public.backfill_task_growth_domain(uuid, text) from public, anon;
revoke all on function public.correct_task_growth_domain(uuid, text) from public, anon;
grant execute on function public.preview_task_growth_domain_backfill(uuid) to authenticated;
grant execute on function public.backfill_task_growth_domain(uuid, text) to authenticated;
grant execute on function public.correct_task_growth_domain(uuid, text) to authenticated;

-- 后续由下方同签名函数替换旧排程实现，保留原有去重及家庭校验。
create or replace function public.create_tasks_from_selection(
    p_child_id uuid, p_category_id uuid, p_template_id uuid,
    p_start_date date, p_end_date date, p_recurrence_type text,
    p_recurrence_weekdays integer[] default '{}'
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
        select count(*) into v_template_count from public.category_task_templates
            where category_id = p_category_id and family_id = v_family_id;
    else
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
                    where ctt.category_id = p_category_id and ctt.template_id = t.id and ctt.family_id = v_family_id))
                or (p_template_id is not null and t.id = p_template_id))
            order by t.created_at, t.id
        loop
            insert into public.tasks(
                family_id, title, description, child_id, created_by, status, category,
                source_category_id, source_template_id, growth_domain, due_date, due_time,
                reward_points, penalty_points, recurrence_series_id, recurrence_type,
                recurrence_weekdays, recurrence_end_date
            ) values (
                v_family_id, v_template.title, v_template.description, p_child_id, auth.uid(), 'pending', v_category_name,
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

commit;
