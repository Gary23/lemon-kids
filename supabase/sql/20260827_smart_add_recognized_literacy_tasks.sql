-- 智能添加识字可直接收录为已认识字。
--
-- 仍先创建 child_literacy_characters 任务及 pending 音素资产，再在同一事务内复用
-- complete_literacy_character_with_phonetic_assets(..., true) 完成迁移。这样历史任务、
-- 已认识内容、音素资产的生命周期与孩子完成待认识任务时完全一致，且绝不进入字库分支。
--
-- 前置：已执行 20260823_literacy_phonetic_assets.sql 与
-- 20260823_literacy_phonetic_asset_lifecycle_atomic.sql。

create or replace function public.create_recognized_literacy_tasks_with_phonetic_assets(
    p_child_id uuid,
    p_family_id uuid,
    p_rows jsonb
)
returns table (id uuid, recognized_character_id uuid, "character" text)
language plpgsql
security definer
set search_path = public
as $$
declare
    row_value jsonb;
    created public.child_literacy_characters%rowtype;
    recognized_id uuid;
    item jsonb;
    item_index integer;
begin
    if jsonb_typeof(p_rows) <> 'array' then raise exception 'p_rows 必须为数组'; end if;
    for row_value in select value from jsonb_array_elements(p_rows)
    loop
        insert into public.child_literacy_characters (
            family_id, child_id, character, words, sentences, sort_order
        ) values (
            p_family_id, p_child_id, row_value->>'character',
            coalesce(row_value->'words', '[]'::jsonb),
            coalesce(row_value->'sentences', '[]'::jsonb),
            coalesce((row_value->>'sort_order')::integer, 0)
        ) returning * into created;

        for item, item_index in
            select value, ordinality - 1
              from jsonb_array_elements(coalesce(created.words, '[]'::jsonb)) with ordinality
        loop
            insert into public.literacy_phonetic_assets (
                content_source, literacy_character_id, item_type, item_index, item_text
            ) values ('pending', created.id, 'word', item_index, btrim(item->>'text'));
        end loop;
        for item, item_index in
            select value, ordinality - 1
              from jsonb_array_elements(coalesce(created.sentences, '[]'::jsonb)) with ordinality
        loop
            insert into public.literacy_phonetic_assets (
                content_source, literacy_character_id, item_type, item_index, item_text
            ) values ('pending', created.id, 'sentence', item_index, btrim(item->>'text'));
        end loop;

        -- 固定 true：智能添加到已认识不得因任何客户端点读状态写入 known_characters。
        perform public.complete_literacy_character_with_phonetic_assets(
            p_child_id, created.id, true
        );

        select r.id into recognized_id
          from public.recognized_characters r
         where r.child_id = p_child_id
           and r.character = created.character;
        if recognized_id is null then raise exception '已认识字迁移失败'; end if;

        id := created.id;
        recognized_character_id := recognized_id;
        "character" := created.character;
        return next;
    end loop;
end;
$$;

-- 新入口在转移完成后才投递 TTS。为避免音频生成稍后才入队时只回写待认识历史任务，
-- 让根任务的 TTS 资产自动关联到其已认识复习卡，生成结果会同步回写两张表。
create or replace function public.attach_recognized_character_to_literacy_tts_asset()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if new.root_literacy_character_id is not null and new.recognized_character_id is null then
        select r.id into new.recognized_character_id
          from public.recognized_characters r
         where r.source_literacy_character_id = new.root_literacy_character_id
         limit 1;
    end if;
    return new;
end;
$$;

drop trigger if exists attach_recognized_character_to_literacy_tts_asset
    on public.literacy_tts_assets;
create trigger attach_recognized_character_to_literacy_tts_asset
before insert or update of root_literacy_character_id, recognized_character_id
on public.literacy_tts_assets
for each row execute function public.attach_recognized_character_to_literacy_tts_asset();

revoke all on function public.create_recognized_literacy_tasks_with_phonetic_assets(uuid, uuid, jsonb)
    from public, anon, authenticated;
grant execute on function public.create_recognized_literacy_tasks_with_phonetic_assets(uuid, uuid, jsonb)
    to service_role;
