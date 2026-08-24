-- 物理清理词句 JSON 中已废弃的无调拼音字段 pinyins。
--
-- 不可逆前置条件：所有旧版认字客户端均已被卸载、强制升级或服务端阻断；
-- 当前版本已使用 literacy_phonetic_assets 进行 TEXT_MODE=1 评测，且历史资产已回填完成。
-- 本迁移只删除每个词句对象的 pinyins 键，保留 text、audio_url 及其他业务字段。

begin;

do $$
begin
    -- 遇到非数组或非对象条目时中止，避免把异常历史数据静默改写为另一种结构。
    if exists (
        select 1
          from (
              select words as items from public.child_literacy_characters
              union all select sentences from public.child_literacy_characters
              union all select words from public.recognized_characters
              union all select sentences from public.recognized_characters
          ) source
         where items is not null
           and jsonb_typeof(items) <> 'array'
    ) then
        raise exception '词句 JSON 必须为数组；请先修复异常数据再清理 pinyins';
    end if;

    if exists (
        select 1
          from (
              select words as items from public.child_literacy_characters
              union all select sentences from public.child_literacy_characters
              union all select words from public.recognized_characters
              union all select sentences from public.recognized_characters
          ) source
         cross join lateral jsonb_array_elements(coalesce(source.items, '[]'::jsonb)) as item(value)
         where jsonb_typeof(item.value) <> 'object'
    ) then
        raise exception '词句 JSON 数组条目必须为对象；请先修复异常数据再清理 pinyins';
    end if;
end;
$$;

update public.child_literacy_characters c
   set words = coalesce((
           select jsonb_agg(item.value - 'pinyins' order by item.ordinality)
             from jsonb_array_elements(coalesce(c.words, '[]'::jsonb)) with ordinality as item(value, ordinality)
       ), '[]'::jsonb),
       sentences = coalesce((
           select jsonb_agg(item.value - 'pinyins' order by item.ordinality)
             from jsonb_array_elements(coalesce(c.sentences, '[]'::jsonb)) with ordinality as item(value, ordinality)
       ), '[]'::jsonb)
 where exists (
           select 1 from jsonb_array_elements(coalesce(c.words, '[]'::jsonb)) as item(value)
            where item.value ? 'pinyins'
       )
    or exists (
           select 1 from jsonb_array_elements(coalesce(c.sentences, '[]'::jsonb)) as item(value)
            where item.value ? 'pinyins'
       );

update public.recognized_characters c
   set words = coalesce((
           select jsonb_agg(item.value - 'pinyins' order by item.ordinality)
             from jsonb_array_elements(coalesce(c.words, '[]'::jsonb)) with ordinality as item(value, ordinality)
       ), '[]'::jsonb),
       sentences = coalesce((
           select jsonb_agg(item.value - 'pinyins' order by item.ordinality)
             from jsonb_array_elements(coalesce(c.sentences, '[]'::jsonb)) with ordinality as item(value, ordinality)
       ), '[]'::jsonb)
 where exists (
           select 1 from jsonb_array_elements(coalesce(c.words, '[]'::jsonb)) as item(value)
            where item.value ? 'pinyins'
       )
    or exists (
           select 1 from jsonb_array_elements(coalesce(c.sentences, '[]'::jsonb)) as item(value)
            where item.value ? 'pinyins'
       );

comment on column public.child_literacy_characters.words is
    '词语 JSON 数组；每项含 text 及可选音频字段';
comment on column public.child_literacy_characters.sentences is
    '句子 JSON 数组；每项含 text 及可选音频字段';
comment on column public.recognized_characters.words is
    '词语 JSON 数组；每项含 text 及可选音频字段';
comment on column public.recognized_characters.sentences is
    '句子 JSON 数组；每项含 text 及可选音频字段';

commit;

-- 部署后核验：四项均应为 0。
select
    'pending_words' as source,
    count(*) filter (where item.value ? 'pinyins') as remaining_pinyins
  from public.child_literacy_characters c
 cross join lateral jsonb_array_elements(coalesce(c.words, '[]'::jsonb)) as item(value)
union all
select
    'pending_sentences',
    count(*) filter (where item.value ? 'pinyins')
  from public.child_literacy_characters c
 cross join lateral jsonb_array_elements(coalesce(c.sentences, '[]'::jsonb)) as item(value)
union all
select
    'recognized_words',
    count(*) filter (where item.value ? 'pinyins')
  from public.recognized_characters c
 cross join lateral jsonb_array_elements(coalesce(c.words, '[]'::jsonb)) as item(value)
union all
select
    'recognized_sentences',
    count(*) filter (where item.value ? 'pinyins')
  from public.recognized_characters c
 cross join lateral jsonb_array_elements(coalesce(c.sentences, '[]'::jsonb)) as item(value);
