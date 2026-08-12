-- 认字模块结构收敛：执行前请先完成 20260802_remove_literacy_pinyin.sql。
--
-- 保留：
--   child_literacy_characters（字、words、sentences 和学习状态；唯一教学内容源）
--   child_literacy_character_help_requests（孩子主动点读过的词/句）
--
-- 此脚本会删除已停止写入的旧评测历史和重复的词句表：
--   child_literacy_reading_attempts
--   child_literacy_reading_attempt_items
--   child_literacy_reading_character_results
--   child_literacy_examples
-- 不会删除 child_literacy_characters 的 words / sentences，也不会删除求助记录。

-- 先处理 target_type 的旧命名。按迁移后的类型去重，保留最早的求助记录。
alter table if exists public.child_literacy_character_help_requests
    drop constraint if exists child_literacy_character_help_requests_target_type_check;

with ranked_requests as (
    select
        id,
        row_number() over (
            partition by
                child_id,
                case when target_type = 'word_group' then 'word' else target_type end,
                target_text
            order by created_at asc, id asc
        ) as row_number
    from public.child_literacy_character_help_requests
)
delete from public.child_literacy_character_help_requests request_row
using ranked_requests ranked
where request_row.id = ranked.id
  and ranked.row_number > 1;

update public.child_literacy_character_help_requests
set target_type = 'word'
where target_type = 'word_group';

alter table if exists public.child_literacy_character_help_requests
    drop constraint if exists child_literacy_help_requests_child_content_key,
    add constraint child_literacy_help_requests_child_content_key
        unique (child_id, target_type, target_text),
    add constraint child_literacy_character_help_requests_target_type_check
        check (target_type in ('character', 'word', 'sentence')),
    drop column if exists family_id;

-- 评测结果仅在 Pad 当前弹层使用，不再有任何客户端或云函数读写以下历史表。
drop table if exists public.child_literacy_reading_character_results;
drop table if exists public.child_literacy_reading_attempt_items;
drop table if exists public.child_literacy_reading_attempts;

-- words / sentences 已存于主任务表，删除重复存储。
drop table if exists public.child_literacy_examples;

-- 验证：应仅返回 child_literacy_characters 和 child_literacy_character_help_requests。
-- select table_name
-- from information_schema.tables
-- where table_schema = 'public'
--   and table_name like 'child_literacy_%'
-- order by table_name;
