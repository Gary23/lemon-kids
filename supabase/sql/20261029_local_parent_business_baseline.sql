-- 仅限 lemon-kids-local 的家长端增量业务基线。
-- 从已审查历史定义整合；不执行历史清表、清积分或默认演示数据插入。
-- 必须由应用脚本显式设置 app.lemon_local_business_target=lemon-kids-local。
begin;
set local lock_timeout = '5s';
set local statement_timeout = '120s';
do $$ begin
 if current_setting('app.lemon_local_business_target', true) is distinct from 'lemon-kids-local'
 or not exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.proname='create_local_parent_profile') then
   raise exception 'Local parent baseline and explicit local target confirmation required';
 end if;
end $$;


CREATE TABLE IF NOT EXISTS public.categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    color TEXT DEFAULT '#4CAF50',
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(family_id, name)
);

CREATE TABLE IF NOT EXISTS public.task_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    reward_points INT NOT NULL DEFAULT 5 CHECK (reward_points > 0),
    penalty_points INT NOT NULL DEFAULT 2,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.category_task_templates (
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    template_id UUID NOT NULL REFERENCES task_templates(id) ON DELETE CASCADE,
    family_id UUID NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    sort_order INT NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (category_id, template_id)
);

CREATE TABLE IF NOT EXISTS public.rewards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES families(id),
    title TEXT NOT NULL,
    cost INT NOT NULL,
    repeatable BOOLEAN DEFAULT true,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.point_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES families(id),
    child_id UUID NOT NULL REFERENCES users(uid),
    amount INT NOT NULL,
    balance INT NOT NULL,
    reason TEXT NOT NULL,
    type TEXT NOT NULL CHECK (type IN ('task_complete', 'task_expired', 'reward_redeem', 'manual')),
    related_task_id UUID REFERENCES tasks(id),
    related_reward_id UUID REFERENCES rewards(id),
    timestamp TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.app_usage (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES families(id),
    child_id UUID NOT NULL REFERENCES users(uid),
    package_name TEXT NOT NULL,
    app_name TEXT NOT NULL,
    duration_seconds BIGINT DEFAULT 0,
    date DATE NOT NULL,
    collected_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.app_limits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES families(id),
    child_id UUID NOT NULL REFERENCES users(uid),
    package_name TEXT NOT NULL,
    app_name TEXT NOT NULL,
    daily_limit_minutes INT DEFAULT 30,
    is_active BOOLEAN DEFAULT true,
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.device_status_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES families(id),
    child_id UUID NOT NULL REFERENCES users(uid),
    event_type TEXT NOT NULL DEFAULT 'heartbeat',
    accessibility_enabled BOOLEAN DEFAULT false,
    limit_service_running BOOLEAN DEFAULT false,
    app_process_alive BOOLEAN DEFAULT true,
    battery_ignoring_optimizations BOOLEAN DEFAULT false,
    message TEXT DEFAULT '',
    created_at TIMESTAMPTZ DEFAULT now()
);


alter table public.tasks add column if not exists end_date date;
alter table public.tasks add column if not exists timer_enabled boolean not null default false;
alter table public.task_templates add column if not exists timer_enabled boolean not null default false;


-- 电子日历核心：重复任务系列与家长驳回的事务性积分回滚。
-- 重复系列会在客户端创建为多个独立日任务，以保留每一天的完成历史。

ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS recurrence_series_id UUID,
    ADD COLUMN IF NOT EXISTS recurrence_type TEXT NOT NULL DEFAULT 'none'
        CHECK (recurrence_type IN ('none', 'daily', 'weekdays', 'weekly')),
    ADD COLUMN IF NOT EXISTS recurrence_weekdays INTEGER[] NOT NULL DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS recurrence_end_date DATE;

CREATE INDEX IF NOT EXISTS idx_tasks_recurrence_series
    ON tasks(recurrence_series_id, due_date);

ALTER TABLE point_records DROP CONSTRAINT IF EXISTS point_records_type_check;
ALTER TABLE point_records ADD CONSTRAINT point_records_type_check
    CHECK (type IN ('task_complete', 'task_expired', 'task_rejected', 'reward_redeem', 'manual'));




do $$ begin
 if not exists(select 1 from pg_constraint where conrelid='public.tasks'::regclass and conname='local_tasks_source_template_fk') then
  alter table public.tasks add constraint local_tasks_source_template_fk foreign key(source_template_id) references public.task_templates(id) on delete set null;
 end if;
 if not exists(select 1 from pg_constraint where conrelid='public.tasks'::regclass and conname='local_tasks_source_category_fk') then
  alter table public.tasks add constraint local_tasks_source_category_fk foreign key(source_category_id) references public.categories(id) on delete set null;
 end if;
end $$;
create unique index if not exists idx_tasks_active_child_date_template on public.tasks(child_id,due_date,source_template_id) where deleted_at is null and source_template_id is not null;
create index if not exists idx_category_task_templates_family on public.category_task_templates(family_id,category_id,sort_order);
create index if not exists idx_task_templates_family on public.task_templates(family_id);
create index if not exists idx_device_status_child_time on public.device_status_logs(child_id,created_at desc);
create index if not exists idx_appusage_child_date on public.app_usage(child_id,date);


alter table public.categories enable row level security;
revoke all on public.categories from public,anon,authenticated;
grant select,insert,update,delete on public.categories to authenticated;
drop policy if exists local_categories_read on public.categories;
create policy local_categories_read on public.categories for select to authenticated using(family_id=public.current_family_id());
drop policy if exists local_categories_parent on public.categories;
create policy local_categories_parent on public.categories for all to authenticated using(public.is_parent_in_family(family_id)) with check(public.is_parent_in_family(family_id));


alter table public.task_templates enable row level security;
revoke all on public.task_templates from public,anon,authenticated;
grant select,insert,update,delete on public.task_templates to authenticated;
drop policy if exists local_task_templates_read on public.task_templates;
create policy local_task_templates_read on public.task_templates for select to authenticated using(family_id=public.current_family_id());
drop policy if exists local_task_templates_parent on public.task_templates;
create policy local_task_templates_parent on public.task_templates for all to authenticated using(public.is_parent_in_family(family_id)) with check(public.is_parent_in_family(family_id));


alter table public.category_task_templates enable row level security;
revoke all on public.category_task_templates from public,anon,authenticated;
grant select on public.category_task_templates to authenticated;
drop policy if exists local_category_task_templates_read on public.category_task_templates;
create policy local_category_task_templates_read on public.category_task_templates for select to authenticated using(family_id=public.current_family_id());


alter table public.device_status_logs enable row level security;
revoke all on public.device_status_logs from public,anon,authenticated;
grant select on public.device_status_logs to authenticated;
drop policy if exists local_device_status_logs_read on public.device_status_logs;
create policy local_device_status_logs_read on public.device_status_logs for select to authenticated using(
 family_id=public.current_family_id() and (public.is_parent_in_family(family_id) or child_id=auth.uid()));


alter table public.app_usage enable row level security;
revoke all on public.app_usage from public,anon,authenticated;
grant select on public.app_usage to authenticated;
drop policy if exists local_app_usage_read on public.app_usage;
create policy local_app_usage_read on public.app_usage for select to authenticated using(
 family_id=public.current_family_id() and (public.is_parent_in_family(family_id) or child_id=auth.uid()));


alter table public.app_limits enable row level security;
revoke all on public.app_limits from public,anon,authenticated;
grant select on public.app_limits to authenticated;
drop policy if exists local_app_limits_read on public.app_limits;
create policy local_app_limits_read on public.app_limits for select to authenticated using(
 family_id=public.current_family_id() and (public.is_parent_in_family(family_id) or child_id=auth.uid()));


grant insert on public.device_status_logs to authenticated;
drop policy if exists local_device_status_logs_report on public.device_status_logs;
create policy local_device_status_logs_report on public.device_status_logs for insert to authenticated with check(
 child_id=auth.uid() and family_id=public.current_family_id() and exists(select 1 from public.users where uid=auth.uid() and role='child'));


grant insert on public.app_usage to authenticated;
drop policy if exists local_app_usage_report on public.app_usage;
create policy local_app_usage_report on public.app_usage for insert to authenticated with check(
 child_id=auth.uid() and family_id=public.current_family_id() and exists(select 1 from public.users where uid=auth.uid() and role='child'));


grant insert,update,delete on public.app_limits to authenticated;
drop policy if exists local_app_limits_parent on public.app_limits;
create policy local_app_limits_parent on public.app_limits for all to authenticated
using(public.is_parent_in_family(family_id)) with check(public.is_parent_in_family(family_id) and exists(select 1 from public.users u where u.uid=child_id and u.family_id=app_limits.family_id and u.role='child'));
revoke all on public.rewards,public.point_records from public,anon,authenticated;
grant select,insert,update on public.rewards to authenticated;
grant select on public.point_records to authenticated;
alter table public.point_records enable row level security;


create or replace function public.set_category_task_templates(
    p_category_id uuid,
    p_template_ids uuid[]
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
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
set search_path = public, pg_temp
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



revoke all on function public.set_category_task_templates(uuid,uuid[]) from public,anon;
grant execute on function public.set_category_task_templates(uuid,uuid[]) to authenticated;
revoke all on function public.rename_category(uuid,text,text) from public,anon;
grant execute on function public.rename_category(uuid,text,text) to authenticated;


-- 来源：20260904_task_history_and_cancellation.sql
-- 任务实例即历史记录：禁止删除过去或已完成的任务；“删除”只取消今天及未来的待完成任务。
-- 本脚本使用 Asia/Shanghai 作为家庭业务日期。如未来支持家庭时区，应改为读取 families.time_zone。

alter table public.tasks
    add column if not exists deleted_at timestamptz;

create index if not exists idx_tasks_active_child_date
    on public.tasks(child_id, due_date)
    where deleted_at is null;

create index if not exists idx_tasks_recycle_bin
    on public.tasks(family_id, deleted_at)
    where deleted_at is not null;

-- 即使旧版本客户端仍持有 tasks 的通用更新权限，也不能绕过受控 RPC
-- 修改 deleted_at 或物理删除任务。其它字段的编辑权限保持现有策略不变。
create or replace function public.guard_task_history()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
    if tg_op = 'UPDATE' and old.deleted_at is not distinct from new.deleted_at then
        return new;
    end if;

    if current_setting('app.task_history_change_authorized', true) is distinct from 'true' then
        raise exception 'Task cancellation and deletion must use the approved operation';
    end if;

    if tg_op = 'DELETE' then
        return old;
    end if;
    return new;
end;
$$;

drop trigger if exists tasks_guard_history on public.tasks;
create trigger tasks_guard_history
before update or delete on public.tasks
for each row execute function public.guard_task_history();

-- 孩子完成任务：只允许本人完成仍有效的待完成任务。
create or replace function public.complete_task(p_task_id uuid, p_child_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_task tasks%rowtype;
    v_user users%rowtype;
    v_new_balance integer;
begin
    if auth.uid() is distinct from p_child_id
       or not exists (select 1 from users where uid = auth.uid() and role = 'child') then
        raise exception 'Only the task owner can complete a task';
    end if;

    select * into v_task
      from tasks
     where id = p_task_id and deleted_at is null
     for update;
    if not found or v_task.child_id <> p_child_id or v_task.status <> 'pending' then
        raise exception 'Task is not available for completion';
    end if;

    select * into v_user from users where uid = p_child_id for update;
    v_new_balance := v_user.total_points + v_task.reward_points;

    update tasks
       set status = 'verified', completed_at = now(), verified_at = now()
     where id = p_task_id;
    update users set total_points = v_new_balance where uid = p_child_id;
    insert into point_records (family_id, child_id, amount, balance, reason, type, related_task_id)
    values (v_task.family_id, p_child_id, v_task.reward_points, v_new_balance,
            '完成任务：' || v_task.title, 'task_complete', p_task_id);
end;
$$;

-- 孩子撤销完成：任务、积分与积分流水必须在同一事务中回退。
create or replace function public.undo_task_completion(p_task_id uuid, p_child_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_task tasks%rowtype;
    v_record point_records%rowtype;
    v_balance integer;
begin
    if auth.uid() is distinct from p_child_id
       or not exists (select 1 from users where uid = auth.uid() and role = 'child') then
        raise exception 'Only the task owner can undo completion';
    end if;

    select * into v_task from tasks where id = p_task_id and deleted_at is null for update;
    if not found or v_task.child_id <> p_child_id or v_task.status not in ('done', 'verified') then
        raise exception 'Task completion cannot be undone';
    end if;

    select * into v_record
      from point_records
     where related_task_id = p_task_id and child_id = p_child_id and type = 'task_complete'
     order by timestamp desc
     limit 1
     for update;
    if not found then
        raise exception 'Task completion record not found';
    end if;

    select total_points into v_balance from users where uid = p_child_id for update;
    v_balance := greatest(v_balance - v_record.amount, 0);

    update tasks
       set status = 'pending', completed_at = null, verified_at = null
     where id = p_task_id;
    update users set total_points = v_balance where uid = p_child_id;
    delete from point_records
     where related_task_id = p_task_id and child_id = p_child_id and type = 'task_complete';
end;
$$;

-- 家长删除任务实际为取消：仅今天及未来、尚未完成且未取消的任务可以取消。
create or replace function public.cancel_task(p_task_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_task tasks%rowtype;
    v_today date := (now() at time zone 'Asia/Shanghai')::date;
begin
    if not exists (select 1 from users where uid = auth.uid() and role = 'parent') then
        raise exception 'Only parents can cancel tasks';
    end if;

    select * into v_task from tasks where id = p_task_id for update;
    if not found
       or not exists (select 1 from users where uid = auth.uid() and family_id = v_task.family_id) then
        raise exception 'Task is outside current family';
    end if;
    if v_task.deleted_at is not null
       or v_task.status <> 'pending'
       or v_task.due_date < v_today then
        raise exception 'Only pending tasks scheduled for today or later can be cancelled';
    end if;

    perform set_config('app.task_history_change_authorized', 'true', true);
    update tasks set deleted_at = now() where id = p_task_id;
end;
$$;

-- 还原也只能作用于尚未发生、没有完成记录的已取消任务。
create or replace function public.restore_cancelled_task(p_task_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_task tasks%rowtype;
    v_today date := (now() at time zone 'Asia/Shanghai')::date;
begin
    if not exists (select 1 from users where uid = auth.uid() and role = 'parent') then
        raise exception 'Only parents can restore tasks';
    end if;

    select * into v_task from tasks where id = p_task_id for update;
    if not found
       or not exists (select 1 from users where uid = auth.uid() and family_id = v_task.family_id) then
        raise exception 'Task is outside current family';
    end if;
    if v_task.deleted_at is null
       or v_task.status <> 'pending'
       or v_task.due_date < v_today
       or v_task.completed_at is not null
       or v_task.verified_at is not null then
        raise exception 'Only recyclable future pending tasks can be restored';
    end if;

    perform set_config('app.task_history_change_authorized', 'true', true);
    update tasks set deleted_at = null where id = p_task_id;
end;
$$;

-- 物理删除严格限制在没有任务执行历史或积分历史的已取消任务。
create or replace function public.permanently_delete_recyclable_task(p_task_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_task tasks%rowtype;
    v_today date := (now() at time zone 'Asia/Shanghai')::date;
begin
    if not exists (select 1 from users where uid = auth.uid() and role = 'parent') then
        raise exception 'Only parents can permanently delete tasks';
    end if;

    select * into v_task from tasks where id = p_task_id for update;
    if not found
       or not exists (select 1 from users where uid = auth.uid() and family_id = v_task.family_id) then
        raise exception 'Task is outside current family';
    end if;
    if v_task.deleted_at is null
       or v_task.status <> 'pending'
       or v_task.due_date < v_today
       or v_task.completed_at is not null
       or v_task.verified_at is not null
       or exists (select 1 from point_records where related_task_id = p_task_id) then
        raise exception 'Task has history and must be retained';
    end if;

    perform set_config('app.task_history_change_authorized', 'true', true);
    delete from tasks where id = p_task_id;
end;
$$;

-- 回收站“清空”只清理符合上述条件的项目，历史任务仍保留在回收站。
create or replace function public.permanently_delete_recyclable_tasks(p_family_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_today date := (now() at time zone 'Asia/Shanghai')::date;
begin
    if not exists (
        select 1 from users where uid = auth.uid() and role = 'parent' and family_id = p_family_id
    ) then
        raise exception 'Only parents in this family can clear the recycle bin';
    end if;

    perform set_config('app.task_history_change_authorized', 'true', true);
    delete from tasks t
     where t.family_id = p_family_id
       and t.deleted_at is not null
       and t.status = 'pending'
       and t.due_date >= v_today
       and t.completed_at is null
       and t.verified_at is null
       and not exists (select 1 from point_records p where p.related_task_id = t.id);
end;
$$;

revoke all on function public.complete_task(uuid, uuid) from public, anon;
revoke all on function public.undo_task_completion(uuid, uuid) from public, anon;
revoke all on function public.cancel_task(uuid) from public, anon;
revoke all on function public.restore_cancelled_task(uuid) from public, anon;
revoke all on function public.permanently_delete_recyclable_task(uuid) from public, anon;
revoke all on function public.permanently_delete_recyclable_tasks(uuid) from public, anon;
grant execute on function public.complete_task(uuid, uuid) to authenticated;
grant execute on function public.undo_task_completion(uuid, uuid) to authenticated;
grant execute on function public.cancel_task(uuid) to authenticated;
grant execute on function public.restore_cancelled_task(uuid) to authenticated;
grant execute on function public.permanently_delete_recyclable_task(uuid) to authenticated;
grant execute on function public.permanently_delete_recyclable_tasks(uuid) to authenticated;


-- 来源：20261005_parent_task_growth_domain.sql
-- 家长端任务成长领域：先部署此迁移，再发布家长端。

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
returns trigger language plpgsql set search_path = public, pg_temp as $$
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
returns trigger language plpgsql set search_path = public, pg_temp as $$
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
language plpgsql security definer set search_path = public, pg_temp as $$
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
returns integer language plpgsql security definer set search_path = public, pg_temp as $$
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
returns void language plpgsql security definer set search_path = public, pg_temp as $$
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
returns setof public.tasks language plpgsql security definer set search_path = public, pg_temp as $$
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


-- 来源：20260930_parent_reward_catalog.sql
-- 家长端家庭奖励目录。先于孩子端兑换记录与事务迁移执行。
-- 可重复执行；如旧数据违反新约束，明确报错，避免静默改写奖励。

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.rewards WHERE btrim(title, E' \t\n\r') = '' OR cost <= 0) THEN
        RAISE EXCEPTION 'rewards 存在空标题或非正价格，请先核对旧数据';
    END IF;
END $$;

ALTER TABLE public.rewards
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS cover_key TEXT NOT NULL DEFAULT 'gift',
    ADD COLUMN IF NOT EXISTS is_featured BOOLEAN NOT NULL DEFAULT false;

UPDATE public.rewards SET repeatable = true WHERE repeatable IS NULL;
UPDATE public.rewards SET is_active = true WHERE is_active IS NULL;
UPDATE public.rewards SET cover_key = 'gift' WHERE cover_key IS NULL;
UPDATE public.rewards SET is_featured = false WHERE is_featured IS NULL;

ALTER TABLE public.rewards
    ALTER COLUMN repeatable SET DEFAULT true,
    ALTER COLUMN repeatable SET NOT NULL,
    ALTER COLUMN is_active SET DEFAULT true,
    ALTER COLUMN is_active SET NOT NULL,
    ALTER COLUMN cover_key SET DEFAULT 'gift',
    ALTER COLUMN cover_key SET NOT NULL,
    ALTER COLUMN is_featured SET DEFAULT false,
    ALTER COLUMN is_featured SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.rewards'::regclass AND conname = 'rewards_title_nonblank') THEN
        ALTER TABLE public.rewards ADD CONSTRAINT rewards_title_nonblank CHECK (length(btrim(title, E' \t\n\r')) > 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.rewards'::regclass AND conname = 'rewards_cost_positive') THEN
        ALTER TABLE public.rewards ADD CONSTRAINT rewards_cost_positive CHECK (cost > 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.rewards'::regclass AND conname = 'rewards_cover_key_valid') THEN
        ALTER TABLE public.rewards ADD CONSTRAINT rewards_cover_key_valid
            CHECK (cover_key IN ('gift', 'toy', 'book', 'outing', 'treat', 'wish'));
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS rewards_one_active_featured_per_family
    ON public.rewards (family_id) WHERE is_active AND is_featured;

ALTER TABLE public.rewards ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rewards_select_family ON public.rewards;
DROP POLICY IF EXISTS rewards_insert_parent ON public.rewards;
DROP POLICY IF EXISTS rewards_update_parent ON public.rewards;
DROP POLICY IF EXISTS rewards_delete_parent ON public.rewards;

CREATE POLICY rewards_select_family ON public.rewards FOR SELECT TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.users u
        WHERE u.uid = auth.uid() AND u.family_id = rewards.family_id
          AND (u.role = 'parent' OR rewards.is_active)
    ));

CREATE POLICY rewards_insert_parent ON public.rewards FOR INSERT TO authenticated
    WITH CHECK (EXISTS (
        SELECT 1 FROM public.users u
        WHERE u.uid = auth.uid() AND u.family_id = rewards.family_id AND u.role = 'parent'
    ));

CREATE POLICY rewards_update_parent ON public.rewards FOR UPDATE TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.users u
        WHERE u.uid = auth.uid() AND u.family_id = rewards.family_id AND u.role = 'parent'
    ))
    WITH CHECK (EXISTS (
        SELECT 1 FROM public.users u
        WHERE u.uid = auth.uid() AND u.family_id = rewards.family_id AND u.role = 'parent'
    ));

-- 旧兑换 RPC 会把一次性奖励的 is_active 改为 false；暂停其调用，
-- 孩子端事务迁移将替换此函数并重新授权。
DO $$
BEGIN
    IF to_regprocedure('public.redeem_reward(uuid,uuid)') IS NOT NULL THEN
        REVOKE EXECUTE ON FUNCTION public.redeem_reward(uuid, uuid) FROM PUBLIC, anon, authenticated;
    END IF;
END $$;


-- 来源：20260930_kid_real_rewards.sql
-- 依赖 20260930_parent_reward_catalog.sql。先在测试环境核对，再于线上执行。

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'rewards' AND column_name = 'is_featured') OR
     NOT EXISTS (SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'rewards' AND column_name = 'cover_key') THEN
    RAISE EXCEPTION '请先部署 20260930_parent_reward_catalog.sql';
  END IF;
END $$;

ALTER TABLE public.point_records DROP CONSTRAINT IF EXISTS point_records_type_check;
ALTER TABLE public.point_records ADD CONSTRAINT point_records_type_check
  CHECK (type IN ('task_complete', 'task_expired', 'task_rejected', 'reward_redeem', 'reward_refund', 'manual'));

CREATE TABLE IF NOT EXISTS public.reward_redemptions (
  id uuid PRIMARY KEY,
  family_id uuid NOT NULL REFERENCES public.families(id),
  child_id uuid NOT NULL REFERENCES public.users(uid),
  reward_id uuid NOT NULL REFERENCES public.rewards(id),
  title_snapshot text NOT NULL,
  cost_snapshot integer NOT NULL CHECK (cost_snapshot > 0),
  repeatable_snapshot boolean NOT NULL,
  status text NOT NULL DEFAULT 'held' CHECK (status IN ('held', 'used', 'cancelled')),
  redeemed_at timestamptz NOT NULL DEFAULT now(),
  used_at timestamptz,
  cancelled_at timestamptz,
  redeem_point_record_id uuid UNIQUE REFERENCES public.point_records(id),
  refund_point_record_id uuid UNIQUE REFERENCES public.point_records(id),
  CONSTRAINT reward_redemptions_state_times CHECK (
    (status = 'held' AND used_at IS NULL AND cancelled_at IS NULL) OR
    (status = 'used' AND used_at IS NOT NULL AND cancelled_at IS NULL) OR
    (status = 'cancelled' AND used_at IS NULL AND cancelled_at IS NOT NULL)
  )
);
CREATE INDEX IF NOT EXISTS reward_redemptions_child_time
  ON public.reward_redemptions(child_id, redeemed_at DESC);
CREATE INDEX IF NOT EXISTS reward_redemptions_reward_status
  ON public.reward_redemptions(reward_id, status);
CREATE UNIQUE INDEX IF NOT EXISTS reward_redemptions_one_time_claim
  ON public.reward_redemptions(reward_id)
  WHERE NOT repeatable_snapshot AND status <> 'cancelled';

ALTER TABLE public.point_records ADD COLUMN IF NOT EXISTS related_redemption_id uuid
  REFERENCES public.reward_redemptions(id);
CREATE UNIQUE INDEX IF NOT EXISTS point_records_one_refund_per_redemption
  ON public.point_records(related_redemption_id) WHERE type = 'reward_refund';

-- 旧 users/point_records 的通用 RLS 曾允许本人直接写积分。业务 RPC 以
-- SECURITY DEFINER 运行；客户端的 authenticated 角色不能绕过事务修改余额或奖励流水。
CREATE OR REPLACE FUNCTION public.guard_direct_reward_points()
RETURNS trigger LANGUAGE plpgsql set search_path = public, pg_temp AS $$
BEGIN
  IF current_user IN ('authenticated', 'anon') AND
     NEW.total_points IS DISTINCT FROM OLD.total_points THEN
    RAISE EXCEPTION 'Points can only be changed by an approved operation';
  END IF;
  RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS users_guard_direct_reward_points ON public.users;
CREATE TRIGGER users_guard_direct_reward_points BEFORE UPDATE OF total_points ON public.users
  FOR EACH ROW EXECUTE FUNCTION public.guard_direct_reward_points();

CREATE OR REPLACE FUNCTION public.guard_reward_point_record_writes()
RETURNS trigger LANGUAGE plpgsql set search_path = public, pg_temp AS $$
BEGIN
  IF current_user IN ('authenticated', 'anon') THEN
    IF TG_OP = 'INSERT' THEN
      IF NEW.type IN ('reward_redeem', 'reward_refund') OR NEW.related_redemption_id IS NOT NULL THEN
        RAISE EXCEPTION 'Reward point records can only be changed by an approved operation';
      END IF;
    ELSIF TG_OP = 'DELETE' THEN
      IF OLD.type IN ('reward_redeem', 'reward_refund') OR OLD.related_redemption_id IS NOT NULL THEN
        RAISE EXCEPTION 'Reward point records can only be changed by an approved operation';
      END IF;
    ELSE
      IF OLD.type IN ('reward_redeem', 'reward_refund') OR NEW.type IN ('reward_redeem', 'reward_refund') OR
         OLD.related_redemption_id IS NOT NULL OR NEW.related_redemption_id IS NOT NULL THEN
        RAISE EXCEPTION 'Reward point records can only be changed by an approved operation';
      END IF;
    END IF;
  END IF;
  IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
  RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS point_records_guard_reward_writes ON public.point_records;
CREATE TRIGGER point_records_guard_reward_writes
  BEFORE INSERT OR UPDATE OR DELETE ON public.point_records
  FOR EACH ROW EXECUTE FUNCTION public.guard_reward_point_record_writes();

ALTER TABLE public.reward_redemptions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS reward_redemptions_read_self ON public.reward_redemptions;
CREATE POLICY reward_redemptions_read_self ON public.reward_redemptions
  FOR SELECT TO authenticated USING (child_id = auth.uid());
REVOKE ALL ON public.reward_redemptions FROM PUBLIC, anon, authenticated;
GRANT SELECT ON public.reward_redemptions TO authenticated;

-- 孩子不能通过旧的家庭通用流水策略读取兄弟姐妹的兑换明细。
DROP POLICY IF EXISTS point_records_family_access ON public.point_records;
DROP POLICY IF EXISTS point_records_select_family ON public.point_records;
DROP POLICY IF EXISTS point_records_delete_parent ON public.point_records;
CREATE POLICY point_records_select_family ON public.point_records FOR SELECT TO authenticated
  USING (EXISTS (
    SELECT 1 FROM public.users u WHERE u.uid = auth.uid()
      AND u.family_id = point_records.family_id
      AND (u.role = 'parent' OR point_records.child_id = u.uid)
  ));
CREATE POLICY point_records_delete_parent ON public.point_records FOR DELETE TO authenticated
  USING (EXISTS (
    SELECT 1 FROM public.users u WHERE u.uid = auth.uid()
      AND u.family_id = point_records.family_id AND u.role = 'parent'
  ));

-- 奖励类型已有兑换后不可修改，包含旧版积分流水；状态仍由家长管理。
CREATE OR REPLACE FUNCTION public.guard_reward_repeatability()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER set search_path = public, pg_temp AS $$
BEGIN
  IF NEW.repeatable IS DISTINCT FROM OLD.repeatable AND
    (EXISTS (SELECT 1 FROM public.reward_redemptions WHERE reward_id = OLD.id) OR
     EXISTS (SELECT 1 FROM public.point_records WHERE related_reward_id = OLD.id AND type = 'reward_redeem')) THEN
    RAISE EXCEPTION '已有兑换记录，不能修改奖励类型';
  END IF;
  RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS rewards_guard_repeatability ON public.rewards;
CREATE TRIGGER rewards_guard_repeatability BEFORE UPDATE ON public.rewards
  FOR EACH ROW EXECUTE FUNCTION public.guard_reward_repeatability();

-- 旧的两参数函数无请求 ID，必须删除所有签名入口。
DROP FUNCTION IF EXISTS public.redeem_reward(uuid, uuid);
CREATE OR REPLACE FUNCTION public.redeem_reward(p_reward_id uuid, p_child_id uuid, p_request_id uuid)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER set search_path = public, pg_temp AS $$
DECLARE
  v_reward public.rewards%ROWTYPE;
  v_user public.users%ROWTYPE;
  v_existing public.reward_redemptions%ROWTYPE;
  v_point_id uuid;
  v_balance integer;
BEGIN
  IF auth.uid() IS DISTINCT FROM p_child_id OR p_request_id IS NULL OR
    NOT EXISTS (SELECT 1 FROM public.users WHERE uid = auth.uid() AND role = 'child') THEN
    RAISE EXCEPTION 'Only the child can redeem';
  END IF;
  SELECT * INTO v_reward FROM public.rewards WHERE id = p_reward_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Reward not found'; END IF;
  SELECT * INTO v_existing FROM public.reward_redemptions WHERE id = p_request_id;
  IF FOUND THEN
    IF v_existing.child_id <> p_child_id OR v_existing.reward_id <> p_reward_id THEN
      RAISE EXCEPTION 'Request ID belongs to another redemption';
    END IF;
    RETURN v_existing.id;
  END IF;
  SELECT * INTO v_user FROM public.users WHERE uid = p_child_id FOR UPDATE;
  IF v_user.family_id IS DISTINCT FROM v_reward.family_id THEN
    RAISE EXCEPTION 'Reward is outside current family';
  END IF;
  IF NOT v_reward.is_active THEN RAISE EXCEPTION 'Reward is inactive'; END IF;
  IF NOT v_reward.repeatable AND (
    EXISTS (SELECT 1 FROM public.reward_redemptions
            WHERE reward_id = v_reward.id AND status <> 'cancelled') OR
    EXISTS (SELECT 1 FROM public.point_records
            WHERE related_reward_id = v_reward.id AND type = 'reward_redeem'
              AND related_redemption_id IS NULL)
  ) THEN RAISE EXCEPTION 'One-time reward already redeemed'; END IF;
  IF v_user.total_points < v_reward.cost THEN RAISE EXCEPTION 'Insufficient points'; END IF;
  v_balance := v_user.total_points - v_reward.cost;
  UPDATE public.users SET total_points = v_balance WHERE uid = p_child_id;
  INSERT INTO public.reward_redemptions
    (id, family_id, child_id, reward_id, title_snapshot, cost_snapshot, repeatable_snapshot)
  VALUES (p_request_id, v_reward.family_id, p_child_id, v_reward.id,
          v_reward.title, v_reward.cost, v_reward.repeatable);
  INSERT INTO public.point_records
    (family_id, child_id, amount, balance, reason, type, related_reward_id, related_redemption_id)
  VALUES (v_reward.family_id, p_child_id, -v_reward.cost, v_balance,
          '兑换奖励：' || v_reward.title, 'reward_redeem', v_reward.id, p_request_id)
  RETURNING id INTO v_point_id;
  UPDATE public.reward_redemptions SET redeem_point_record_id = v_point_id WHERE id = p_request_id;
  RETURN p_request_id;
END $$;

CREATE OR REPLACE FUNCTION public.cancel_reward_redemption(p_redemption_id uuid, p_child_id uuid)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER set search_path = public, pg_temp AS $$
DECLARE
  v_reward_id uuid;
  v_redemption public.reward_redemptions%ROWTYPE;
  v_balance integer;
  v_point_id uuid;
BEGIN
  IF auth.uid() IS DISTINCT FROM p_child_id OR
    NOT EXISTS (SELECT 1 FROM public.users WHERE uid = auth.uid() AND role = 'child') THEN
    RAISE EXCEPTION 'Only the child can cancel a redemption';
  END IF;
  -- 与兑换采用相同的奖励行优先锁序，取消后一次性奖励才能安全释放名额。
  SELECT reward_id INTO v_reward_id FROM public.reward_redemptions
    WHERE id = p_redemption_id AND child_id = p_child_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'Redemption not found'; END IF;
  PERFORM 1 FROM public.rewards WHERE id = v_reward_id FOR UPDATE;
  SELECT * INTO v_redemption FROM public.reward_redemptions
    WHERE id = p_redemption_id AND child_id = p_child_id FOR UPDATE;
  IF v_redemption.status = 'cancelled' THEN RETURN v_redemption.id; END IF;
  IF v_redemption.status <> 'held' THEN RAISE EXCEPTION 'Used reward cannot be cancelled'; END IF;
  SELECT total_points INTO v_balance FROM public.users WHERE uid = p_child_id FOR UPDATE;
  v_balance := v_balance + v_redemption.cost_snapshot;
  UPDATE public.users SET total_points = v_balance WHERE uid = p_child_id;
  INSERT INTO public.point_records
    (family_id, child_id, amount, balance, reason, type, related_reward_id, related_redemption_id)
  VALUES (v_redemption.family_id, p_child_id, v_redemption.cost_snapshot, v_balance,
          '取消兑换：' || v_redemption.title_snapshot, 'reward_refund',
          v_redemption.reward_id, v_redemption.id)
  RETURNING id INTO v_point_id;
  UPDATE public.reward_redemptions
    SET status = 'cancelled', cancelled_at = now(), refund_point_record_id = v_point_id
    WHERE id = p_redemption_id;
  RETURN p_redemption_id;
END $$;

CREATE OR REPLACE FUNCTION public.use_reward_redemption(p_redemption_id uuid, p_child_id uuid)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER set search_path = public, pg_temp AS $$
DECLARE v_redemption public.reward_redemptions%ROWTYPE;
BEGIN
  IF auth.uid() IS DISTINCT FROM p_child_id OR
    NOT EXISTS (SELECT 1 FROM public.users WHERE uid = auth.uid() AND role = 'child') THEN
    RAISE EXCEPTION 'Only the child can use a redemption';
  END IF;
  SELECT * INTO v_redemption FROM public.reward_redemptions
    WHERE id = p_redemption_id AND child_id = p_child_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Redemption not found'; END IF;
  IF v_redemption.status = 'used' THEN RETURN p_redemption_id; END IF;
  IF v_redemption.status <> 'held' THEN RAISE EXCEPTION 'Cancelled reward cannot be used'; END IF;
  UPDATE public.reward_redemptions SET status = 'used', used_at = now()
    WHERE id = p_redemption_id;
  RETURN p_redemption_id;
END $$;

CREATE OR REPLACE FUNCTION public.get_monthly_task_points(p_child_id uuid)
RETURNS integer LANGUAGE plpgsql STABLE SECURITY DEFINER set search_path = public, pg_temp AS $$
DECLARE v_total integer;
BEGIN
  IF auth.uid() IS DISTINCT FROM p_child_id OR
    NOT EXISTS (SELECT 1 FROM public.users WHERE uid = auth.uid() AND role = 'child') THEN
    RAISE EXCEPTION 'Only the child can read monthly points';
  END IF;
  SELECT COALESCE(sum(amount), 0)::integer INTO v_total FROM public.point_records
    WHERE child_id = p_child_id AND type = 'task_complete'
      AND timestamp >= date_trunc('month', now() AT TIME ZONE 'Asia/Shanghai') AT TIME ZONE 'Asia/Shanghai'
      AND timestamp < (date_trunc('month', now() AT TIME ZONE 'Asia/Shanghai') + interval '1 month') AT TIME ZONE 'Asia/Shanghai';
  RETURN v_total;
END $$;

CREATE OR REPLACE FUNCTION public.get_unavailable_one_time_rewards(p_family_id uuid)
RETURNS TABLE(reward_id uuid) LANGUAGE plpgsql STABLE SECURITY DEFINER set search_path = public, pg_temp AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.users
    WHERE uid = auth.uid() AND role = 'child' AND family_id = p_family_id) THEN
    RAISE EXCEPTION 'Family is not available to current child';
  END IF;
  RETURN QUERY
    SELECT DISTINCT r.id FROM public.rewards r
    WHERE r.family_id = p_family_id AND NOT r.repeatable AND
      (EXISTS (SELECT 1 FROM public.reward_redemptions d
               WHERE d.reward_id = r.id AND d.status <> 'cancelled') OR
       EXISTS (SELECT 1 FROM public.point_records p
               WHERE p.related_reward_id = r.id AND p.type = 'reward_redeem'
                 AND p.related_redemption_id IS NULL));
END $$;

-- 花掉的积分不足时，任务撤销必须拒绝，不能将余额截断为零。
CREATE OR REPLACE FUNCTION public.undo_task_completion(p_task_id uuid, p_child_id uuid)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER set search_path = public, pg_temp AS $$
DECLARE
  v_task public.tasks%ROWTYPE;
  v_record public.point_records%ROWTYPE;
  v_balance integer;
BEGIN
  IF auth.uid() IS DISTINCT FROM p_child_id OR
     NOT EXISTS (SELECT 1 FROM public.users WHERE uid = auth.uid() AND role = 'child') THEN
    RAISE EXCEPTION 'Only the task owner can undo completion';
  END IF;
  SELECT * INTO v_task FROM public.tasks WHERE id = p_task_id AND deleted_at IS NULL FOR UPDATE;
  IF NOT FOUND OR v_task.child_id <> p_child_id OR v_task.status NOT IN ('done', 'verified') THEN
    RAISE EXCEPTION 'Task completion cannot be undone';
  END IF;
  SELECT * INTO v_record FROM public.point_records
    WHERE related_task_id = p_task_id AND child_id = p_child_id AND type = 'task_complete'
    ORDER BY timestamp DESC LIMIT 1 FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Task completion record not found'; END IF;
  SELECT total_points INTO v_balance FROM public.users WHERE uid = p_child_id FOR UPDATE;
  IF v_balance < v_record.amount THEN
    RAISE EXCEPTION '积分已用于兑换，余额不足以撤销该任务';
  END IF;
  UPDATE public.tasks SET status = 'pending', completed_at = NULL, verified_at = NULL WHERE id = p_task_id;
  UPDATE public.users SET total_points = v_balance - v_record.amount WHERE uid = p_child_id;
  DELETE FROM public.point_records WHERE id = v_record.id;
END $$;

REVOKE ALL ON FUNCTION public.redeem_reward(uuid, uuid, uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.cancel_reward_redemption(uuid, uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.use_reward_redemption(uuid, uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_monthly_task_points(uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_unavailable_one_time_rewards(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.redeem_reward(uuid, uuid, uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.cancel_reward_redemption(uuid, uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.use_reward_redemption(uuid, uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_monthly_task_points(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_unavailable_one_time_rewards(uuid) TO authenticated;


-- 来源：20261005_parent_badge_progress.sql
-- 家长记录专项勋章进度。先部署本文件，再安装新版家长端。

create table if not exists public.badge_progress (
    child_id uuid not null references public.users(uid) on delete cascade,
    family_id uuid not null references public.families(id) on delete cascade,
    badge_key text not null,
    segment text not null default '',
    value integer not null default 0 check (value >= 0),
    version integer not null default 1 check (version > 0),
    updated_at timestamptz not null default now(),
    updated_by uuid references public.users(uid) on delete set null,
    primary key (child_id, badge_key, segment),
    constraint badge_progress_key_check check (badge_key in (
        'recognition','dictation','reading','idioms','poems','writing',
        'english_words','english_reading','dino_english',
        'calculation','thinking_seed','math_explaining','life_skills'
    )),
    constraint badge_progress_segment_check check (
        (badge_key = 'english_reading' and segment ~ '^[A-Z]$') or
        (badge_key <> 'english_reading' and segment = '')
    ),
    constraint badge_progress_limit_check check (
        (badge_key in ('recognition','dictation') and value <= 3000) or
        (badge_key = 'idioms' and value <= 256) or
        (badge_key = 'poems' and value <= 155) or
        (badge_key = 'english_words' and value <= 1800) or
        (badge_key = 'dino_english' and value <= 437) or
        (badge_key = 'thinking_seed' and value <= 211) or
        (badge_key = 'english_reading' and value <= case segment
            when 'A' then 50 when 'B' then 50 when 'C' then 52 when 'D' then 59
            when 'E' then 55 when 'F' then 63 when 'G' then 77 when 'H' then 78
            when 'I' then 91 when 'J' then 101 when 'K' then 107 when 'L' then 128
            when 'M' then 134 when 'N' then 144 when 'O' then 117 when 'P' then 99
            when 'Q' then 98 when 'R' then 89 when 'S' then 54 when 'T' then 52
            when 'U' then 55 when 'V' then 58 when 'W' then 58 when 'X' then 59
            when 'Y' then 59 when 'Z' then 62 end) or
        (badge_key in ('reading','writing','calculation','math_explaining','life_skills'))
    )
);
create index if not exists badge_progress_family_child_idx on public.badge_progress(family_id,child_id);

create table if not exists public.badge_progress_audit (
    id uuid primary key default gen_random_uuid(),
    child_id uuid not null references public.users(uid) on delete cascade,
    family_id uuid not null references public.families(id) on delete cascade,
    badge_key text not null,
    segment text not null default '',
    old_value integer not null,
    new_value integer not null,
    changed_by uuid references public.users(uid) on delete set null,
    changed_at timestamptz not null default now()
);
create index if not exists badge_progress_audit_child_time_idx on public.badge_progress_audit(child_id,changed_at desc);

alter table public.badge_progress enable row level security;
alter table public.badge_progress_audit enable row level security;
drop policy if exists badge_progress_family_read on public.badge_progress;
create policy badge_progress_family_read on public.badge_progress for select to authenticated
    using (exists(select 1 from public.users u where u.uid = auth.uid() and u.family_id = badge_progress.family_id
        and (u.role = 'parent' or (u.role = 'child' and u.uid = badge_progress.child_id))));
drop policy if exists badge_progress_audit_family_read on public.badge_progress_audit;
create policy badge_progress_audit_family_read on public.badge_progress_audit for select to authenticated
    using (exists(select 1 from public.users u where u.uid = auth.uid() and u.family_id = badge_progress_audit.family_id
        and (u.role = 'parent' or (u.role = 'child' and u.uid = badge_progress_audit.child_id))));
revoke all on public.badge_progress, public.badge_progress_audit from anon, authenticated;
grant select on public.badge_progress, public.badge_progress_audit to authenticated;

create or replace function public.set_badge_progress(
    p_child_id uuid, p_badge_key text, p_segment text, p_value integer, p_expected_version integer
) returns public.badge_progress language plpgsql security definer set search_path = public, pg_temp as $$
declare
    v_family_id uuid;
    v_old public.badge_progress%rowtype;
    v_new public.badge_progress%rowtype;
begin
    select family_id into v_family_id from public.users where uid = auth.uid() and role = 'parent';
    if v_family_id is null or not exists(select 1 from public.users
        where uid = p_child_id and family_id = v_family_id and role = 'child') then
        raise exception 'Child is outside current parent family';
    end if;
    if p_value is null or p_value < 0 or p_expected_version is null or p_expected_version < 0 then
        raise exception 'Invalid value or version';
    end if;
    -- 表约束同样校验 key、segment、上限；函数先给明确的客户端错误。
    if p_badge_key not in ('recognition','dictation','reading','idioms','poems','writing',
        'english_words','english_reading','dino_english','calculation','thinking_seed',
        'math_explaining','life_skills') or p_segment is null or
       (p_badge_key = 'english_reading' and p_segment !~ '^[A-Z]$') or
       (p_badge_key <> 'english_reading' and p_segment <> '') then
        raise exception 'Invalid badge key or segment';
    end if;
    -- 同一孩子的并发首次写入也要串行；版本冲突由下方检查返回。
    perform pg_advisory_xact_lock(hashtextextended(p_child_id::text || ':' || p_badge_key || ':' || p_segment, 0));
    select * into v_old from public.badge_progress
        where child_id = p_child_id and badge_key = p_badge_key and segment = p_segment for update;
    if found then
        if v_old.version <> p_expected_version then raise exception 'Badge progress version conflict'; end if;
        if v_old.value = p_value then return v_old; end if;
        update public.badge_progress set value = p_value, version = version + 1,
            updated_at = now(), updated_by = auth.uid()
        where child_id = p_child_id and badge_key = p_badge_key and segment = p_segment
        returning * into v_new;
    else
        if p_expected_version <> 0 then raise exception 'Badge progress version conflict'; end if;
        insert into public.badge_progress(child_id,family_id,badge_key,segment,value,version,updated_by)
        values(p_child_id,v_family_id,p_badge_key,p_segment,p_value,1,auth.uid()) returning * into v_new;
    end if;
    insert into public.badge_progress_audit(child_id,family_id,badge_key,segment,old_value,new_value,changed_by)
    values(p_child_id,v_family_id,p_badge_key,p_segment,coalesce(v_old.value,0),p_value,auth.uid());
    return v_new;
end;
$$;
revoke all on function public.set_badge_progress(uuid,text,text,integer,integer) from public, anon;
grant execute on function public.set_badge_progress(uuid,text,text,integer,integer) to authenticated;


-- 每日任务内容只允许本家庭家长编辑尚未完成的今天及未来实例。

create or replace function public.update_daily_task_description(p_task_id uuid, p_description text)
returns void language plpgsql security definer set search_path = public, pg_temp as $$
declare
    v_family_id uuid;
begin
    select family_id into v_family_id from public.users
    where uid = auth.uid() and role = 'parent';
    if v_family_id is null then raise exception 'Only parents can edit task descriptions'; end if;
    update public.tasks
    set description = btrim(coalesce(p_description, ''))
    where id = p_task_id and family_id = v_family_id
      and deleted_at is null and status = 'pending'
      and due_date >= (now() at time zone 'Asia/Shanghai')::date;
    if not found then raise exception 'Task is not editable'; end if;
end;
$$;

revoke all on function public.update_daily_task_description(uuid, text) from public, anon;
grant execute on function public.update_daily_task_description(uuid, text) to authenticated;



-- 来源：20261020_parent_task_timing_management.sql
-- 任务库计时开关及已安排任务同步。先于孩子端计时迁移执行。

alter table public.task_templates add column if not exists timer_enabled boolean not null default false;
alter table public.tasks add column if not exists timer_enabled boolean not null default false;

-- 修改模板时同步未完成实例；已完成实例的快照不再变化。
create or replace function public.sync_template_timer_enabled()
returns trigger language plpgsql security definer set search_path = public, pg_temp as $$
begin
  if new.timer_enabled is distinct from old.timer_enabled then
    if to_regclass('public.task_timer_sessions') is not null and not new.timer_enabled then
      -- 会话表在第二份迁移后出现；其 trigger 会在本次更新后处理运行态。
      execute 'update public.task_timer_sessions set status = ''paused'', accumulated_seconds = least(21600, accumulated_seconds + greatest(0, extract(epoch from least(now(), last_heartbeat) - running_since))::integer), running_since = null, updated_at = now() where source_template_id = $1 and status = ''running''' using new.id;
    end if;
    update public.tasks set timer_enabled = new.timer_enabled
    where source_template_id = new.id and status = 'pending' and deleted_at is null;
  end if;
  return new;
end $$;
drop trigger if exists task_template_timer_sync on public.task_templates;
create trigger task_template_timer_sync after update of timer_enabled on public.task_templates
for each row execute function public.sync_template_timer_enabled();

-- 回收站还原时重新采用当前模板开关，避免已取消任务保留过期快照。
create or replace function public.sync_restored_task_timer_enabled()
returns trigger language plpgsql security definer set search_path = public, pg_temp as $$
begin
  if old.deleted_at is not null and new.deleted_at is null and new.source_template_id is not null then
    select t.timer_enabled into new.timer_enabled from public.task_templates t
      where t.id = new.source_template_id and t.family_id = new.family_id;
    if not found then new.timer_enabled := false; end if;
  end if;
  return new;
end $$;
drop trigger if exists task_timer_restore_sync on public.tasks;
create trigger task_timer_restore_sync before update of deleted_at on public.tasks
for each row execute function public.sync_restored_task_timer_enabled();

create or replace function public.disable_orphan_task_timer()
returns trigger language plpgsql security definer set search_path = public, pg_temp as $$
begin
  if old.source_template_id is not null and new.source_template_id is null then
    new.timer_enabled := false;
  end if;
  return new;
end $$;
drop trigger if exists task_timer_orphan_disable on public.tasks;
create trigger task_timer_orphan_disable before update of source_template_id on public.tasks
for each row execute function public.disable_orphan_task_timer();

-- 保留 20261010 的八参数/七参数创建接口，额外复制计时开关。

create or replace function public.create_tasks_from_selection(
    p_child_id uuid, p_category_id uuid, p_template_id uuid,
    p_start_date date, p_end_date date, p_recurrence_type text,
    p_recurrence_weekdays integer[], p_selected_template_ids uuid[]
)
returns setof public.tasks language plpgsql security definer set search_path = public, pg_temp as $$
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
                source_category_id, source_template_id, growth_domain, timer_enabled, due_date, due_time,
                reward_points, penalty_points, recurrence_series_id, recurrence_type,
                recurrence_weekdays, recurrence_end_date
            ) values (
                v_family_id, v_template.title, '', p_child_id, auth.uid(), 'pending', v_category_name,
                p_category_id, v_template.id, coalesce(v_template.growth_domain, 'other'), v_template.timer_enabled, v_date, null,
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
returns setof public.tasks language sql security definer set search_path = public, pg_temp as $$
    select * from public.create_tasks_from_selection(
        p_child_id, p_category_id, p_template_id, p_start_date, p_end_date,
        p_recurrence_type, p_recurrence_weekdays, null::uuid[]
    );
$$;

revoke all on function public.create_tasks_from_selection(uuid, uuid, uuid, date, date, text, integer[], uuid[]) from public, anon;
grant execute on function public.create_tasks_from_selection(uuid, uuid, uuid, date, date, text, integer[], uuid[]) to authenticated;
revoke all on function public.create_tasks_from_selection(uuid, uuid, uuid, date, date, text, integer[]) from public, anon;
grant execute on function public.create_tasks_from_selection(uuid, uuid, uuid, date, date, text, integer[]) to authenticated;


-- 来源：20261020_kid_task_timer.sql
-- 首页计时：会话互斥、有效用时及原子完成。依赖 20261020_parent_task_timing_management.sql。

create table if not exists public.task_timer_sessions (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references public.families(id),
  child_id uuid not null references public.users(uid),
  task_id uuid not null references public.tasks(id) on delete cascade,
  source_template_id uuid references public.task_templates(id) on delete set null,
  mode text not null check (mode in ('countup','countdown')),
  target_seconds integer check (target_seconds between 60 and 3600 and target_seconds % 60 = 0),
  status text not null check (status in ('running','paused','finished','abandoned')),
  accumulated_seconds integer not null default 0 check (accumulated_seconds between 0 and 21600),
  running_since timestamptz,
  last_heartbeat timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint countdown_has_target check ((mode = 'countdown') = (target_seconds is not null))
);
create unique index if not exists task_timer_one_open_per_child
  on public.task_timer_sessions(child_id) where status in ('running','paused');
create index if not exists task_timer_task_created on public.task_timer_sessions(task_id, created_at desc);

create table if not exists public.task_duration_records (
  id uuid primary key default gen_random_uuid(),
  task_id uuid not null unique references public.tasks(id) on delete cascade,
  family_id uuid not null references public.families(id),
  child_id uuid not null references public.users(uid),
  source_template_id uuid references public.task_templates(id) on delete set null,
  task_title text not null,
  completed_at timestamptz not null default now(),
  duration_seconds integer not null check (duration_seconds between 1 and 21600),
  previous_average_seconds integer,
  previous_sample_count integer not null default 0
);
create index if not exists task_duration_child_template_date
  on public.task_duration_records(child_id, source_template_id, completed_at desc);
create index if not exists task_duration_family_template_date
  on public.task_duration_records(family_id, source_template_id, completed_at desc);

-- 日历打卡、家长取消等旧入口若改变任务状态，立即释放未结束会话。
create or replace function public.close_unavailable_task_timer()
returns trigger language plpgsql security definer set search_path = public, pg_temp as $$
begin
  if (new.status <> 'pending' or new.deleted_at is not null) and
      (old.status = 'pending' and old.deleted_at is null) then
    update public.task_timer_sessions set status = 'abandoned', running_since = null,
      updated_at = now() where task_id = new.id and status in ('running','paused');
  elsif old.source_template_id is not null and new.source_template_id is null then
    update public.task_timer_sessions set status='paused', running_since=null,
      accumulated_seconds=least(21600, accumulated_seconds +
        greatest(0, extract(epoch from least(now(), last_heartbeat) - running_since))::integer),
      updated_at=now() where task_id=new.id and status='running';
  end if;
  return new;
end $$;
drop trigger if exists task_timer_task_unavailable on public.tasks;
create trigger task_timer_task_unavailable after update of status, deleted_at, source_template_id on public.tasks
for each row execute function public.close_unavailable_task_timer();

alter table public.task_timer_sessions enable row level security;
alter table public.task_duration_records enable row level security;
drop policy if exists timer_session_child_read on public.task_timer_sessions;
create policy timer_session_child_read on public.task_timer_sessions for select to authenticated
using (child_id = auth.uid());
drop policy if exists duration_family_read on public.task_duration_records;
create policy duration_family_read on public.task_duration_records for select to authenticated
using (exists (select 1 from public.users u where u.uid = auth.uid()
  and u.family_id = task_duration_records.family_id
  and (u.role = 'parent' or u.uid = task_duration_records.child_id)));
revoke all on public.task_timer_sessions, public.task_duration_records from anon, authenticated;
grant select on public.task_timer_sessions, public.task_duration_records to authenticated;

-- 运行会话若失去心跳，按最后一次心跳结算并自动暂停。
create or replace function public.settle_task_timer(p_session_id uuid, p_force_pause boolean default false)
returns void language plpgsql security definer set search_path = public, pg_temp as $$
declare s public.task_timer_sessions%rowtype; v_stop timestamptz; v_seconds integer;
begin
  select * into s from public.task_timer_sessions where id = p_session_id for update;
  if not found or s.status <> 'running' then return; end if;
  if s.last_heartbeat < now() - interval '30 seconds' then
    v_stop := s.last_heartbeat;
    p_force_pause := true;
  else
    v_stop := now();
  end if;
  v_seconds := greatest(0, floor(extract(epoch from v_stop - s.running_since))::integer);
  if s.accumulated_seconds + v_seconds >= 21600 then
    v_seconds := greatest(0, 21600 - s.accumulated_seconds);
    p_force_pause := true;
  end if;
  if p_force_pause then
    update public.task_timer_sessions set accumulated_seconds = accumulated_seconds + v_seconds,
      status = 'paused', running_since = null, updated_at = now() where id = s.id;
  end if;
end $$;
revoke all on function public.settle_task_timer(uuid, boolean) from public, anon, authenticated;

-- start / heartbeat / pause / resume / finish / abandon。返回服务端会话快照。
create or replace function public.task_timer_action(
  p_task_id uuid, p_action text, p_mode text default null, p_target_seconds integer default null
) returns setof public.task_timer_sessions language plpgsql security definer set search_path = public, pg_temp as $$
declare t public.tasks%rowtype; s public.task_timer_sessions%rowtype;
  v_duration integer; v_average integer; v_count integer;
begin
  if not exists(select 1 from public.users where uid = auth.uid() and role = 'child') then
    raise exception 'Only children can use task timers';
  end if;
  -- 串行化同一孩子的新建和操作，唯一索引也负责最后防线。
  perform pg_advisory_xact_lock(hashtextextended(auth.uid()::text, 12020));
  select * into t from public.tasks where id = p_task_id and child_id = auth.uid() and deleted_at is null for update;
  if not found then raise exception 'Task is not available'; end if;
  select * into s from public.task_timer_sessions
    where child_id = auth.uid() and status in ('running','paused') order by created_at desc limit 1 for update;
  if found then perform public.settle_task_timer(s.id); select * into s from public.task_timer_sessions where id = s.id; end if;
  if p_action = 'start' then
    if s.id is not null then
      if s.task_id <> p_task_id then raise exception 'Another task is already timed'; end if;
      return next s; return;
    end if;
    if t.status <> 'pending' or not t.timer_enabled or t.source_template_id is null then
      raise exception 'Task timing is disabled';
    end if;
    if p_mode not in ('countup','countdown') or
       (p_mode = 'countdown' and (p_target_seconds is null or p_target_seconds < 60 or p_target_seconds > 3600 or p_target_seconds % 60 <> 0)) or
       (p_mode = 'countup' and p_target_seconds is not null) then
      raise exception 'Invalid timer mode or target';
    end if;
    insert into public.task_timer_sessions(family_id, child_id, task_id, source_template_id, mode,
      target_seconds, status, running_since, last_heartbeat)
    values(t.family_id, t.child_id, t.id, t.source_template_id, p_mode, p_target_seconds,
      'running', now(), now()) returning * into s;
    return next s; return;
  end if;
  if p_action = 'finish' and t.status <> 'pending' then
    select * into s from public.task_timer_sessions
      where child_id = auth.uid() and task_id = p_task_id and status = 'finished'
      order by updated_at desc limit 1;
    if found then return next s; return; end if;
  end if;
  if s.id is null or s.task_id <> p_task_id then raise exception 'Active timer not found'; end if;
  if p_action = 'heartbeat' then
    if s.status = 'running' then
      update public.task_timer_sessions set last_heartbeat = now(), updated_at = now() where id = s.id returning * into s;
    end if;
  elsif p_action = 'pause' then
    perform public.settle_task_timer(s.id, true);
    select * into s from public.task_timer_sessions where id = s.id;
  elsif p_action = 'resume' then
    if t.status <> 'pending' or not t.timer_enabled or s.accumulated_seconds >= 21600 then
      raise exception 'Timer cannot resume';
    end if;
    if s.status = 'paused' then
      update public.task_timer_sessions set status = 'running', running_since = now(),
        last_heartbeat = now(), updated_at = now() where id = s.id returning * into s;
    end if;
  elsif p_action = 'abandon' then
    update public.task_timer_sessions set status = 'abandoned', running_since = null,
      updated_at = now() where id = s.id returning * into s;
  elsif p_action = 'finish' then
    if t.status <> 'pending' then raise exception 'Task is not pending'; end if;
    perform public.settle_task_timer(s.id, true);
    select * into s from public.task_timer_sessions where id = s.id;
    v_duration := s.accumulated_seconds;
    if v_duration < 1 then raise exception 'Timer must run for at least one second'; end if;
    select count(*), round(avg(duration_seconds))::integer into v_count, v_average
      from public.task_duration_records
      where child_id = t.child_id and source_template_id = t.source_template_id
        and completed_at >= now() - interval '30 days';
    -- complete_task is the latest verified reward transaction; any error rolls this entire RPC back.
    perform public.complete_task(t.id, t.child_id);
    insert into public.task_duration_records(task_id, family_id, child_id, source_template_id,
      task_title, duration_seconds, previous_average_seconds, previous_sample_count)
    values(t.id, t.family_id, t.child_id, t.source_template_id, t.title, v_duration, v_average, v_count);
    update public.task_timer_sessions set status = 'finished', updated_at = now() where id = s.id returning * into s;
  else
    raise exception 'Unknown timer action';
  end if;
  return next s;
end $$;
revoke all on function public.task_timer_action(uuid, text, text, integer) from public, anon;
grant execute on function public.task_timer_action(uuid, text, text, integer) to authenticated;

create or replace function public.get_task_duration_averages(p_family_id uuid, p_child_id uuid default null)
returns table(source_template_id uuid, child_id uuid, average_seconds integer, sample_count integer)
language plpgsql stable security definer set search_path = public, pg_temp as $$
begin
  if not exists(select 1 from public.users u where u.uid = auth.uid() and u.family_id = p_family_id
    and (u.role = 'parent' or (u.role = 'child' and p_child_id = auth.uid()))) then
    raise exception 'Family is not available';
  end if;
  return query select d.source_template_id, case when p_child_id is null then null::uuid else d.child_id end,
    round(avg(d.duration_seconds))::integer,
    count(*)::integer from public.task_duration_records d
    where d.family_id = p_family_id and d.source_template_id is not null
      and d.completed_at >= now() - interval '30 days'
      and (p_child_id is null or d.child_id = p_child_id)
    group by d.source_template_id, case when p_child_id is null then null::uuid else d.child_id end;
end $$;
revoke all on function public.get_task_duration_averages(uuid, uuid) from public, anon;
grant execute on function public.get_task_duration_averages(uuid, uuid) to authenticated;

create or replace function public.get_task_duration_records(p_family_id uuid, p_template_id uuid)
returns setof public.task_duration_records language plpgsql stable security definer set search_path = public, pg_temp as $$
begin
  if not exists(select 1 from public.users where uid = auth.uid() and family_id = p_family_id and role = 'parent') then
    raise exception 'Only family parents can read duration details';
  end if;
  return query select * from public.task_duration_records
    where family_id = p_family_id and source_template_id = p_template_id
      and completed_at >= now() - interval '30 days' order by completed_at desc;
end $$;
revoke all on function public.get_task_duration_records(uuid, uuid) from public, anon;
grant execute on function public.get_task_duration_records(uuid, uuid) to authenticated;

create or replace function public.get_child_task_duration_records(p_child_id uuid)
returns setof public.task_duration_records language plpgsql stable security definer set search_path = public, pg_temp as $$
begin
  if auth.uid() is distinct from p_child_id or
      not exists(select 1 from public.users where uid = p_child_id and role = 'child') then
    raise exception 'Only the child can read own durations';
  end if;
  return query select * from public.task_duration_records
    where child_id = p_child_id and completed_at >= now() - interval '30 days'
    order by completed_at desc;
end $$;
revoke all on function public.get_child_task_duration_records(uuid) from public, anon;
grant execute on function public.get_child_task_duration_records(uuid) to authenticated;

create or replace function public.delete_task_duration_record(p_record_id uuid)
returns void language plpgsql security definer set search_path = public, pg_temp as $$
begin
  delete from public.task_duration_records d where d.id = p_record_id
    and exists(select 1 from public.users u where u.uid = auth.uid() and u.role = 'parent' and u.family_id = d.family_id);
  if not found then raise exception 'Duration record not available'; end if;
end $$;
revoke all on function public.delete_task_duration_record(uuid) from public, anon;
grant execute on function public.delete_task_duration_record(uuid) to authenticated;

-- 由数据库调度或运维定时调用；查询始终过滤窗口，清理延迟不影响统计。
create or replace function public.prune_task_duration_records()
returns integer language plpgsql security definer set search_path = public, pg_temp as $$
declare v_deleted integer;
begin
  if auth.role() is distinct from 'service_role' and current_user <> 'postgres' then
    raise exception 'Service role required'; end if;
  delete from public.task_duration_records where completed_at < now() - interval '30 days';
  get diagnostics v_deleted = row_count;
  delete from public.task_timer_sessions where status in ('finished','abandoned') and updated_at < now() - interval '30 days';
  return v_deleted;
end $$;
revoke all on function public.prune_task_duration_records() from public, anon, authenticated;
grant execute on function public.prune_task_duration_records() to service_role;

-- Supabase pg_cron 每小时清理；迁移若无法启用扩展会失败，避免只过滤查询却长期存储旧用时。
-- 超级用户扩展安装由 prerequisites 脚本负责；业务函数仍归 postgres。
do $$ begin
 if not exists(select 1 from pg_extension where extname='pg_cron') then
  raise exception 'Apply local parent business prerequisites as supabase_admin first';
 end if;
end $$;
select cron.schedule('task-duration-prune-hourly', '0 * * * *',
  'select public.prune_task_duration_records();');

-- 沿用 20260930 的“积分不足不能撤销”规则，并删除本次用时。
create or replace function public.undo_task_completion(p_task_id uuid, p_child_id uuid)
returns void language plpgsql security definer set search_path = public, pg_temp as $$
declare v_task public.tasks%rowtype; v_record public.point_records%rowtype; v_balance integer;
begin
  if auth.uid() is distinct from p_child_id or
     not exists(select 1 from public.users where uid = auth.uid() and role = 'child') then
    raise exception 'Only the task owner can undo completion'; end if;
  select * into v_task from public.tasks where id = p_task_id and deleted_at is null for update;
  if not found or v_task.child_id <> p_child_id or v_task.status not in ('done','verified') then
    raise exception 'Task completion cannot be undone'; end if;
  select * into v_record from public.point_records where related_task_id = p_task_id
    and child_id = p_child_id and type = 'task_complete' order by timestamp desc limit 1 for update;
  if not found then raise exception 'Task completion record not found'; end if;
  select total_points into v_balance from public.users where uid = p_child_id for update;
  if v_balance < v_record.amount then raise exception '积分已用于兑换，余额不足以撤销该任务'; end if;
  update public.tasks set status = 'pending', completed_at = null, verified_at = null where id = p_task_id;
  update public.users set total_points = v_balance - v_record.amount where uid = p_child_id;
  delete from public.point_records where id = v_record.id;
  delete from public.task_duration_records where task_id = p_task_id;
end $$;



create or replace function public.local_guard_task_sources()
returns trigger language plpgsql security definer set search_path = public, pg_temp as $$
begin
 if new.source_template_id is not null and not exists(select 1 from public.task_templates t where t.id=new.source_template_id and t.family_id=new.family_id) then raise exception 'Template is outside task family'; end if;
 if new.source_category_id is not null and not exists(select 1 from public.categories c where c.id=new.source_category_id and c.family_id=new.family_id) then raise exception 'Category is outside task family'; end if;
 return new;
end $$;
drop trigger if exists local_tasks_guard_sources on public.tasks;
create trigger local_tasks_guard_sources before insert or update of source_template_id,source_category_id,family_id on public.tasks for each row execute function public.local_guard_task_sources();
-- Internal trigger helpers are never client RPC endpoints.
revoke all on function public.local_guard_task_sources() from public,anon,authenticated;
notify pgrst, 'reload schema';
commit;
