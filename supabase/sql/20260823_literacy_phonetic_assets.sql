-- 认字词句腾讯数字拼音资产。
-- 前置：已执行 child_literacy_characters、recognized_characters 及其音频资产迁移。
-- 本脚本由服务端 service_role 调用；不要向 anon、authenticated 开放资产写权限。

create table if not exists public.literacy_phonetic_assets (
    id uuid primary key default gen_random_uuid(),
    content_source text not null check (content_source in ('pending', 'recognized')),
    -- 根据 content_source 指向 child_literacy_characters 或 recognized_characters；无法用单一 FK
    -- 表达这一互斥关系，因此所有服务端读取均会同时校验来源主表和 item_text。
    literacy_character_id uuid not null,
    item_type text not null check (item_type in ('word', 'sentence')),
    item_index integer not null check (item_index >= 0),
    item_text text not null check (char_length(btrim(item_text)) > 0),
    phoneme_tokens jsonb,
    status text not null default 'pending'
        check (status in ('pending', 'processing', 'ready', 'failed')),
    attempt_count integer not null default 0 check (attempt_count >= 0),
    last_error text,
    generator_version text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (content_source, literacy_character_id, item_type, item_index),
    check (
        (status <> 'ready' and phoneme_tokens is null)
        or (status = 'ready' and jsonb_typeof(phoneme_tokens) = 'array')
    )
);

create index if not exists literacy_phonetic_assets_claim_idx
    on public.literacy_phonetic_assets (status, updated_at, id)
    where status in ('pending', 'failed');
create index if not exists literacy_phonetic_assets_owner_idx
    on public.literacy_phonetic_assets (content_source, literacy_character_id);

alter table public.literacy_phonetic_assets enable row level security;

-- 将待认识任务和资产一次性写入，固定 JSON 数组下标就是资产 item_index。
create or replace function public.create_literacy_tasks_with_phonetic_assets(
    p_child_id uuid,
    p_family_id uuid,
    p_rows jsonb
)
returns table (id uuid, "character" text)
language plpgsql
security definer
set search_path = public
as $$
declare
    row_value jsonb;
    created public.child_literacy_characters%rowtype;
    item jsonb;
    item_index integer;
begin
    if jsonb_typeof(p_rows) <> 'array' then raise exception 'p_rows 必须为数组'; end if;
    for row_value in select value from jsonb_array_elements(p_rows)
    loop
        insert into public.child_literacy_characters (
            family_id, child_id, character, words, sentences, sort_order
        ) values (
            p_family_id, p_child_id, row_value->>'character',
            coalesce(row_value->'words', '[]'::jsonb),
            coalesce(row_value->'sentences', '[]'::jsonb),
            coalesce((row_value->>'sort_order')::integer, 0)
        ) returning * into created;

        for item, item_index in
            select value, ordinality - 1
              from jsonb_array_elements(coalesce(created.words, '[]'::jsonb)) with ordinality
        loop
            insert into public.literacy_phonetic_assets (
                content_source, literacy_character_id, item_type, item_index, item_text
            ) values ('pending', created.id, 'word', item_index, btrim(item->>'text'));
        end loop;
        for item, item_index in
            select value, ordinality - 1
              from jsonb_array_elements(coalesce(created.sentences, '[]'::jsonb)) with ordinality
        loop
            insert into public.literacy_phonetic_assets (
                content_source, literacy_character_id, item_type, item_index, item_text
            ) values ('pending', created.id, 'sentence', item_index, btrim(item->>'text'));
        end loop;
        id := created.id;
        "character" := created.character;
        return next;
    end loop;
end;
$$;

create or replace function public.claim_literacy_phonetic_assets(p_limit integer default 50)
returns setof public.literacy_phonetic_assets
language sql
security definer
set search_path = public
as $$
    with candidates as (
        select id
          from public.literacy_phonetic_assets
         where status = 'pending'
            or (status = 'failed' and attempt_count < 3 and updated_at < now() - interval '5 minutes')
         order by created_at, id
         for update skip locked
         limit greatest(1, least(p_limit, 50))
    )
    update public.literacy_phonetic_assets a
       set status = 'processing', attempt_count = attempt_count + 1, updated_at = now()
      from candidates c
     where a.id = c.id
 returning a.*;
$$;

create or replace function public.complete_literacy_phonetic_asset(
    p_asset_id uuid, p_phoneme_tokens jsonb, p_generator_version text
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if jsonb_typeof(p_phoneme_tokens) <> 'array' then raise exception 'phoneme_tokens 必须为数组'; end if;
    update public.literacy_phonetic_assets
       set phoneme_tokens = p_phoneme_tokens, status = 'ready', last_error = null,
           generator_version = nullif(btrim(p_generator_version), ''), updated_at = now()
     where id = p_asset_id and status = 'processing';
    if not found then raise exception '音素资产不存在或未被领取'; end if;
end;
$$;

create or replace function public.fail_literacy_phonetic_asset(p_asset_id uuid, p_reason text)
returns void
language sql
security definer
set search_path = public
as $$
    update public.literacy_phonetic_assets
       set status = 'failed', phoneme_tokens = null, last_error = left(coalesce(p_reason, ''), 1000), updated_at = now()
     where id = p_asset_id and status = 'processing';
$$;

-- 待认识字收录为已认识字时完整保留已就绪或待重试资产，不重新生成或覆盖人工修正。
create or replace function public.migrate_literacy_phonetic_assets(
    p_pending_character_id uuid, p_recognized_character_id uuid
)
returns void
language sql
security definer
set search_path = public
as $$
    update public.literacy_phonetic_assets
       set content_source = 'recognized', literacy_character_id = p_recognized_character_id, updated_at = now()
     where content_source = 'pending' and literacy_character_id = p_pending_character_id;
$$;

create or replace function public.delete_literacy_phonetic_assets(
    p_content_source text, p_literacy_character_id uuid
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if p_content_source not in ('pending', 'recognized') then raise exception '来源不正确'; end if;
    delete from public.literacy_phonetic_assets
     where content_source = p_content_source and literacy_character_id = p_literacy_character_id;
end;
$$;

-- 历史数据一次性回填队列。已存在同一索引资产时保留其人工或已完成结果。
insert into public.literacy_phonetic_assets (
    content_source, literacy_character_id, item_type, item_index, item_text
)
select 'pending', c.id, 'word', item.ordinality - 1, btrim(item.value->>'text')
  from public.child_literacy_characters c
 cross join lateral jsonb_array_elements(coalesce(c.words, '[]'::jsonb)) with ordinality as item(value, ordinality)
 where btrim(coalesce(item.value->>'text', '')) <> ''
on conflict (content_source, literacy_character_id, item_type, item_index) do nothing;

insert into public.literacy_phonetic_assets (
    content_source, literacy_character_id, item_type, item_index, item_text
)
select 'pending', c.id, 'sentence', item.ordinality - 1, btrim(item.value->>'text')
  from public.child_literacy_characters c
 cross join lateral jsonb_array_elements(coalesce(c.sentences, '[]'::jsonb)) with ordinality as item(value, ordinality)
 where btrim(coalesce(item.value->>'text', '')) <> ''
on conflict (content_source, literacy_character_id, item_type, item_index) do nothing;

insert into public.literacy_phonetic_assets (
    content_source, literacy_character_id, item_type, item_index, item_text
)
select 'recognized', c.id, 'word', item.ordinality - 1, btrim(item.value->>'text')
  from public.recognized_characters c
 cross join lateral jsonb_array_elements(coalesce(c.words, '[]'::jsonb)) with ordinality as item(value, ordinality)
 where btrim(coalesce(item.value->>'text', '')) <> ''
on conflict (content_source, literacy_character_id, item_type, item_index) do nothing;

insert into public.literacy_phonetic_assets (
    content_source, literacy_character_id, item_type, item_index, item_text
)
select 'recognized', c.id, 'sentence', item.ordinality - 1, btrim(item.value->>'text')
  from public.recognized_characters c
 cross join lateral jsonb_array_elements(coalesce(c.sentences, '[]'::jsonb)) with ordinality as item(value, ordinality)
 where btrim(coalesce(item.value->>'text', '')) <> ''
on conflict (content_source, literacy_character_id, item_type, item_index) do nothing;

revoke all on table public.literacy_phonetic_assets from public, anon, authenticated;
revoke all on function public.create_literacy_tasks_with_phonetic_assets(uuid, uuid, jsonb) from public, anon, authenticated;
revoke all on function public.claim_literacy_phonetic_assets(integer) from public, anon, authenticated;
revoke all on function public.complete_literacy_phonetic_asset(uuid, jsonb, text) from public, anon, authenticated;
revoke all on function public.fail_literacy_phonetic_asset(uuid, text) from public, anon, authenticated;
revoke all on function public.migrate_literacy_phonetic_assets(uuid, uuid) from public, anon, authenticated;
revoke all on function public.delete_literacy_phonetic_assets(text, uuid) from public, anon, authenticated;
grant execute on function public.create_literacy_tasks_with_phonetic_assets(uuid, uuid, jsonb) to service_role;
grant execute on function public.claim_literacy_phonetic_assets(integer) to service_role;
grant execute on function public.complete_literacy_phonetic_asset(uuid, jsonb, text) to service_role;
grant execute on function public.fail_literacy_phonetic_asset(uuid, text) to service_role;
grant execute on function public.migrate_literacy_phonetic_assets(uuid, uuid) to service_role;
grant execute on function public.delete_literacy_phonetic_assets(text, uuid) to service_role;
