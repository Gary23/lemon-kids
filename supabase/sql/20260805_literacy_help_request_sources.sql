-- 长按点读的求助记录可以来自认字任务，也可以来自 recognized_characters。
-- 在 Supabase Dashboard -> SQL Editor 执行一次；依赖
-- 20260801_literacy_help_requests.sql、20260802_simplify_literacy_schema.sql
-- 和 20260804_recognized_characters.sql 已执行。

alter table public.child_literacy_character_help_requests
    alter column literacy_character_id drop not null,
    add column if not exists recognized_character_id uuid
        references public.recognized_characters(id) on delete cascade;

-- 历史记录均来自认字任务。新记录必须且只能关联一种来源，避免留下无归属的求助内容。
alter table public.child_literacy_character_help_requests
    drop constraint if exists child_literacy_help_requests_one_source_check,
    add constraint child_literacy_help_requests_one_source_check
        check (
            (literacy_character_id is not null and recognized_character_id is null)
            or (literacy_character_id is null and recognized_character_id is not null)
        );

create index if not exists child_literacy_help_requests_recognized_content_created_idx
    on public.child_literacy_character_help_requests (recognized_character_id, target_type, created_at desc)
    where recognized_character_id is not null;

-- 验证：长按已认识主字，或长按词/句中已经进入 known_characters 的字后，
-- 应能看到一条上下文求助记录；其余长按只朗读，不写入此表。
-- select target_type, target_text, literacy_character_id, recognized_character_id, created_at
-- from public.child_literacy_character_help_requests
-- where child_id = '<孩子 UUID>'
-- order by created_at desc;
