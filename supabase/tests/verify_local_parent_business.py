#!/usr/bin/env python3
"""在一次性 PostgreSQL/Auth/PostgREST 容器验证本地家长业务迁移；从不迁移源库。"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.parse
import urllib.request
import urllib.error
import uuid

ROOT = Path(__file__).resolve().parents[2]
DOCKER = '/Volumes/LemonKidsContainer/bin/docker'
ENV = dict(os.environ, DOCKER_HOST='unix:///Volumes/LemonKidsContainer/colima/default/docker.sock',
           DOCKER_CONFIG='/Volumes/LemonKidsContainer/docker-config')
SOURCE = 'supabase_db_lemon-kids-local'
SQL = ROOT / 'supabase/sql/20261029_local_parent_business_baseline.sql'

def docker(*args, data=None, check=True):
    p = subprocess.run([DOCKER, *args], env=ENV, input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if check and p.returncode:
        # 不输出环境变量、连接串、认证响应或数据库转储。
        raise RuntimeError('Docker command failed: ' + ' '.join(args[:2]) + '\n' + re.sub(r"PASSWORD '[^']*'", "PASSWORD '[redacted]'", p.stderr.decode(errors='replace'))[-2500:])
    return p.stdout

def info(name):
    return json.loads(docker('inspect', name))[0]

def psql(name, query, user='postgres'):
    return docker('exec', '-i', name, 'psql', '-X', '-v', 'ON_ERROR_STOP=1', '-U', user, '-d', 'postgres', '-At', data=query.encode()).decode()

def snapshot(name):
    # 仅比较原核心表的原有字段；新增列不应影响数据保留判断。
    columns = json.loads(psql(name, "select json_object_agg(table_name,cols) from (select table_name,array_agg(column_name order by ordinal_position) cols from information_schema.columns where table_schema='public' and table_name in ('families','users','binding_codes','tasks') group by table_name) q;"))
    result = {}
    for table, cols in columns.items():
        projection = ','.join('"'+c+'"' for c in cols)
        rows = json.loads(psql(name, f"select coalesce(jsonb_agg(to_jsonb(q) order by to_jsonb(q)::text),'[]') from (select {projection} from public.{table}) q;"))
        result[table] = {'columns': cols, 'count': len(rows), 'sha256': hashlib.sha256(json.dumps(rows, sort_keys=True).encode()).hexdigest()}
    return result

def compare_snapshot(name, baseline):
    for table, old in baseline.items():
        cols = ','.join('"'+c+'"' for c in old['columns'])
        rows = json.loads(psql(name, f"select coalesce(jsonb_agg(to_jsonb(q) order by to_jsonb(q)::text),'[]') from (select {cols} from public.{table}) q;"))
        assert len(rows) == old['count'], table+' row count changed'
        assert hashlib.sha256(json.dumps(rows, sort_keys=True).encode()).hexdigest() == old['sha256'], table+' original values changed'

def migration_files():
    base=ROOT/'supabase/sql'
    return [base/'20261029_local_parent_business_prerequisites.sql',
            base/'20261029_local_parent_business_baseline.sql',
            base/'20261029_local_parent_business_storage.sql']

CORE_SNAPSHOT = r'''
lock table public.families, public.users, public.binding_codes, public.tasks in share row exclusive mode;
create temporary table local_parent_original_core(table_name text, projection text, original_rows jsonb) on commit drop;
do $$ declare v_table text; v_projection text; v_rows jsonb;
begin
 foreach v_table in array array['families','users','binding_codes','tasks'] loop
  select string_agg(format('%I',column_name),',' order by ordinal_position) into v_projection
   from information_schema.columns where table_schema='public' and table_name=v_table;
  execute format('select coalesce(jsonb_agg(to_jsonb(q) order by to_jsonb(q)::text),''[]''::jsonb) from (select %s from public.%I) q',v_projection,v_table) into v_rows;
  insert into local_parent_original_core values(v_table,v_projection,v_rows);
 end loop;
end $$;
'''

CORE_VERIFY = r'''
-- Verify original core values before committing, under the same row/write locks.
do $$ declare v_snapshot record; v_rows jsonb;
begin
 for v_snapshot in select * from local_parent_original_core loop
  execute format('select coalesce(jsonb_agg(to_jsonb(q) order by to_jsonb(q)::text),''[]''::jsonb) from (select %s from public.%I) q',v_snapshot.projection,v_snapshot.table_name) into v_rows;
  if v_rows is distinct from v_snapshot.original_rows then
   raise exception 'Migration changed original core data in %; transaction aborted',v_snapshot.table_name;
  end if;
 end loop;
end $$;
'''

def migration_bundle():
    chunks=["begin; set local lock_timeout='5s'; set local app.lemon_local_business_target='lemon-kids-local';", CORE_SNAPSHOT]
    for path,role in zip(migration_files(),['supabase_admin','postgres','supabase_admin']):
        chunks.append('set local role '+role+';')
        chunks.append(re.sub(r'^\s*(begin|commit);\s*$', '', path.read_text(), flags=re.M|re.I))
    chunks.append(CORE_VERIFY)
    chunks.append('commit;')
    return '\n'.join(chunks)

def apply(name):
    assert name.startswith('lemon-parent-verify-'), 'Never apply to the source container'
    return psql(name,migration_bundle(),user='supabase_admin')

def connect_url(value, host):
    u=urllib.parse.urlsplit(value)
    login=u.netloc.rsplit('@',1)[0]
    return urllib.parse.urlunsplit((u.scheme, login+'@'+host+':5432', '/postgres', u.query, u.fragment))

def api_clone(source, target, net, db, port):
    src=info(source); env={}
    for item in src['Config']['Env']:
        k,_,v=item.partition('='); env[k]=v
    for k in ('PGRST_DB_URI','GOTRUE_DB_DATABASE_URL'):
        if k in env: env[k]=connect_url(env[k],db)
    if 'GOTRUE_API_HOST' in env: env['GOTRUE_API_HOST']='0.0.0.0'
    if 'GOTRUE_API_PORT' in env: env['GOTRUE_API_PORT']='9999'
    if 'PGRST_SERVER_PORT' in env: env['PGRST_SERVER_PORT']='3000'
    # 环境通过 stdin 传递，不出现在命令参数/日志中。
    internal=9999 if 'auth' in source else 3000
    args=['run','-d','--name',target,'--network',net,'-p',f'127.0.0.1:{port}:{internal}','--env-file','/dev/stdin',src['Config']['Image']]
    docker(*args,data=('\n'.join(k+'='+v for k,v in env.items())+'\n').encode())

def request(base, path, body=None, token=None, key=None, method=None, prefer=None):
    headers={'Content-Type':'application/json'}
    if token: headers['Authorization']='Bearer '+token
    if key: headers['apikey']=key
    if prefer: headers['Prefer']=prefer
    req=urllib.request.Request(base+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
    try:
        with urllib.request.urlopen(req,timeout=12) as res:
            raw=res.read(); return res.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        raw=error.read()
        try: payload=json.loads(raw)
        except ValueError: payload={'message':'non-json error'}
        return error.code,payload

def wait_api(base,path):
    for _ in range(45):
        try:
            status,_=request(base,path)
            if status < 500: return
        except (OSError,TimeoutError): pass
        time.sleep(1)
    raise RuntimeError('Temporary API did not become ready')

def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--keep',action='store_true'); args=ap.parse_args()
    prefix='lemon-parent-verify-'+uuid.uuid4().hex[:8]; net=prefix+'-net'; db=prefix+'-db'; auth=prefix+'-auth'; rest=prefix+'-rest'
    started=[]
    before=snapshot(SOURCE)
    print('source counts:',{k:v['count'] for k,v in before.items()},flush=True)
    try:
        docker('network','create',net)
        image=info(SOURCE)['Config']['Image']
        cmd="/nix/var/nix/profiles/default/bin/initdb -D /tmp/pgverify -U verify_bootstrap --auth=trust >/tmp/init.log && printf 'host all all 0.0.0.0/0 scram-sha-256\n' >> /tmp/pgverify/pg_hba.conf && exec /nix/var/nix/profiles/default/bin/postgres -D /tmp/pgverify -c listen_addresses=* -c shared_preload_libraries=pg_cron -c cron.database_name=postgres -c max_connections=60"
        docker('run','-d','--name',db,'--network',net,'--user','postgres','--tmpfs','/tmp:rw,size=768m,mode=1777','--entrypoint','bash',image,'-c',cmd); started.append(db)
        for _ in range(45):
            if docker('exec',db,'pg_isready','-U','verify_bootstrap',check=False).strip().endswith(b'accepting connections'): break
            time.sleep(1)
        else: raise RuntimeError('Temporary database did not start')
        roles=docker('exec',SOURCE,'pg_dumpall','-U','postgres','--roles-only').decode()
        roles=re.sub(r' GRANTED BY [a-zA-Z_][a-zA-Z_0-9]*', '', roles)
        psql(db,roles,user='verify_bootstrap')
        psql(db,'alter database postgres owner to postgres;',user='verify_bootstrap')
        dump=docker('exec',SOURCE,'pg_dump','-U','postgres','-d','postgres','--clean','--if-exists')
        docker('exec','-i',db,'psql','-X','-v','ON_ERROR_STOP=1','-U','verify_bootstrap','-d','postgres',data=dump)
        compare_snapshot(db,before)
        failed=False
        try:
            psql(db,migration_bundle().replace(CORE_VERIFY, "update public.tasks set title=title || ' rollback probe';\n"+CORE_VERIFY),user='supabase_admin')
        except RuntimeError as error:
            if 'Migration changed original core data' not in str(error): raise
            failed=True
        assert failed, 'Failure probe must abort the migration'
        assert psql(db,"select to_regclass('public.categories') is null;").strip()=='t', 'Failed migration left new business schema behind'
        compare_snapshot(db,before)
        print('transaction rollback probe: all DDL and original data preserved',flush=True)
        apply(db); compare_snapshot(db,before)
        apply(db); compare_snapshot(db,before)
        print('migration: twice applied, original core data unchanged',flush=True)
        api_clone('supabase_auth_lemon-kids-local',auth,net,db,54524); started.append(auth)
        api_clone('supabase_rest_lemon-kids-local',rest,net,db,54521); started.append(rest)
        wait_api('http://127.0.0.1:54524','/health'); wait_api('http://127.0.0.1:54521','/')
        context={'db':db,'auth':auth,'rest':rest,'network':net,'before':before}
        context_file=Path('/tmp/lemon-parent-verify-context.json');context_file.write_text(json.dumps(context));context_file.chmod(0o600)
        test_file=ROOT/'supabase/tests/local_parent_business_checks.py'
        if test_file.exists():
            subprocess.run([os.sys.executable,str(test_file),str(context_file)],env=ENV,check=True)
        compare_snapshot(SOURCE,before)
        print('source core data: unchanged; verification ready',flush=True)
    finally:
        if not args.keep:
            for name in reversed(started): docker('rm','-f',name,check=False)
            docker('network','rm',net,check=False)
        else: print('kept temporary environment:',prefix,flush=True)

if __name__=='__main__': main()
