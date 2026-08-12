-- 允许孩子在“帮助过的内容”中删除自己的单条求助记录。
-- 依赖：已执行 20260801_literacy_help_requests.sql。

drop policy if exists "child deletes own literacy help requests"
    on public.child_literacy_character_help_requests;

create policy "child deletes own literacy help requests"
    on public.child_literacy_character_help_requests
    for delete
    using (child_id = auth.uid());

-- 验证：以孩子账号登录后，只能删除 child_id 为自己的记录。
