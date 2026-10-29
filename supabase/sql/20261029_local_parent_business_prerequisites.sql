-- 仅限本地；以 supabase_admin 安装扩展，业务迁移仍以 postgres 执行。
-- 必须与 baseline 使用相同的显式本地目标确认。
begin;
do $$ begin
 if current_setting('app.lemon_local_business_target',true) is distinct from 'lemon-kids-local'
 or not exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.proname='create_local_parent_profile') then
  raise exception 'Explicit local parent target required';
 end if;
end $$;
create schema if not exists extensions;
create extension if not exists pg_cron with schema extensions;
grant usage on schema cron to postgres;
grant execute on function cron.schedule(text,text,text) to postgres;
commit;
