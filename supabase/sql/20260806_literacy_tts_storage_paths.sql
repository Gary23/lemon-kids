-- 认字点读 TTS 对象路径约束。
--
-- 前置条件：已执行 20260806_literacy_tts_assets.sql 与
-- 20260806_literacy_tts_storage.sql。
-- object_path 只保存 bucket 内的相对路径，不包含 "literacy-audio/" 前缀。
-- 生成 SCF 上传时必须指定 bucket = literacy-audio、contentType = audio/mpeg，
-- 并在成功上传且文件大小非零后，再将该相对路径写入本表。
--
-- 系统任务（包括其转入的已认识字）使用：
--   {voice_version}/task/{root_literacy_character_id}/character.mp3
--   {voice_version}/task/{root_literacy_character_id}/word-{item_order}.mp3
--   {voice_version}/task/{root_literacy_character_id}/sentence-{item_order}.mp3
-- 手工/导入的已认识字使用：
--   {voice_version}/recognized/{recognized_character_id}/character.mp3
--   {voice_version}/recognized/{recognized_character_id}/word-{item_order}.mp3
--   {voice_version}/recognized/{recognized_character_id}/sentence-{item_order}.mp3
--
-- 路径以根任务或已认识字 ID 隔离，不得使用全局文本哈希作为物理对象地址。
-- 这样后续按资产清单删除时，不会误删其他任务仍在使用的音频。

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conrelid = 'public.literacy_tts_assets'::regclass
          and conname = 'literacy_tts_assets_object_path_contract_check'
    ) then
        alter table public.literacy_tts_assets
            add constraint literacy_tts_assets_object_path_contract_check
            check (
                object_path is null
                or (
                    root_literacy_character_id is not null
                    and object_path =
                        voice_version || '/task/' || root_literacy_character_id::text || '/' ||
                        case item_type
                            when 'character' then 'character.mp3'
                            else item_type || '-' || item_order::text || '.mp3'
                        end
                )
                or (
                    root_literacy_character_id is null
                    and recognized_character_id is not null
                    and object_path =
                        voice_version || '/recognized/' || recognized_character_id::text || '/' ||
                        case item_type
                            when 'character' then 'character.mp3'
                            else item_type || '-' || item_order::text || '.mp3'
                        end
                )
            );
    end if;
end
$$;

-- 部署后验证：应返回 literacy_tts_assets_object_path_contract_check。
-- select conname
-- from pg_constraint
-- where conrelid = 'public.literacy_tts_assets'::regclass
--   and conname = 'literacy_tts_assets_object_path_contract_check';
