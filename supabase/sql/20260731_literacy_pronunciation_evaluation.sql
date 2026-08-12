-- 认字：词句内容与口语评测记录
-- 在 Supabase Dashboard -> SQL Editor 执行。
-- 前置条件：public.child_literacy_characters 已存在，且 words / sentences 为 jsonb 数组。
--
-- 约定：character_index 从 0 开始，只计入汉字，不计标点；它用于将腾讯的逐字结果映射回 UI。
--       一个“词”评测可将同一识字任务的多个词合并为一次接口调用；一个“句”评测
--       对应一个句子一次调用。attempt_items 保存本次调用实际包含的词/句。

create table if not exists public.child_literacy_examples (
    id uuid primary key default gen_random_uuid(),
    literacy_character_id uuid not null references public.child_literacy_characters(id) on delete cascade,
    example_type text not null check (example_type in ('word', 'sentence')),
    text text not null check (char_length(btrim(text)) > 0),
    audio_url text not null default '',
    sort_order integer not null default 0 check (sort_order >= 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (literacy_character_id, example_type, sort_order)
);

create index if not exists child_literacy_examples_character_type_idx
    on public.child_literacy_examples (literacy_character_id, example_type, sort_order);

-- 一次点击“开始”到“结束”的评测请求。默认不保存音频；audio_url 仅为未来经监护人同意
-- 后保留样本用于调参时预留，正常评测请保持为 NULL。
create table if not exists public.child_literacy_reading_attempts (
    id uuid primary key default gen_random_uuid(),
    family_id uuid not null,
    child_id uuid not null references auth.users(id) on delete cascade,
    literacy_character_id uuid not null references public.child_literacy_characters(id) on delete cascade,
    target_type text not null check (target_type in ('character', 'word_group', 'sentence')),
    target_text text not null,
    -- 实际上送腾讯云的文本，用于审计和复现。
    evaluation_text text not null,
    provider text not null default 'tencent_soel' check (provider in ('tencent_soel')),
    provider_request_id text,
    status text not null default 'completed'
        check (status in ('completed', 'failed', 'cancelled')),
    transcript text,
    overall_pron_accuracy numeric(5,2),
    overall_pron_fluency numeric(5,2),
    -- 保存本次采用的判定规则，后续调整阈值不会改写历史“对/错”。
    pron_accuracy_threshold numeric(5,2) not null default 70.00
        check (pron_accuracy_threshold between 0 and 100),
    require_tone_match boolean not null default true,
    audio_duration_ms integer check (audio_duration_ms is null or audio_duration_ms >= 0),
    audio_url text,
    raw_response jsonb,
    created_at timestamptz not null default now()
);

create index if not exists child_literacy_reading_attempts_child_created_idx
    on public.child_literacy_reading_attempts (child_id, created_at desc);
create index if not exists child_literacy_reading_attempts_character_created_idx
    on public.child_literacy_reading_attempts (literacy_character_id, created_at desc);

-- 一次腾讯请求中实际包含的评测条目：
-- - character：一行，example_id 为 NULL，target_text 使用主字数据；
-- - word_group：三行（或实际存在的词数），每行对应一个词；
-- - sentence：一行，对应一个句子。
-- target_character_start 是该条目在整次请求“汉字序列”中的起始下标，可稳定映射
-- 腾讯返回的 Words 结果。三个词合并调用时，三个条目的下标连续。
create table if not exists public.child_literacy_reading_attempt_items (
    id uuid primary key default gen_random_uuid(),
    attempt_id uuid not null references public.child_literacy_reading_attempts(id) on delete cascade,
    example_id uuid references public.child_literacy_examples(id) on delete set null,
    item_order integer not null check (item_order >= 0),
    target_text text not null,
    target_character_start integer not null default 0 check (target_character_start >= 0),
    target_character_count integer not null check (target_character_count > 0),
    created_at timestamptz not null default now(),
    unique (attempt_id, item_order),
    unique (attempt_id, target_character_start)
);

create index if not exists child_literacy_reading_attempt_items_attempt_idx
    on public.child_literacy_reading_attempt_items (attempt_id, item_order);

-- 一次评测中每个目标汉字的最终判定。is_correct 必须由服务端按 MatchTag、发音分、
-- 声调结果和可配置阈值写入，客户端不能自行伪造。
create table if not exists public.child_literacy_reading_character_results (
    id uuid primary key default gen_random_uuid(),
    attempt_id uuid not null references public.child_literacy_reading_attempts(id) on delete cascade,
    -- 指向该汉字所属的词/句条目；单字评测也会有一个 attempt_item。
    attempt_item_id uuid not null references public.child_literacy_reading_attempt_items(id) on delete cascade,
    -- 在条目中的汉字下标。
    character_index integer not null check (character_index >= 0),
    expected_character text not null check (char_length(expected_character) = 1),
    match_tag text,
    pron_accuracy numeric(5,2),
    pron_fluency numeric(5,2),
    tone_accuracy numeric(5,2),
    tone_matched boolean,
    is_correct boolean not null,
    created_at timestamptz not null default now(),
    unique (attempt_item_id, character_index)
);

create index if not exists child_literacy_reading_character_results_attempt_idx
    on public.child_literacy_reading_character_results (attempt_id, attempt_item_id, character_index);

-- 将现有 child_literacy_characters.words / sentences 的 JSONB 数据迁入新表。
-- 可以重复执行；已存在的 (汉字、类型、顺序) 会更新文本与音频 URL。
insert into public.child_literacy_examples (
    literacy_character_id, example_type, text, audio_url, sort_order
)
select
    character_row.id,
    'word',
    item.value ->> 'text',
    coalesce(item.value ->> 'audio_url', ''),
    item.ordinality - 1
from public.child_literacy_characters character_row
cross join lateral jsonb_array_elements(coalesce(character_row.words, '[]'::jsonb)) with ordinality as item(value, ordinality)
where coalesce(item.value ->> 'text', '') <> ''
on conflict (literacy_character_id, example_type, sort_order)
do update set
    text = excluded.text,
    audio_url = excluded.audio_url,
    updated_at = now();

insert into public.child_literacy_examples (
    literacy_character_id, example_type, text, audio_url, sort_order
)
select
    character_row.id,
    'sentence',
    item.value ->> 'text',
    coalesce(item.value ->> 'audio_url', ''),
    item.ordinality - 1
from public.child_literacy_characters character_row
cross join lateral jsonb_array_elements(coalesce(character_row.sentences, '[]'::jsonb)) with ordinality as item(value, ordinality)
where coalesce(item.value ->> 'text', '') <> ''
on conflict (literacy_character_id, example_type, sort_order)
do update set
    text = excluded.text,
    audio_url = excluded.audio_url,
    updated_at = now();

alter table public.child_literacy_examples enable row level security;
alter table public.child_literacy_reading_attempts enable row level security;
alter table public.child_literacy_reading_attempt_items enable row level security;
alter table public.child_literacy_reading_character_results enable row level security;

-- 孩子仅能读取自己任务关联的内容与自己的评测记录；评测写入建议由可信后端
-- 使用 service_role 完成，因此这里不开放客户端 insert/update，避免伪造“读对”记录。
drop policy if exists "child reads own literacy examples" on public.child_literacy_examples;
create policy "child reads own literacy examples" on public.child_literacy_examples
for select using (
    exists (
        select 1 from public.child_literacy_characters c
        where c.id = literacy_character_id and c.child_id = auth.uid()
    )
);

drop policy if exists "child reads own literacy reading attempts" on public.child_literacy_reading_attempts;
create policy "child reads own literacy reading attempts" on public.child_literacy_reading_attempts
for select using (child_id = auth.uid());

drop policy if exists "child reads own literacy reading attempt items" on public.child_literacy_reading_attempt_items;
create policy "child reads own literacy reading attempt items" on public.child_literacy_reading_attempt_items
for select using (
    exists (
        select 1 from public.child_literacy_reading_attempts a
        where a.id = attempt_id and a.child_id = auth.uid()
    )
);

drop policy if exists "child reads own literacy reading results" on public.child_literacy_reading_character_results;
create policy "child reads own literacy reading results" on public.child_literacy_reading_character_results
for select using (
    exists (
        select 1 from public.child_literacy_reading_attempts a
        where a.id = attempt_id and a.child_id = auth.uid()
    )
);
