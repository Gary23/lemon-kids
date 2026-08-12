-- 将“求助事件”收敛为“孩子点击过的字”集合：同一孩子、同一个汉字只保留一行。
-- 先执行本脚本，再部署新版 evaluate-reading 云函数。

delete from public.child_literacy_character_help_requests
where id in (
    select id
    from (
        select
            id,
            row_number() over (
                partition by child_id, requested_character
                order by created_at asc, id asc
            ) as row_number
        from public.child_literacy_character_help_requests
    ) duplicated_rows
    where row_number > 1
);

alter table public.child_literacy_character_help_requests
    drop constraint if exists child_literacy_help_requests_child_character_key;

alter table public.child_literacy_character_help_requests
    add constraint child_literacy_help_requests_child_character_key
    unique (child_id, requested_character);

-- 验证：该查询不应返回任何行。
-- select child_id, requested_character, count(*)
-- from public.child_literacy_character_help_requests
-- group by child_id, requested_character
-- having count(*) > 1;
