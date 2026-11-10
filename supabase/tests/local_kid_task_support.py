"""Shared helpers for local kid-task preflight and isolated verification."""
import hashlib
import json
import re
from verify_local_parent_business import ROOT, SOURCE, psql

SQL = ROOT / 'supabase/sql/20261030_local_kid_task_support.sql'


def snapshot_all(name):
    tables = json.loads(psql(name, "select json_agg(tablename order by tablename) from pg_tables where schemaname='public';"))
    queries = []
    for table in tables:
        assert re.fullmatch(r'[a-z_]+', table)
        queries.append(f"select '{table}' table_name, coalesce(jsonb_agg(to_jsonb(q) order by to_jsonb(q)::text),'[]') rows from public.{table} q")
    data = json.loads(psql(name, 'select jsonb_object_agg(table_name,rows) from (' + ' union all '.join(queries) + ') snapshots;'))
    return {table: {'count': len(rows), 'sha256': hashlib.sha256(
        json.dumps(rows, sort_keys=True, separators=(',', ':')).encode()).hexdigest()} for table, rows in data.items()}


def migration_bundle():
    sql = re.sub(r'^\s*(begin|commit);\s*$', '', SQL.read_text(), flags=re.M | re.I)
    # Snapshot and compare every public business table under the same write locks.
    # All snapshot data stays inside this transaction and is dropped on commit.
    guard = r'''
do $$ declare t text; v_rows jsonb;
begin
 for t in select tablename from pg_tables where schemaname='public' order by tablename loop
  execute format('lock table public.%I in share row exclusive mode',t);
  execute format('select coalesce(jsonb_agg(to_jsonb(q) order by to_jsonb(q)::text),''[]''::jsonb) from public.%I q',t) into v_rows;
  insert into kid_task_original_rows values(t,v_rows);
 end loop;
end $$;
'''
    verify = r'''
do $$ declare old record; v_rows jsonb;
begin
 for old in select * from kid_task_original_rows loop
  execute format('select coalesce(jsonb_agg(to_jsonb(q) order by to_jsonb(q)::text),''[]''::jsonb) from public.%I q',old.table_name) into v_rows;
  if v_rows is distinct from old.original_rows then
   raise exception 'Kid task migration changed business data';
  end if;
 end loop;
end $$;
'''
    return ("begin; set local lock_timeout='5s'; set local statement_timeout='30s'; "
            "set local app.lemon_local_kid_task_target='lemon-kids-local'; "
            "create temporary table kid_task_original_rows(table_name text, original_rows jsonb) on commit drop;\n"
            + guard + sql + verify + '\ncommit;')


def preflight(name=SOURCE):
    return psql(name, """begin read only;
select jsonb_build_object(
 'growth_exists',to_regprocedure('public.growth_snapshot()') is not null,
 'onboarding_exists',to_regprocedure('public.create_local_parent_profile(text)') is not null,
 'public_tables',(select count(*) from pg_tables where schemaname='public'),
 'reward_bucket_private',exists(select 1 from storage.buckets where id='reward-images' and not public),
 'own_usage_delete_policy',exists(select 1 from pg_policies where schemaname='public'
      and tablename='app_usage' and policyname='local_kid_app_usage_delete')
); commit;""")
