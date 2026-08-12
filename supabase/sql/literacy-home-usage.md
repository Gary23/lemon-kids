# 认字：首页数据操作与常用 SQL

本文只记录数据库字段、人工查询和运维操作。当前学习、首页快照与完成流转规则以 [学习与数据规则](../../kid-literacy-app/docs/LEARNING-RULES.md) 为准；评测接口的写入边界见 [评测云函数说明](../../cloud-functions/evaluate-reading/README.md)。

## 首页依赖字段

```sql
alter table public.child_literacy_characters
    add column if not exists learned_at timestamptz;
```

`learned_at` 由服务端完成任务时首次写入。客户端不直接写入学习状态。

## 点击正确发音帮助

首次点击插入记录，之后点击只增加次数并更新时间。将 UUID 占位符替换为实际值。

```sql
insert into public.child_literacy_helped_characters (
    child_id,
    literacy_character_id
)
values (
    '孩子 UUID'::uuid,
    '汉字记录 UUID'::uuid
)
on conflict (child_id, literacy_character_id)
do update set
    help_count = child_literacy_helped_characters.help_count + 1,
    last_helped_at = now();
```

查询孩子点过帮助、可能不熟练的字：

```sql
select
    character.character,
    helped.help_count,
    helped.last_helped_at
from public.child_literacy_helped_characters helped
join public.child_literacy_characters character
    on character.id = helped.literacy_character_id
where helped.child_id = '孩子 UUID'::uuid
order by helped.help_count desc, helped.last_helped_at desc;
```

## 排查首页数据

查看某个孩子仍待学习的主字：

```sql
select id, character, learned_at
from public.child_literacy_characters
where child_id = '孩子 UUID'::uuid
  and learned_at is null
order by created_at asc, id asc;
```

查看已认识字的收录记录：

```sql
select character, recognized_at
from public.recognized_characters
where child_id = '孩子 UUID'::uuid
order by recognized_at desc, character asc;
```
