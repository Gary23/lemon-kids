-- 将求助记录从“点击了哪个字”迁移为“请求朗读了哪个词/句”。
-- 依赖：已执行 20260801_literacy_help_requests.sql。
-- 本脚本会删除同一孩子重复的相同词/句记录，仅保留最早一条。

-- 旧版词组记录的 target_text 是以中文逗号连接的完整词组；根据旧的点击位置
-- 还原为实际被朗读的那个词。句子和单字记录本身就是完整朗读内容，无需改写。
do $$
declare
    help_request record;
    parts text[];
    part text;
    content_start integer;
begin
    for help_request in
        select id, target_text, character_index
        from public.child_literacy_character_help_requests
        where target_type = 'word_group'
    loop
        parts := string_to_array(help_request.target_text, '，');
        content_start := 0;
        foreach part in array parts
        loop
            if help_request.character_index >= content_start
               and help_request.character_index < content_start + char_length(part) then
                update public.child_literacy_character_help_requests
                set target_text = part
                where id = help_request.id;
                exit;
            end if;
            content_start := content_start + char_length(part) + 1;
        end loop;
    end loop;
end $$;

delete from public.child_literacy_character_help_requests
where id in (
    select id
    from (
        select
            id,
            row_number() over (
                partition by child_id, target_type, target_text
                order by created_at asc, id asc
            ) as row_number
        from public.child_literacy_character_help_requests
    ) duplicated_rows
    where row_number > 1
);

alter table public.child_literacy_character_help_requests
    drop constraint if exists child_literacy_help_requests_child_character_key;

drop index if exists public.child_literacy_help_requests_character_created_idx;

alter table public.child_literacy_character_help_requests
    drop column if exists requested_character,
    drop column if exists character_index,
    add constraint child_literacy_help_requests_child_content_key
    unique (child_id, target_type, target_text);

create index if not exists child_literacy_help_requests_content_created_idx
    on public.child_literacy_character_help_requests (child_id, target_type, created_at desc);

-- 验证：同一孩子的同一词或句不应重复。
-- select child_id, target_type, target_text, count(*)
-- from public.child_literacy_character_help_requests
-- group by child_id, target_type, target_text
-- having count(*) > 1;
