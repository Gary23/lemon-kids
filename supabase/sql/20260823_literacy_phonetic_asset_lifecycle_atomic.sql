-- 音素资产生命周期原子化。
--
-- 前置：已执行 20260823_literacy_phonetic_assets.sql、20260807_literacy_tts_cleanup.sql。
-- 本迁移将“完成待认识字”和“已认识字存库”各自收敛为一次 RPC 调用：主业务写入、
-- 音频清理投递（由既有 archive RPC 完成）及音素资产迁移/清理处于同一数据库事务。

create or replace function public.complete_literacy_character_with_phonetic_assets(
    p_child_id uuid,
    p_literacy_character_id uuid,
    p_has_character_audio_point_read boolean default true
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    task public.child_literacy_characters%rowtype;
    recognized_id uuid;
    should_recognize boolean := coalesce(p_has_character_audio_point_read, true);
begin
    select * into task
      from public.child_literacy_characters
     where id = p_literacy_character_id
       and child_id = p_child_id
     for update;
    if not found then raise exception '未找到该识字任务'; end if;

    if should_recognize then
        -- 不覆盖同字的手工/既有复习卡；冲突时读取它的 ID 后合并资产。
        insert into public.recognized_characters (
            family_id, child_id, character,
            character_audio_url, character_audio_version, character_audio_hash,
            words, sentences, source, source_literacy_character_id
        ) values (
            task.family_id, task.child_id, task.character,
            task.character_audio_url, task.character_audio_version, task.character_audio_hash,
            task.words, task.sentences, 'system', task.id
        )
        on conflict (child_id, character) do nothing
        returning id into recognized_id;

        if recognized_id is null then
            select id into recognized_id
              from public.recognized_characters
             where child_id = task.child_id and character = task.character
             for update;
        end if;

        -- 若同一复习卡已有资产，ready（包括人工维护）优先；否则以刚完成任务的
        -- ready 资产补齐。processing/failed 资产重置为 pending，避免工作者在转移
        -- 期间持有旧 ID 后把新资产永久卡在 processing。最后删除 pending 来源，确保
        -- 不会留下双份资产。
        insert into public.literacy_phonetic_assets (
            content_source, literacy_character_id, item_type, item_index, item_text,
            phoneme_tokens, status, attempt_count, last_error, generator_version
        )
        select 'recognized', recognized_id, item_type, item_index, item_text,
               case when status = 'ready' then phoneme_tokens else null end,
               case when status = 'ready' then 'ready' else 'pending' end,
               case when status = 'ready' then attempt_count else 0 end,
               case when status = 'ready' then last_error else null end,
               generator_version
          from public.literacy_phonetic_assets
         where content_source = 'pending'
           and literacy_character_id = task.id
        on conflict (content_source, literacy_character_id, item_type, item_index) do update
            set phoneme_tokens = excluded.phoneme_tokens,
                status = excluded.status,
                attempt_count = excluded.attempt_count,
                last_error = excluded.last_error,
                generator_version = excluded.generator_version,
                updated_at = now()
          where public.literacy_phonetic_assets.status <> 'ready'
            and excluded.status = 'ready';

        delete from public.literacy_phonetic_assets
         where content_source = 'pending' and literacy_character_id = task.id;
    else
        insert into public.known_characters (user_id, character, learned_at)
        values (task.child_id, task.character, now())
        on conflict (user_id, character) do nothing;

        delete from public.literacy_phonetic_assets
         where content_source = 'pending' and literacy_character_id = task.id;
    end if;

    update public.child_literacy_characters
       set learned_at = coalesce(learned_at, now())
     where id = task.id;

    return jsonb_build_object('character', task.character, 'recognized', should_recognize);
end;
$$;

-- archive_recognized_character 已在自身函数体内完成字库写入和音频清理投递。
-- PostgreSQL 函数调用不会开启独立事务，因此这里的音素清理与它天然同生共死。
create or replace function public.archive_recognized_character_with_phonetic_assets(
    p_child_id uuid,
    p_recognized_character_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    archived jsonb;
begin
    archived := public.archive_recognized_character(p_child_id, p_recognized_character_id);
    delete from public.literacy_phonetic_assets
     where content_source = 'recognized'
       and literacy_character_id = p_recognized_character_id;
    return archived;
end;
$$;

revoke all on function public.complete_literacy_character_with_phonetic_assets(uuid, uuid, boolean)
    from public, anon, authenticated;
revoke all on function public.archive_recognized_character_with_phonetic_assets(uuid, uuid)
    from public, anon, authenticated;
grant execute on function public.complete_literacy_character_with_phonetic_assets(uuid, uuid, boolean)
    to service_role;
grant execute on function public.archive_recognized_character_with_phonetic_assets(uuid, uuid)
    to service_role;

-- 部署后核验（应全部为 0）：
-- select count(*) from literacy_phonetic_assets a
-- left join child_literacy_characters c on a.content_source = 'pending' and a.literacy_character_id = c.id
-- left join recognized_characters r on a.content_source = 'recognized' and a.literacy_character_id = r.id
-- where (a.content_source = 'pending' and c.id is null)
--    or (a.content_source = 'recognized' and r.id is null);
