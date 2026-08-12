-- 全量切换腾讯内置词典评测后，删除认字评测不再使用的拼音结构。
-- 在 Supabase Dashboard -> SQL Editor 执行一次。
-- 此操作会删除历史逐字拼音和历史评测中的拼音列，请确认不再需要这些数据。

drop table if exists public.child_literacy_example_pronunciations cascade;

alter table if exists public.child_literacy_reading_character_results
    drop column if exists expected_pinyin,
    drop column if exists hypothesis_pinyin;

alter table if exists public.child_literacy_characters
    drop column if exists pinyin;

-- 已废弃的动态字库拼音列也一并删除，避免后续继续维护两套拼音数据。
alter table if exists public.known_characters
    drop column if exists pinyin;
