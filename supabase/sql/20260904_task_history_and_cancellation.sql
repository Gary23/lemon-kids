-- 任务实例即历史记录：禁止删除过去或已完成的任务；“删除”只取消今天及未来的待完成任务。
-- 本脚本使用 Asia/Shanghai 作为家庭业务日期。如未来支持家庭时区，应改为读取 families.time_zone。

begin;

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
set search_path = public
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
set search_path = public
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
set search_path = public
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
set search_path = public
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
set search_path = public
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
set search_path = public
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
set search_path = public
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

commit;
