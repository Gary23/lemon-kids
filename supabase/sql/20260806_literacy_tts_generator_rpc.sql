-- 认字点读 TTS 生成器所需的服务端 RPC。
--
-- 前置条件：已执行 20260806_literacy_tts_assets.sql、
-- 20260806_literacy_tts_storage.sql 与 20260806_literacy_tts_storage_paths.sql。
-- 本脚本不给 anon/authenticated 开放任何权限；仅 generate-literacy-audio SCF
-- 使用 service_role 调用。它同时保证：同一资产只能被一个批处理抢占，且资产
-- 状态与主表/JSON 中的 audio_url 在一个数据库事务内回写。

create table if not exists public.literacy_tts_daily_usage (
    usage_date date primary key,
    character_count integer not null default 0 check (character_count >= 0),
    updated_at timestamptz not null default now()
);

-- 读取当前字、词、句并补齐首次生成所需的队列。相同位置、同一音色版本已有
-- 资产时不覆盖：文本/音色发生变化必须先提高 voice_version，避免覆盖客户端缓存。
create or replace function public.enqueue_literacy_tts_assets(
    p_source text,
    p_record_id uuid,
    p_voice_version text,
    p_speed smallint default -1
)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
    inserted_count integer := 0;
begin
    if p_source not in ('task', 'recognized') then
        raise exception 'p_source 只能为 task 或 recognized';
    end if;
    if char_length(btrim(coalesce(p_voice_version, ''))) = 0 then
        raise exception 'p_voice_version 不能为空';
    end if;

    if p_source = 'task' then
        with source_items as (
            select c.id as root_id, null::uuid as recognized_id,
                   'character'::text as item_type, 0 as item_order,
                   btrim(c.character) as source_text
              from public.child_literacy_characters c
             where (p_record_id is null or c.id = p_record_id)
            union all
            select c.id, null::uuid, 'word', (w.ordinality - 1)::integer,
                   btrim(case when jsonb_typeof(w.value) = 'string'
                              then w.value #>> '{}'
                              else coalesce(w.value ->> 'text', '') end)
              from public.child_literacy_characters c
              cross join lateral jsonb_array_elements(coalesce(c.words, '[]'::jsonb))
                   with ordinality as w(value, ordinality)
             where (p_record_id is null or c.id = p_record_id)
            union all
            select c.id, null::uuid, 'sentence', (s.ordinality - 1)::integer,
                   btrim(case when jsonb_typeof(s.value) = 'string'
                              then s.value #>> '{}'
                              else coalesce(s.value ->> 'text', '') end)
              from public.child_literacy_characters c
              cross join lateral jsonb_array_elements(coalesce(c.sentences, '[]'::jsonb))
                   with ordinality as s(value, ordinality)
             where (p_record_id is null or c.id = p_record_id)
        ), inserted as (
            insert into public.literacy_tts_assets (
                root_literacy_character_id, recognized_character_id,
                item_type, item_order, source_text, source_hash, voice_version, speed
            )
            select root_id, recognized_id, item_type, item_order, source_text,
                   md5(source_text || E'\\x1f' || p_voice_version || E'\\x1f' || p_speed::text),
                   p_voice_version, p_speed
              from source_items
             where source_text <> ''
            on conflict (root_literacy_character_id, item_type, item_order, voice_version)
                where root_literacy_character_id is not null do nothing
            returning 1
        ) select count(*) into inserted_count from inserted;
    else
        -- 系统转入的已认识字仍沿用根任务路径；冲突时补上其 recognized ID，
        -- 使 ready 回写可以同步到两张主表。
        with source_items as (
            select r.source_literacy_character_id as root_id, r.id as recognized_id,
                   'character'::text as item_type, 0 as item_order,
                   btrim(r.character) as source_text
              from public.recognized_characters r
             where (p_record_id is null or r.id = p_record_id)
            union all
            select r.source_literacy_character_id, r.id, 'word', (w.ordinality - 1)::integer,
                   btrim(case when jsonb_typeof(w.value) = 'string'
                              then w.value #>> '{}'
                              else coalesce(w.value ->> 'text', '') end)
              from public.recognized_characters r
              cross join lateral jsonb_array_elements(coalesce(r.words, '[]'::jsonb))
                   with ordinality as w(value, ordinality)
             where (p_record_id is null or r.id = p_record_id)
            union all
            select r.source_literacy_character_id, r.id, 'sentence', (s.ordinality - 1)::integer,
                   btrim(case when jsonb_typeof(s.value) = 'string'
                              then s.value #>> '{}'
                              else coalesce(s.value ->> 'text', '') end)
              from public.recognized_characters r
              cross join lateral jsonb_array_elements(coalesce(r.sentences, '[]'::jsonb))
                   with ordinality as s(value, ordinality)
             where (p_record_id is null or r.id = p_record_id)
        ), inserted as (
            insert into public.literacy_tts_assets (
                root_literacy_character_id, recognized_character_id,
                item_type, item_order, source_text, source_hash, voice_version, speed
            )
            select root_id, recognized_id, item_type, item_order, source_text,
                   md5(source_text || E'\\x1f' || p_voice_version || E'\\x1f' || p_speed::text),
                   p_voice_version, p_speed
              from source_items
             where source_text <> '' and root_id is not null
            on conflict (root_literacy_character_id, item_type, item_order, voice_version)
                where root_literacy_character_id is not null do update
                set recognized_character_id = coalesce(
                    public.literacy_tts_assets.recognized_character_id,
                    excluded.recognized_character_id
                )
            returning 1
        ) select count(*) into inserted_count from inserted;

        -- 手工/导入的已认识字没有根任务，使用独立的 recognized 路径和唯一索引。
        with source_items as (
            select r.source_literacy_character_id as root_id, r.id as recognized_id,
                   'character'::text as item_type, 0 as item_order,
                   btrim(r.character) as source_text
              from public.recognized_characters r
             where (p_record_id is null or r.id = p_record_id)
            union all
            select r.source_literacy_character_id, r.id, 'word', (w.ordinality - 1)::integer,
                   btrim(case when jsonb_typeof(w.value) = 'string'
                              then w.value #>> '{}'
                              else coalesce(w.value ->> 'text', '') end)
              from public.recognized_characters r
              cross join lateral jsonb_array_elements(coalesce(r.words, '[]'::jsonb))
                   with ordinality as w(value, ordinality)
             where (p_record_id is null or r.id = p_record_id)
            union all
            select r.source_literacy_character_id, r.id, 'sentence', (s.ordinality - 1)::integer,
                   btrim(case when jsonb_typeof(s.value) = 'string'
                              then s.value #>> '{}'
                              else coalesce(s.value ->> 'text', '') end)
              from public.recognized_characters r
              cross join lateral jsonb_array_elements(coalesce(r.sentences, '[]'::jsonb))
                   with ordinality as s(value, ordinality)
             where (p_record_id is null or r.id = p_record_id)
        ), inserted as (
            insert into public.literacy_tts_assets (
                root_literacy_character_id, recognized_character_id,
                item_type, item_order, source_text, source_hash, voice_version, speed
            )
            select root_id, recognized_id, item_type, item_order, source_text,
                   md5(source_text || E'\\x1f' || p_voice_version || E'\\x1f' || p_speed::text),
                   p_voice_version, p_speed
              from source_items
             where source_text <> '' and root_id is null
            on conflict (recognized_character_id, item_type, item_order, voice_version)
                where root_literacy_character_id is null
                  and recognized_character_id is not null do nothing
            returning 1
        ) select inserted_count + count(*) into inserted_count from inserted;
    end if;

    return inserted_count;
end;
$$;

-- FOR UPDATE SKIP LOCKED 让同时运行的定时任务不会重复合成同一条资产。
create or replace function public.claim_literacy_tts_assets(
    p_source text,
    p_record_id uuid,
    p_limit integer,
    p_retry_failed boolean default false,
    p_max_attempts integer default 3
)
returns setof public.literacy_tts_assets
language sql
security definer
set search_path = public
as $$
    with candidates as (
        select a.id
          from public.literacy_tts_assets a
         where (
                    a.status = 'pending'
                 or (p_retry_failed and a.status = 'failed' and a.attempt_count < p_max_attempts)
               )
           and (
                (p_source = 'task' and a.root_literacy_character_id is not null)
             or (p_source = 'recognized'
                 and a.root_literacy_character_id is null
                 and a.recognized_character_id is not null)
           )
           and (
                p_record_id is null
             or (p_source = 'task' and a.root_literacy_character_id = p_record_id)
             or (p_source = 'recognized' and a.recognized_character_id = p_record_id)
           )
         order by a.created_at, a.id
         for update skip locked
         limit greatest(1, least(p_limit, 50))
    )
    update public.literacy_tts_assets a
       set status = 'processing',
           attempt_count = a.attempt_count + 1,
           last_error = null,
           updated_at = now()
      from candidates c
     where a.id = c.id
 returning a.*;
$$;

-- 在调用腾讯云前原子预留当天字符额度。失败重试会再次预留，因为每次请求均可能计费。
create or replace function public.reserve_literacy_tts_characters(
    p_character_count integer,
    p_daily_limit integer
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
    if p_character_count <= 0 or p_daily_limit <= 0 then
        return false;
    end if;

    insert into public.literacy_tts_daily_usage (usage_date, character_count)
    values (current_date, p_character_count)
    on conflict (usage_date) do update
       set character_count = public.literacy_tts_daily_usage.character_count + excluded.character_count,
           updated_at = now()
     where public.literacy_tts_daily_usage.character_count + excluded.character_count <= p_daily_limit;

    return found;
end;
$$;

-- 上传并 HEAD 校验通过后才调用。此函数把资产状态和主表的 URL/版本/哈希一起提交。
create or replace function public.mark_literacy_tts_asset_ready(
    p_asset_id uuid,
    p_object_path text,
    p_audio_url text,
    p_provider_request_id text
)
returns public.literacy_tts_assets
language plpgsql
security definer
set search_path = public
as $$
declare
    asset public.literacy_tts_assets%rowtype;
    metadata jsonb;
begin
    select * into asset
      from public.literacy_tts_assets
     where id = p_asset_id
       and status = 'processing'
     for update;
    if not found then
        raise exception '资产 % 不处于 processing 状态', p_asset_id;
    end if;

    update public.literacy_tts_assets
       set object_path = p_object_path,
           status = 'ready',
           provider_request_id = nullif(btrim(p_provider_request_id), ''),
           last_error = null,
           updated_at = now()
     where id = asset.id
 returning * into asset;

    metadata := jsonb_build_object(
        'audio_url', p_audio_url,
        'audio_version', asset.voice_version,
        'audio_hash', asset.source_hash
    );

    if asset.root_literacy_character_id is not null then
        if asset.item_type = 'character' then
            update public.child_literacy_characters
               set character_audio_url = p_audio_url,
                   character_audio_version = asset.voice_version,
                   character_audio_hash = asset.source_hash
             where id = asset.root_literacy_character_id;
        elsif asset.item_type = 'word' then
            update public.child_literacy_characters
               set words = jsonb_set(words, array[asset.item_order::text],
                   coalesce(words -> asset.item_order, '{}'::jsonb) || metadata, true)
             where id = asset.root_literacy_character_id;
        else
            update public.child_literacy_characters
               set sentences = jsonb_set(sentences, array[asset.item_order::text],
                   coalesce(sentences -> asset.item_order, '{}'::jsonb) || metadata, true)
             where id = asset.root_literacy_character_id;
        end if;
    end if;

    if asset.recognized_character_id is not null then
        if asset.item_type = 'character' then
            update public.recognized_characters
               set character_audio_url = p_audio_url,
                   character_audio_version = asset.voice_version,
                   character_audio_hash = asset.source_hash
             where id = asset.recognized_character_id;
        elsif asset.item_type = 'word' then
            update public.recognized_characters
               set words = jsonb_set(words, array[asset.item_order::text],
                   coalesce(words -> asset.item_order, '{}'::jsonb) || metadata, true)
             where id = asset.recognized_character_id;
        else
            update public.recognized_characters
               set sentences = jsonb_set(sentences, array[asset.item_order::text],
                   coalesce(sentences -> asset.item_order, '{}'::jsonb) || metadata, true)
             where id = asset.recognized_character_id;
        end if;
    end if;

    return asset;
end;
$$;

-- 当每日硬额度耗尽时，把刚抢占的资产放回 pending，不把“没有调用 TTS”记为失败尝试。
create or replace function public.defer_literacy_tts_asset(
    p_asset_id uuid,
    p_reason text
)
returns void
language sql
security definer
set search_path = public
as $$
    update public.literacy_tts_assets
       set status = 'pending',
           attempt_count = greatest(attempt_count - 1, 0),
           last_error = left(coalesce(p_reason, ''), 1000),
           updated_at = now()
     where id = p_asset_id
       and status = 'processing';
$$;

revoke all on table public.literacy_tts_daily_usage from anon, authenticated;
revoke all on function public.enqueue_literacy_tts_assets(text, uuid, text, smallint) from public;
revoke all on function public.claim_literacy_tts_assets(text, uuid, integer, boolean, integer) from public;
revoke all on function public.reserve_literacy_tts_characters(integer, integer) from public;
revoke all on function public.mark_literacy_tts_asset_ready(uuid, text, text, text) from public;
revoke all on function public.defer_literacy_tts_asset(uuid, text) from public;

-- Supabase 项目可能通过默认权限显式给 anon/authenticated 授予了 EXECUTE；
-- 仅 REVOKE FROM public 不足以覆盖这种既有授权，必须逐角色撤销。
revoke all on function public.enqueue_literacy_tts_assets(text, uuid, text, smallint) from anon, authenticated;
revoke all on function public.claim_literacy_tts_assets(text, uuid, integer, boolean, integer) from anon, authenticated;
revoke all on function public.reserve_literacy_tts_characters(integer, integer) from anon, authenticated;
revoke all on function public.mark_literacy_tts_asset_ready(uuid, text, text, text) from anon, authenticated;
revoke all on function public.defer_literacy_tts_asset(uuid, text) from anon, authenticated;

grant execute on function public.enqueue_literacy_tts_assets(text, uuid, text, smallint) to service_role;
grant execute on function public.claim_literacy_tts_assets(text, uuid, integer, boolean, integer) to service_role;
grant execute on function public.reserve_literacy_tts_characters(integer, integer) to service_role;
grant execute on function public.mark_literacy_tts_asset_ready(uuid, text, text, text) to service_role;
grant execute on function public.defer_literacy_tts_asset(uuid, text) to service_role;

-- 部署后验证：不得出现 anon 或 authenticated；postgres 是数据库所有者，
-- service_role 是生成 SCF 使用的唯一非所有者调用方。
-- select routine_name, grantee, privilege_type
-- from information_schema.role_routine_grants
-- where routine_schema = 'public'
--   and routine_name in (
--       'enqueue_literacy_tts_assets', 'claim_literacy_tts_assets',
--       'reserve_literacy_tts_characters', 'mark_literacy_tts_asset_ready',
--       'defer_literacy_tts_asset'
--   )
-- order by routine_name, grantee;
