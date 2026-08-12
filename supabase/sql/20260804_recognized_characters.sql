-- 正式的“已认识的字”表。
-- 它独立于 known_characters（字库）和 child_literacy_characters（认字任务），
-- 因此不会因认字任务的创建、完成或删除而改变。
-- 在 Supabase Dashboard -> SQL Editor 人工审查并执行。

create table if not exists public.recognized_characters (
    id uuid primary key default gen_random_uuid(),
    family_id uuid not null,
    child_id uuid not null references auth.users(id) on delete cascade,
    -- 单个汉字；同一孩子不能重复收录同一个字。
    character text not null check (char_length(btrim(character)) = 1),
    -- 进入认字页时展示的教学内容，格式与认字任务保持一致：
    -- [{"text":"日光","audio_url":""}]。
    words jsonb not null default '[]'::jsonb check (jsonb_typeof(words) = 'array'),
    sentences jsonb not null default '[]'::jsonb check (jsonb_typeof(sentences) = 'array'),
    -- 首次确认“已认识”的业务时间，也是首页最近收录的排序依据。
    recognized_at timestamptz not null default now(),
    -- 数据来历便于后续筛选或清理临时数据；当前允许手工录入、批量导入和系统写入。
    source text not null default 'manual'
        check (source in ('manual', 'import', 'system')),
    -- 可选备注，例如“演示数据”；不参与孩子端展示。
    note text not null default '',
    created_at timestamptz not null default now(),
    unique (child_id, character)
);

create index if not exists recognized_characters_child_recognized_idx
    on public.recognized_characters (child_id, recognized_at desc);

alter table public.recognized_characters enable row level security;

-- 孩子端只读取自己的已认识字；新增、修改、删除仅由受信任的家长端或后台执行。
drop policy if exists "child reads own recognized characters" on public.recognized_characters;
create policy "child reads own recognized characters" on public.recognized_characters
for select using (child_id = auth.uid());

-- 清理手工演示数据示例（将 UUID 替换为实际孩子 ID）：
-- delete from public.recognized_characters
-- where child_id = '孩子 UUID'::uuid and source = 'manual';
