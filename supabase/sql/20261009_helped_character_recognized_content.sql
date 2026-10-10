-- 帮助记录收录已有复习字时，原子更新词句、音素资产和收录时间。
-- 前置：20260823_literacy_phonetic_assets.sql。
create or replace function public.update_recognized_literacy_content_from_help(
    p_child_id uuid, p_character text, p_words jsonb, p_sentences jsonb
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    target_id uuid;
    old_words jsonb;
    old_sentences jsonb;
    item jsonb;
    item_index integer;
begin
    select id, words, sentences into target_id, old_words, old_sentences
      from public.recognized_characters
     where child_id = p_child_id and character = p_character
     for update;
    if target_id is null then raise exception '未找到该已认识汉字'; end if;
    if jsonb_typeof(p_words) <> 'array' or jsonb_typeof(p_sentences) <> 'array'
       or jsonb_array_length(p_words) < 1 or jsonb_array_length(p_sentences) <> 1 then
        raise exception '词句内容不完整';
    end if;

    if (select jsonb_agg(value->>'text' order by ordinality)
          from jsonb_array_elements(coalesce(old_words, '[]'::jsonb)) with ordinality)
       is not distinct from
       (select jsonb_agg(value->>'text' order by ordinality)
          from jsonb_array_elements(p_words) with ordinality)
       and
       (select jsonb_agg(value->>'text' order by ordinality)
          from jsonb_array_elements(coalesce(old_sentences, '[]'::jsonb)) with ordinality)
       is not distinct from
       (select jsonb_agg(value->>'text' order by ordinality)
          from jsonb_array_elements(p_sentences) with ordinality) then
        update public.recognized_characters set recognized_at = now() where id = target_id;
    else
        update public.recognized_characters
           set words = p_words, sentences = p_sentences, recognized_at = now()
         where id = target_id;
        delete from public.literacy_phonetic_assets
         where content_source = 'recognized' and literacy_character_id = target_id;
        for item, item_index in
            select value, ordinality - 1 from jsonb_array_elements(p_words) with ordinality
        loop
            insert into public.literacy_phonetic_assets (
                content_source, literacy_character_id, item_type, item_index, item_text
            ) values ('recognized', target_id, 'word', item_index, btrim(item->>'text'));
        end loop;
        for item, item_index in
            select value, ordinality - 1 from jsonb_array_elements(p_sentences) with ordinality
        loop
            insert into public.literacy_phonetic_assets (
                content_source, literacy_character_id, item_type, item_index, item_text
            ) values ('recognized', target_id, 'sentence', item_index, btrim(item->>'text'));
        end loop;
    end if;
    return target_id;
end;
$$;

revoke all on function public.update_recognized_literacy_content_from_help(uuid, text, jsonb, jsonb)
    from public, anon, authenticated;
grant execute on function public.update_recognized_literacy_content_from_help(uuid, text, jsonb, jsonb)
    to service_role;

-- 同字仍有待认识任务时，更新该任务的词句并直接完成为已认识。
create or replace function public.complete_pending_literacy_content_from_help(
    p_child_id uuid, p_character text, p_words jsonb, p_sentences jsonb
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    task_id uuid;
    item jsonb;
    item_index integer;
begin
    select id into task_id from public.child_literacy_characters
     where child_id = p_child_id and character = p_character and learned_at is null
     for update;
    if task_id is null then raise exception '未找到该待认识任务'; end if;
    if jsonb_typeof(p_words) <> 'array' or jsonb_typeof(p_sentences) <> 'array'
       or jsonb_array_length(p_words) < 1 or jsonb_array_length(p_sentences) <> 1 then
        raise exception '词句内容不完整';
    end if;
    update public.child_literacy_characters
       set words = p_words, sentences = p_sentences where id = task_id;
    delete from public.literacy_phonetic_assets
     where content_source = 'pending' and literacy_character_id = task_id;
    for item, item_index in
        select value, ordinality - 1 from jsonb_array_elements(p_words) with ordinality
    loop
        insert into public.literacy_phonetic_assets (
            content_source, literacy_character_id, item_type, item_index, item_text
        ) values ('pending', task_id, 'word', item_index, btrim(item->>'text'));
    end loop;
    for item, item_index in
        select value, ordinality - 1 from jsonb_array_elements(p_sentences) with ordinality
    loop
        insert into public.literacy_phonetic_assets (
            content_source, literacy_character_id, item_type, item_index, item_text
        ) values ('pending', task_id, 'sentence', item_index, btrim(item->>'text'));
    end loop;
    perform public.complete_literacy_character_with_phonetic_assets(p_child_id, task_id, true);
    return task_id;
end;
$$;

revoke all on function public.complete_pending_literacy_content_from_help(uuid, text, jsonb, jsonb)
    from public, anon, authenticated;
grant execute on function public.complete_pending_literacy_content_from_help(uuid, text, jsonb, jsonb)
    to service_role;
