-- 修复智能添加“直接收录已认识”与历史根任务唯一约束的冲突。
--
-- 本文件适用于已经执行过 20260827_smart_add_recognized_literacy_tasks.sql 的环境。
-- 旧的全量唯一约束阻止同字完成后再次创建历史任务，与智能添加既有规则
-- “历史已完成任务允许再次创建”不一致。改为只约束未完成任务：同一孩子同一字
-- 同时最多一条待认识任务，已完成历史可保留多条。

alter table public.child_literacy_characters
    drop constraint if exists child_literacy_characters_child_id_character_key;

create unique index if not exists child_literacy_characters_child_unlearned_character_key
    on public.child_literacy_characters (child_id, character)
    where learned_at is null;
