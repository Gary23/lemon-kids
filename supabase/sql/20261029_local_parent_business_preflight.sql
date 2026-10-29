-- 只读预检，不输出账号、令牌、连接串或业务内容。
begin read only;
select current_database() as database, current_user as role;
select tablename from pg_tables where schemaname='public' order by tablename;
select 'families' as table_name,count(*) from public.families
union all select 'users',count(*) from public.users
union all select 'binding_codes',count(*) from public.binding_codes
union all select 'tasks',count(*) from public.tasks;
select count(*) as source_template_references from public.tasks where source_template_id is not null;
select count(*) as source_category_references from public.tasks where source_category_id is not null;
select count(*) as duplicate_active_template_tasks from (
 select child_id,due_date,source_template_id from public.tasks
 where deleted_at is null and source_template_id is not null
 group by child_id,due_date,source_template_id having count(*)>1
) q;
select extname,extversion from pg_extension where extname='pg_cron';
select rolname,rolsuper from pg_roles where rolname in ('postgres','supabase_admin');
select count(*) as local_onboarding_marker from pg_proc p join pg_namespace n on n.oid=p.pronamespace
 where n.nspname='public' and p.proname='create_local_parent_profile';
commit;
