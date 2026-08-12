-- 认字教学音频的归档与可重试清理。
--
-- 前置条件：已执行 20260806_literacy_tts_assets.sql、
-- 20260806_literacy_tts_storage_paths.sql 与 20260806_literacy_tts_generator_rpc.sql。
-- 本脚本需在 Supabase Dashboard 的 SQL Editor 审查后执行；不向客户端开放
-- 资产、归档或清理 RPC。

-- 清理工作者领取任务时使用 deleting，避免一个仍在飞行中的生成器把已经投递
-- 删除的资产重新标记为 ready。
do $$
declare
    status_constraint text;
begin
    select conname into status_constraint
      from pg_constraint
     where conrelid = 'public.literacy_tts_assets'::regclass
       and contype = 'c'
       and pg_get_constraintdef(oid) like '%delete_pending%'
     limit 1;

    if status_constraint is not null then
        execute format('alter table public.literacy_tts_assets drop constraint %I', status_constraint);
    end if;
end;
$$;

alter table public.literacy_tts_assets
    add constraint literacy_tts_assets_status_check check (
        status in ('pending', 'processing', 'ready', 'failed', 'delete_pending', 'deleting', 'deleted')
    );

-- recognized_characters 物理删除时，其关联资产的外键会按既有定义置空。已完成
-- 删除的历史资产因此允许没有来源；仍在生命周期内的资产必须继续保有精确来源。
do $$
declare
    ownership_constraint text;
begin
    select conname into ownership_constraint
      from pg_constraint
     where conrelid = 'public.literacy_tts_assets'::regclass
       and contype = 'c'
       and pg_get_constraintdef(oid) like '%root_literacy_character_id%'
       and pg_get_constraintdef(oid) like '%recognized_character_id%'
       and pg_get_constraintdef(oid) not like '%object_path%'
       and pg_get_constraintdef(oid) not like '%status%'
     limit 1;

    if ownership_constraint is not null then
        execute format('alter table public.literacy_tts_assets drop constraint %I', ownership_constraint);
    end if;
end;
$$;

alter table public.literacy_tts_assets
    drop constraint if exists literacy_tts_assets_active_ownership_check;

alter table public.literacy_tts_assets
    add constraint literacy_tts_assets_active_ownership_check check (
        status = 'deleted'
        or root_literacy_character_id is not null
        or recognized_character_id is not null
    );

-- 已删除资产在 recognized_characters 删除后会失去归属 ID；其 object_path 仅用于
-- 审计，不再被播放或删除流程使用，因此允许保留。非 deleted 资产仍严格执行路径契约。
alter table public.literacy_tts_assets
    drop constraint if exists literacy_tts_assets_object_path_contract_check;

alter table public.literacy_tts_assets
    add constraint literacy_tts_assets_object_path_contract_check check (
        status = 'deleted'
        or object_path is null
        or (
            root_literacy_character_id is not null
            and object_path =
                voice_version || '/task/' || root_literacy_character_id::text || '/' ||
                case item_type
                    when 'character' then 'character.mp3'
                    else item_type || '-' || item_order::text || '.mp3'
                end
        )
        or (
            root_literacy_character_id is null
            and recognized_character_id is not null
            and object_path =
                voice_version || '/recognized/' || recognized_character_id::text || '/' ||
                case item_type
                    when 'character' then 'character.mp3'
                    else item_type || '-' || item_order::text || '.mp3'
                end
        )
    );

-- 历史系统转入记录在早期函数中未写入来源任务时，根据同一孩子和主字回填。
-- 若历史中存在多个同字任务，不猜测归属，保持 NULL 并按“手工/导入”范围清理。
with unambiguous_task as (
    select child_id, character, (array_agg(id order by id))[1] as id
      from public.child_literacy_characters
     group by child_id, character
    having count(*) = 1
)
update public.recognized_characters r
   set source_literacy_character_id = t.id
  from unambiguous_task t
 where r.source = 'system'
   and r.source_literacy_character_id is null
   and r.child_id = t.child_id
   and r.character = t.character;

-- 将一条已认识字转入字库，并只投递清理任务。整个事务的业务顺序严格为：
-- known_characters 幂等写入成功 -> 清空失效 URL -> delete_pending ->（无资产时）删记录。
-- 物理对象删除不在本事务内执行，因此 Storage 临时故障绝不会回滚已存入的字库。
create or replace function public.archive_recognized_character(
    p_child_id uuid,
    p_recognized_character_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    recognized public.recognized_characters%rowtype;
    pending_asset_count integer := 0;
begin
    select * into recognized
      from public.recognized_characters
     where id = p_recognized_character_id
       and child_id = p_child_id
     for update;

    -- 网络重试可能发生在清理完成、记录已物理删除之后；此时仍视为归档成功。
    if not found then
        return jsonb_build_object('archived', true, 'already_archived', true);
    end if;

    insert into public.known_characters (user_id, character, learned_at)
    values (recognized.child_id, recognized.character, coalesce(recognized.recognized_at, now()))
    on conflict (user_id, character) do nothing;

    -- 已投递删除后，不再给仍留在设备中的任务/复习卡暴露已失效的 URL。
    update public.recognized_characters
       set character_audio_url = '',
           character_audio_version = null,
           character_audio_hash = null,
           words = (
               select coalesce(jsonb_agg(
                   case when jsonb_typeof(item.value) = 'object'
                        then item.value - 'audio_url' - 'audio_version' - 'audio_hash'
                        else item.value end
                   order by item.ordinality
               ), '[]'::jsonb)
                 from jsonb_array_elements(coalesce(recognized.words, '[]'::jsonb))
                      with ordinality as item(value, ordinality)
           ),
           sentences = (
               select coalesce(jsonb_agg(
                   case when jsonb_typeof(item.value) = 'object'
                        then item.value - 'audio_url' - 'audio_version' - 'audio_hash'
                        else item.value end
                   order by item.ordinality
               ), '[]'::jsonb)
                 from jsonb_array_elements(coalesce(recognized.sentences, '[]'::jsonb))
                      with ordinality as item(value, ordinality)
           )
     where id = recognized.id;

    if recognized.source_literacy_character_id is not null then
        update public.child_literacy_characters c
           set character_audio_url = '',
               character_audio_version = null,
               character_audio_hash = null,
               words = (
                   select coalesce(jsonb_agg(
                       case when jsonb_typeof(item.value) = 'object'
                            then item.value - 'audio_url' - 'audio_version' - 'audio_hash'
                            else item.value end
                       order by item.ordinality
                   ), '[]'::jsonb)
                     from jsonb_array_elements(coalesce(c.words, '[]'::jsonb))
                          with ordinality as item(value, ordinality)
               ),
               sentences = (
                   select coalesce(jsonb_agg(
                       case when jsonb_typeof(item.value) = 'object'
                            then item.value - 'audio_url' - 'audio_version' - 'audio_hash'
                            else item.value end
                       order by item.ordinality
                   ), '[]'::jsonb)
                     from jsonb_array_elements(coalesce(c.sentences, '[]'::jsonb))
                          with ordinality as item(value, ordinality)
               )
         where c.id = recognized.source_literacy_character_id;
    end if;

    update public.literacy_tts_assets
       set status = 'delete_pending',
           last_error = null,
           updated_at = now()
     where status <> 'deleted'
       and (
            recognized_character_id = recognized.id
            or (
                recognized.source_literacy_character_id is not null
                and root_literacy_character_id = recognized.source_literacy_character_id
            )
       );

    select count(*) into pending_asset_count
      from public.literacy_tts_assets
     where status <> 'deleted'
       and (
            recognized_character_id = recognized.id
            or (
                recognized.source_literacy_character_id is not null
                and root_literacy_character_id = recognized.source_literacy_character_id
            )
       );

    -- 没有生成过音频也不应永久遗留一条已认识记录。
    if pending_asset_count = 0 then
        delete from public.recognized_characters where id = recognized.id;
    end if;

    return jsonb_build_object(
        'archived', true,
        'character', recognized.character,
        'cleanup_pending_assets', pending_asset_count
    );
end;
$$;

-- 清理工作者领取 delete_pending 资产。意外中断超过 15 分钟的 deleting 资产可安全重领。
create or replace function public.claim_literacy_tts_assets_for_deletion(
    p_limit integer default 50
)
returns setof public.literacy_tts_assets
language sql
security definer
set search_path = public
as $$
    with candidates as (
        select a.id
          from public.literacy_tts_assets a
         where a.status = 'delete_pending'
            or (a.status = 'deleting' and a.updated_at < now() - interval '15 minutes')
         order by a.updated_at, a.id
         for update skip locked
         limit greatest(1, least(p_limit, 50))
    )
    update public.literacy_tts_assets a
       set status = 'deleting',
           updated_at = now()
      from candidates c
     where a.id = c.id
 returning a.*;
$$;

-- Storage 删除成功（对象原本不存在也算成功）后调用。最后一条关联资产删除完成时，
-- 才物理删除 recognized_characters；FK 会将历史资产上的 recognized ID 置空。
create or replace function public.mark_literacy_tts_asset_deleted(
    p_asset_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    asset public.literacy_tts_assets%rowtype;
    cleanup_recognized_id uuid;
    source_task_id uuid;
    recognized_deleted boolean := false;
begin
    select * into asset
      from public.literacy_tts_assets
     where id = p_asset_id
       and status = 'deleting'
     for update;
    if not found then
        raise exception '资产 % 不处于 deleting 状态', p_asset_id;
    end if;

    update public.literacy_tts_assets
       set status = 'deleted',
           deleted_at = now(),
           last_error = null,
           updated_at = now()
     where id = asset.id;

    cleanup_recognized_id := asset.recognized_character_id;
    if cleanup_recognized_id is null and asset.root_literacy_character_id is not null then
        -- 早期“任务来源”资产没有 recognized ID；归档中的系统记录仍可通过根任务找到。
        select id into cleanup_recognized_id
          from public.recognized_characters
         where source_literacy_character_id = asset.root_literacy_character_id
         limit 1;
    end if;

    if cleanup_recognized_id is not null then
        select source_literacy_character_id into source_task_id
          from public.recognized_characters
         where id = cleanup_recognized_id
         for update;

        if found and not exists (
            select 1
              from public.literacy_tts_assets a
             where a.status <> 'deleted'
               and (
                    a.recognized_character_id = cleanup_recognized_id
                    or (
                        source_task_id is not null
                        and a.root_literacy_character_id = source_task_id
                    )
               )
        ) then
            delete from public.recognized_characters where id = cleanup_recognized_id;
            recognized_deleted := true;
        end if;
    end if;

    return jsonb_build_object('asset_deleted', true, 'recognized_deleted', recognized_deleted);
end;
$$;

-- 删除失败时只把任务放回可重试队列；不影响已写入的 known_characters。
create or replace function public.defer_literacy_tts_asset_deletion(
    p_asset_id uuid,
    p_reason text
)
returns void
language sql
security definer
set search_path = public
as $$
    update public.literacy_tts_assets
       set status = 'delete_pending',
           last_error = left(coalesce(p_reason, ''), 1000),
           updated_at = now()
     where id = p_asset_id
       and status = 'deleting';
$$;

revoke all on function public.archive_recognized_character(uuid, uuid) from public, anon, authenticated;
revoke all on function public.claim_literacy_tts_assets_for_deletion(integer) from public, anon, authenticated;
revoke all on function public.mark_literacy_tts_asset_deleted(uuid) from public, anon, authenticated;
revoke all on function public.defer_literacy_tts_asset_deletion(uuid, text) from public, anon, authenticated;

grant execute on function public.archive_recognized_character(uuid, uuid) to service_role;
grant execute on function public.claim_literacy_tts_assets_for_deletion(integer) to service_role;
grant execute on function public.mark_literacy_tts_asset_deleted(uuid) to service_role;
grant execute on function public.defer_literacy_tts_asset_deletion(uuid, text) to service_role;

-- 部署后验证（应仅出现 service_role 与 postgres）：
-- select routine_name, grantee, privilege_type
-- from information_schema.role_routine_grants
-- where routine_schema = 'public'
--   and routine_name in (
--       'archive_recognized_character', 'claim_literacy_tts_assets_for_deletion',
--       'mark_literacy_tts_asset_deleted', 'defer_literacy_tts_asset_deletion'
--   )
-- order by routine_name, grantee;
