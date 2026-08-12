-- 认字教学音频清理完成后立即移除资产记录。
--
-- 前置条件：已执行 20260807_literacy_tts_cleanup.sql。
-- 此脚本覆盖清理成功回调；不会删除 pending、processing、ready、failed、
-- delete_pending 或 deleting 状态的资产，因此不会影响生成、播放或失败重试。

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
            -- 所有关联对象均已从 Storage 删除。先清除已删除资产，再删除已认识字，
            -- 以免外键把关联 ID 置空后留下无归属的历史行。
            delete from public.literacy_tts_assets a
             where a.status = 'deleted'
               and (
                    a.recognized_character_id = cleanup_recognized_id
                    or (
                        source_task_id is not null
                        and a.root_literacy_character_id = source_task_id
                    )
               );

            delete from public.recognized_characters where id = cleanup_recognized_id;
            recognized_deleted := true;
        end if;
    end if;

    return jsonb_build_object('asset_deleted', true, 'recognized_deleted', recognized_deleted);
end;
$$;

revoke all on function public.mark_literacy_tts_asset_deleted(uuid) from public, anon, authenticated;
grant execute on function public.mark_literacy_tts_asset_deleted(uuid) to service_role;

-- 可选的一次性历史清理：确认不再需要旧审计记录后再单独执行。
-- delete from public.literacy_tts_assets where status = 'deleted';
