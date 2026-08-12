-- 补齐早期“待认识 → 已认识”自动转存时遗漏的主字音频元数据。
--
-- 仅处理系统转入、仍可关联到原识字任务、且主字音频 URL 为空的记录；
-- 不触碰手工/导入的已认识字，也不覆盖已经正确保存的音频版本。
update public.recognized_characters as recognized
   set character_audio_url = task.character_audio_url,
       character_audio_version = task.character_audio_version,
       character_audio_hash = task.character_audio_hash
  from public.child_literacy_characters as task
 where recognized.source = 'system'
   and recognized.source_literacy_character_id = task.id
   and recognized.child_id = task.child_id
   and coalesce(recognized.character_audio_url, '') = ''
   and coalesce(task.character_audio_url, '') <> '';

-- 部署后核验（应返回 0）：
-- select count(*)
-- from public.recognized_characters recognized
-- join public.child_literacy_characters task
--   on task.id = recognized.source_literacy_character_id
--  and task.child_id = recognized.child_id
-- where recognized.source = 'system'
--   and coalesce(recognized.character_audio_url, '') = ''
--   and coalesce(task.character_audio_url, '') <> '';
