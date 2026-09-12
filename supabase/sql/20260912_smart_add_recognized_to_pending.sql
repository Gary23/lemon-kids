-- 智能添加中将“已认识”字转回待认识。
--
-- 前置：已执行 20260823_literacy_phonetic_assets.sql 与
-- 20260827_smart_add_recognized_existing_task_fix.sql。
-- 同一事务中删除旧已认识记录及其音素资产，再创建新的待认识任务和 pending 音素资产。
-- 这样客户端不会看到已认识记录已删除、待认识任务却未创建的中间状态。

create or replace function public.create_literacy_tasks_replacing_recognized_with_phonetic_assets(
    p_child_id uuid,
    p_family_id uuid,
    p_rows jsonb
)
returns table (id uuid, "character" text)
language plpgsql
security definer
set search_path = public
as $$
declare
    row_value jsonb;
    created public.child_literacy_characters%rowtype;
    replaced_recognized_id uuid;
    item jsonb;
    item_index integer;
begin
    if jsonb_typeof(p_rows) <> 'array' then raise exception 'p_rows 必须为数组'; end if;
    for row_value in select value from jsonb_array_elements(p_rows)
    loop
        -- 锁定后再删除，防止与其他“置顶”或存库操作并发时留下孤立音素资产。
        select r.id into replaced_recognized_id
          from public.recognized_characters r
         where r.child_id = p_child_id
           and r.character = row_value->>'character'
         for update;

        if replaced_recognized_id is not null then
            delete from public.literacy_phonetic_assets
             where content_source = 'recognized'
               and literacy_character_id = replaced_recognized_id;

            delete from public.recognized_characters
             where id = replaced_recognized_id;
        end if;

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

        id := created.id;
        "character" := created.character;
        return next;
    end loop;
end;
$$;

revoke all on function public.create_literacy_tasks_replacing_recognized_with_phonetic_assets(uuid, uuid, jsonb)
    from public, anon, authenticated;
grant execute on function public.create_literacy_tasks_replacing_recognized_with_phonetic_assets(uuid, uuid, jsonb)
    to service_role;
