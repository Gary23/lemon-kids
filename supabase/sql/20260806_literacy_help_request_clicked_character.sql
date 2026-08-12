-- 求助记录除完整词/句外，保留孩子长按的具体汉字及其在内容中的位置。
-- 依赖：已执行 20260805_literacy_help_request_sources.sql。
-- 历史记录没有可恢复的点击位置，保留为空；新版云函数写入的记录两个字段均非空。

alter table public.child_literacy_character_help_requests
    add column if not exists requested_character text,
    add column if not exists character_index integer;

-- 过去相同词/句只保留一条记录。现在同一内容点到不同位置时要分别保留，
-- 以便“帮助过的内容”精确展示当时被点的字。
alter table public.child_literacy_character_help_requests
    drop constraint if exists child_literacy_help_requests_child_content_key,
    drop constraint if exists child_literacy_help_requests_clicked_content_key,
    drop constraint if exists child_literacy_help_requests_clicked_character_check,
    add constraint child_literacy_help_requests_clicked_content_key
        unique (child_id, target_type, target_text, requested_character, character_index),
    add constraint child_literacy_help_requests_clicked_character_check
        check (
            (requested_character is null and character_index is null)
            or (
                requested_character ~ '^[一-龥]$'
                and character_index >= 0
            )
        );

-- 验证：新增长按记录应带有被点击的字与位置；同一词/句的不同点击位置可同时存在。
-- select target_type, target_text, requested_character, character_index, created_at
-- from public.child_literacy_character_help_requests
-- where child_id = '<孩子 UUID>'
-- order by created_at desc;
