-- 认字点读腾讯 TTS 的数据模型。
--
-- 前置条件：已执行 20260804_literacy_learning_items.sql 与
-- 20260804_recognized_characters.sql。此脚本仅创建数据结构，不创建 Storage
-- bucket，也不开放孩子端对资产队列的访问权限。
--
-- words / sentences 继续存放在两个主表的 JSONB 数组中。每项的目标结构为：
-- {"text":"树木","audio_url":"…","audio_version":"v1","audio_hash":"…"}。
-- 历史 JSON 未包含 audio_version / audio_hash 时由生成器视为待生成；这里不回填
-- 空值，以便生成器可以明确识别历史数据。

alter table public.child_literacy_characters
    add column if not exists character_audio_url text not null default '',
    add column if not exists character_audio_version text,
    add column if not exists character_audio_hash text;

alter table public.recognized_characters
    add column if not exists character_audio_url text not null default '',
    add column if not exists character_audio_version text,
    add column if not exists character_audio_hash text,
    add column if not exists source_literacy_character_id uuid
        references public.child_literacy_characters(id) on delete set null;

-- 系统转入的已认识字可借此快速回溯到原认字任务；手工和导入记录保持 NULL。
create index if not exists recognized_characters_source_literacy_character_idx
    on public.recognized_characters (source_literacy_character_id)
    where source_literacy_character_id is not null;

-- 音频资产既是生成队列，也是删除清单。
-- root_literacy_character_id 用于认字任务及其系统转入的已认识字；
-- recognized_character_id 用于手工/导入已认识字，也可记录系统转入后的归属，
-- 方便归档时覆盖两条记录相关的资产。
create table if not exists public.literacy_tts_assets (
    id uuid primary key default gen_random_uuid(),
    root_literacy_character_id uuid
        references public.child_literacy_characters(id) on delete set null,
    recognized_character_id uuid
        references public.recognized_characters(id) on delete set null,
    item_type text not null check (item_type in ('character', 'word', 'sentence')),
    -- words / sentences 的 JSON 下标；主字固定为 0。
    item_order integer not null check (
        item_order >= 0
        and (item_type <> 'character' or item_order = 0)
    ),
    source_text text not null check (char_length(btrim(source_text)) > 0),
    -- 对 source_text、音色版本与语速等合成输入计算的稳定摘要。
    source_hash text not null check (char_length(btrim(source_hash)) > 0),
    voice_version text not null check (char_length(btrim(voice_version)) > 0),
    speed smallint not null,
    object_path text,
    status text not null default 'pending' check (
        status in ('pending', 'processing', 'ready', 'failed', 'delete_pending', 'deleted')
    ),
    attempt_count integer not null default 0 check (attempt_count >= 0),
    last_error text,
    -- 腾讯云 TextToVoice 的 RequestId；便于排错，不存储任何密钥或令牌。
    provider_request_id text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    check (root_literacy_character_id is not null or recognized_character_id is not null)
);

create index if not exists literacy_tts_assets_status_idx
    on public.literacy_tts_assets (status);

create index if not exists literacy_tts_assets_root_literacy_character_idx
    on public.literacy_tts_assets (root_literacy_character_id)
    where root_literacy_character_id is not null;

create index if not exists literacy_tts_assets_recognized_character_idx
    on public.literacy_tts_assets (recognized_character_id)
    where recognized_character_id is not null;

-- 一个认字根任务的同一内容只允许存在一个指定音色版本的资产，避免并发重复合成扣费。
create unique index if not exists literacy_tts_assets_root_item_voice_key
    on public.literacy_tts_assets (
        root_literacy_character_id, item_type, item_order, voice_version
    )
    where root_literacy_character_id is not null;

-- 没有根任务的手工/导入已认识字，也必须具备同等的去重保护。
create unique index if not exists literacy_tts_assets_recognized_item_voice_key
    on public.literacy_tts_assets (
        recognized_character_id, item_type, item_order, voice_version
    )
    where root_literacy_character_id is null
      and recognized_character_id is not null;

-- 资产队列及错误详情只供 service_role 的生成、清理任务使用；孩子端从主表读取 URL。
alter table public.literacy_tts_assets enable row level security;

-- 部署后验证：
-- select indexname from pg_indexes
-- where schemaname = 'public' and tablename = 'literacy_tts_assets'
-- order by indexname;
--
-- select column_name from information_schema.columns
-- where table_schema = 'public'
--   and table_name in ('child_literacy_characters', 'recognized_characters')
--   and (
--       column_name like 'character_audio%'
--       or column_name = 'source_literacy_character_id'
--   );
