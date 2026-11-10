#!/usr/bin/env python3
"""Verify kid-task against disposable DB/Auth/REST/Storage containers, never write source."""
import base64
import argparse
from datetime import date, timedelta
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from verify_local_parent_business import DOCKER, ENV, docker, info, psql, api_clone, connect_url, request
from local_kid_task_support import ROOT, SOURCE, SQL, migration_bundle, preflight, snapshot_all

REST = 'http://127.0.0.1:54531'
AUTH = 'http://127.0.0.1:54534'
STORAGE = 'http://127.0.0.1:54535'
urllib.request.install_opener(urllib.request.build_opener(urllib.request.ProxyHandler({})))


def jwt(secret, claims):
    enc = lambda x: base64.urlsafe_b64encode(json.dumps(x, separators=(',', ':')).encode()).rstrip(b'=').decode()
    raw = enc({'alg': 'HS256', 'typ': 'JWT'}) + '.' + enc(claims)
    return raw + '.' + base64.urlsafe_b64encode(hmac.new(secret.encode(), raw.encode(), hashlib.sha256).digest()).rstrip(b'=').decode()


def expect(condition, label):
    if not condition:
        raise AssertionError(label)


def wait_ready(base, path, container):
    # The local x86 VM can take longer than 45 seconds to start Storage under build load.
    deadline = time.monotonic() + 180
    next_progress = time.monotonic() + 20
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(base + path, timeout=3) as response:
                if response.status < 500:
                    return
        except urllib.error.HTTPError as error:
            if error.code < 500:
                return
        except (OSError, TimeoutError):
            pass
        if time.monotonic() >= next_progress:
            state = info(container)['State']
            expect(state['Running'], 'Temporary ' + container.rsplit('-', 1)[-1] + ' exited: ' + str(state['ExitCode']))
            print('Temporary ' + container.rsplit('-', 1)[-1] + ' still starting...', flush=True)
            next_progress = time.monotonic() + 20
        time.sleep(1)
    raise RuntimeError('Temporary ' + container.rsplit('-', 1)[-1] + ' readiness timeout')


def run_checks(db, auth, storage):
    checks = []
    def mark(label):
        checks.append(label)
        print('PASS:', label, flush=True)

    def call(token, path, body=None, method=None, ok=(200, 201, 204), prefer=None):
        status, result = request(REST, path, body, token=token, method=method, prefer=prefer)
        # Avoid logging response bodies, user data, request credentials or SQL.
        expect(status in ok, path.split('?')[0] + ' HTTP ' + str(status))
        return result

    authenv = dict(x.split('=', 1) for x in info(auth)['Config']['Env'])
    admin = jwt(authenv['GOTRUE_JWT_SECRET'], {'role': 'service_role', 'aud': 'authenticated', 'exp': int(time.time()) + 3600})
    anon = jwt(authenv['GOTRUE_JWT_SECRET'], {'role': 'anon', 'exp': int(time.time()) + 3600})
    families = []
    for _ in range(2):
        email = 'kid-local-' + uuid.uuid4().hex + '@example.invalid'
        password = uuid.uuid4().hex
        status, parent = request(AUTH, '/admin/users', {'email': email, 'password': password, 'email_confirm': True}, token=admin)
        expect(status in (200, 201), 'temporary Auth parent setup')
        status, session = request(AUTH, '/token?grant_type=password', {'email': email, 'password': password})
        expect(status == 200, 'real parent password login')
        parent_token = session['access_token']
        call(parent_token, '/rpc/create_local_parent_profile', {'p_name': 'local verification parent'})
        family = call(parent_token, '/rpc/create_local_family', {'p_name': 'local verification family'})
        code = call(parent_token, '/rpc/generate_binding_code', {'p_child_uid': None, 'p_child_name': 'local verification child', 'p_type': 'task'})
        binding = call(anon, '/rpc/exchange_binding_code', {'p_code': code, 'p_device_id': uuid.uuid4().hex, 'p_type': 'task', 'p_force_rebind': False})
        expect(binding['status'] == 'success' and binding['family_id'] == family['id'], 'task binding exchange')
        child_id = binding['child_uid']
        child_password = 'LmK!d_' + hashlib.md5((child_id + 'salt_lemon_2024').encode()).hexdigest()
        status, session = request(AUTH, '/token?grant_type=password', {'email': binding['email'], 'password': child_password})
        expect(status == 200, 'real binding-derived child login')
        status, refreshed = request(AUTH, '/token?grant_type=refresh_token', {'refresh_token': session['refresh_token']})
        error_code = refreshed.get('error_code', refreshed.get('code', 'unknown')) if isinstance(refreshed, dict) else 'unknown'
        expect(status == 200, 'child refresh-token login HTTP ' + str(status) + ' code=' + str(error_code))
        second = call(anon, '/rpc/exchange_binding_code', {'p_code': code, 'p_device_id': uuid.uuid4().hex, 'p_type': 'task', 'p_force_rebind': False})
        expect(second['status'] == 'success', 'task binding permits multiple devices')
        families.append({'id': family['id'], 'parent': parent_token, 'child': child_id, 'token': refreshed['access_token']})
    a, b = families
    P, C, B = a['parent'], a['token'], b['token']
    mark('real parent onboarding, task binding, multi-device login and child refresh')

    growth = call(C, '/rpc/growth_snapshot', {})
    expect(growth['child_id'] == a['child'] and growth['total_exp'] == 0 and growth['events'] == [], 'empty own growth model')
    for token in (anon, P):
        expect(request(REST, '/rpc/growth_snapshot', {}, token=token)[0] >= 400, 'growth requires a child identity')
    expect(call(B, '/rpc/growth_snapshot', {})['child_id'] == b['child'], 'snapshot identity derived from JWT')
    for table in ('tasks', 'rewards', 'point_records', 'app_usage', 'app_limits'):
        expect(call(C, '/' + table) == [], table + ' valid empty state')
        status, rows = request(REST, '/' + table, token=anon)
        expect(status in (401, 403) or (status == 200 and rows == []), table + ' anonymous data inaccessible')
    mark('growth empty response, role restriction and empty business reads')

    cat = call(P, '/categories', {'family_id': a['id'], 'name': 'verification'}, prefer='return=representation')[0]['id']
    template = call(P, '/task_templates', {'family_id': a['id'], 'title': 'local verification task', 'growth_domain': 'reading'}, prefer='return=representation')[0]['id']
    call(P, '/rpc/set_category_task_templates', {'p_category_id': cat, 'p_template_ids': [template]})
    today = date.today().isoformat()
    args = {'p_child_id': a['child'], 'p_category_id': cat, 'p_template_id': None, 'p_start_date': today, 'p_end_date': today,
            'p_recurrence_type': 'none', 'p_recurrence_weekdays': [], 'p_selected_template_ids': [template]}
    task = call(P, '/rpc/create_tasks_from_selection', args)[0]
    expect(call(C, '/tasks')[0]['id'] == task['id'], 'home/calendar own tasks')
    status, rows = request(REST, '/tasks', token=anon)
    expect(status in (401, 403) or (status == 200 and rows == []), 'anonymous cannot read populated task table')
    expect(call(B, '/tasks') == [] and call(B, '/categories') == [], 'foreign family tasks hidden')
    expect(len(call(C, '/categories')) == 1, 'own family categories visible')
    future = (date.today() + timedelta(days=14)).isoformat()
    args.update(p_start_date=future, p_end_date=future)
    future_task = call(P, '/rpc/create_tasks_from_selection', args)[0]
    expect(call(C, '/tasks?due_date=eq.' + future)[0]['id'] == future_task['id'], 'future plan real data')
    call(C, '/rpc/complete_task', {'p_task_id': task['id'], 'p_child_id': a['child']})
    expect(call(C, '/rpc/growth_snapshot', {})['total_exp'] == task['reward_points'], 'completion growth')
    call(C, '/rpc/undo_task_completion', {'p_task_id': task['id'], 'p_child_id': a['child']})
    expect(call(C, '/rpc/growth_snapshot', {})['total_exp'] == 0, 'undo recomputes growth')
    expect(request(REST, '/rpc/complete_task', {'p_task_id': task['id'], 'p_child_id': a['child']}, token=B)[0] >= 400, 'foreign completion denied')
    mark('home/calendar/future tasks, completion/undo, points and family isolation')

    call(C, '/rpc/complete_task', {'p_task_id': task['id'], 'p_child_id': a['child']})
    expect(call(C, '/tasks?id=eq.' + task['id'])[0]['status'] == 'verified', 're-completion after undo')
    expect(request(REST, '/rpc/complete_task', {'p_task_id': task['id'], 'p_child_id': a['child']}, token=C)[0] >= 400, 'completed task cannot complete twice')
    mark('task re-completion after undo and single completion transaction')

    call(P, '/rpc/set_badge_progress', {'p_child_id': a['child'], 'p_badge_key': 'reading', 'p_segment': '', 'p_value': 3, 'p_expected_version': 0})
    reward = call(P, '/rewards', {'family_id': a['id'], 'title': 'local reward', 'cost': 2, 'repeatable': True}, prefer='return=representation')[0]
    redeem = {'p_reward_id': reward['id'], 'p_child_id': a['child'], 'p_request_id': str(uuid.uuid4())}
    redemption = call(C, '/rpc/redeem_reward', redeem)
    expect(call(C, '/rpc/redeem_reward', redeem) == redemption, 'redemption idempotency')
    call(C, '/rpc/cancel_reward_redemption', {'p_redemption_id': redemption, 'p_child_id': a['child']})
    redeem['p_request_id'] = str(uuid.uuid4())
    redemption = call(C, '/rpc/redeem_reward', redeem)
    call(C, '/rpc/use_reward_redemption', {'p_redemption_id': redemption, 'p_child_id': a['child']})
    expect(request(REST, '/rpc/cancel_reward_redemption', {'p_redemption_id': redemption, 'p_child_id': a['child']}, token=C)[0] >= 400, 'used redemption cannot refund')
    growth = call(C, '/rpc/growth_snapshot', {})
    expect(growth['completed_tasks'] == 1 and growth['total_exp'] == task['reward_points'], 'spending does not reduce EXP')
    expect(len(growth['badge_progress']) == 1 and any(e['kind'] == 'reward' for e in growth['events']), 'badge and reward milestones')
    expect(call(B, '/rpc/growth_snapshot', {})['events'] == [], 'other child has no foreign milestones')
    expect(call(B, '/reward_redemptions') == [], 'foreign redemptions hidden')
    expect(call(C, '/rpc/get_monthly_task_points', {'p_child_id': a['child']}) == task['reward_points'], 'monthly earned points')
    expect(call(C, '/rpc/get_unavailable_one_time_rewards', {'p_family_id': a['id']}) == [], 'one-time reward availability model')
    mark('redemption/refund/use, idempotency and real growth milestones')

    path = a['id'] + '/' + str(uuid.uuid4()) + '.jpg'
    image = (ROOT / 'kid-task-app/src/main/res/drawable-nodpi/reward_book.jpg').read_bytes()
    def upload(token, name):
        req = urllib.request.Request(STORAGE + '/object/reward-images/' + name, data=image,
                  headers={'Authorization': 'Bearer ' + token, 'Content-Type': 'image/jpeg'}, method='POST')
        try:
            with urllib.request.urlopen(req, timeout=12) as response:
                return response.status
        except urllib.error.HTTPError as error:
            return error.code
    expect(upload(P, path) in (200, 201), 'real Storage upload')
    expect(upload(C, a['id'] + '/' + str(uuid.uuid4()) + '.jpg') >= 400, 'child upload denied')
    call(P, '/rewards?id=eq.' + reward['id'], {'image_path': path}, method='PATCH')
    status, signed = request(STORAGE, '/object/sign/reward-images/' + path, {'expiresIn': 600}, token=C)
    expect(status == 200, 'child signs own-family reward image')
    signed_path = signed.get('signedURL', signed.get('signedUrl', ''))
    parsed = urllib.parse.urlsplit(signed_path)
    expect(not parsed.netloc and parsed.path.startswith('/object/sign/'), 'Storage returns a relative image path')
    with urllib.request.urlopen(STORAGE + signed_path, timeout=12) as response:
        expect(response.status == 200 and response.read() == image, 'signed image bytes match')
    expect(request(STORAGE, '/object/sign/reward-images/' + path, {'expiresIn': 600}, token=B)[0] >= 400, 'foreign image signing denied')
    mark('real private Storage upload, child signing/download and foreign/child write denial')

    call(C, '/app_usage', {'family_id': a['id'], 'child_id': a['child'], 'package_name': 'verify.local', 'app_name': 'verify', 'date': today, 'duration_seconds': 10})
    expect(call(B, '/app_usage') == [], 'usage isolation')
    call(B, '/app_usage?child_id=eq.' + a['child'], method='DELETE')
    call(P, '/app_usage?child_id=eq.' + a['child'], method='DELETE')
    expect(len(call(C, '/app_usage')) == 1, 'foreign child and parent cannot delete own report')
    expect(request(REST, '/app_usage?child_id=eq.' + a['child'], token=anon, method='DELETE')[0] in (401, 403), 'anonymous cannot delete usage')
    call(C, '/app_usage?child_id=eq.' + a['child'], method='DELETE')
    expect(call(C, '/app_usage') == [], 'own usage replacement permission')
    mark('usage reporting/replacement and own-family scope')
    return checks


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--keep-on-failure', action='store_true', help='Keep disposable containers for diagnosis; clean them after debugging')
    args = parser.parse_args()
    prefix = 'lemon-kid-verify-' + uuid.uuid4().hex[:8]
    net, db, auth, rest, storage = [prefix + suffix for suffix in ('-net', '-db', '-auth', '-rest', '-storage')]
    started = []
    passed = False
    print('Reading source business snapshot (read-only)...', flush=True)
    before = snapshot_all(SOURCE)
    print('source:', preflight().strip(), flush=True)
    try:
        docker('network', 'create', net)
        print('Starting disposable database...', flush=True)
        image = info(SOURCE)['Config']['Image']
        command = "/nix/var/nix/profiles/default/bin/initdb -D /tmp/pgverify -U verify_bootstrap --auth=trust >/tmp/init.log && printf 'host all all 0.0.0.0/0 scram-sha-256\\n' >> /tmp/pgverify/pg_hba.conf && exec /nix/var/nix/profiles/default/bin/postgres -D /tmp/pgverify -c listen_addresses=* -c shared_preload_libraries=pg_cron -c cron.database_name=postgres -c max_connections=60"
        docker('run', '-d', '--name', db, '--network', net, '--user', 'postgres', '--tmpfs', '/tmp:rw,size=768m,mode=1777', '--entrypoint', 'bash', image, '-c', command)
        started.append(db)
        for _ in range(45):
            if docker('exec', db, 'pg_isready', '-U', 'verify_bootstrap', check=False).strip().endswith(b'accepting connections'):
                break
            time.sleep(1)
        else:
            raise RuntimeError('Temporary database did not start')
        roles = docker('exec', SOURCE, 'pg_dumpall', '-U', 'postgres', '--roles-only').decode()
        roles = re.sub(r' GRANTED BY [a-zA-Z_][a-zA-Z_0-9]*', '', roles)
        psql(db, roles, user='verify_bootstrap')
        psql(db, 'alter database postgres owner to postgres;', user='verify_bootstrap')
        dump = docker('exec', SOURCE, 'pg_dump', '-U', 'postgres', '-d', 'postgres', '--clean', '--if-exists')
        print('Restoring source copy into disposable database...', flush=True)
        docker('exec', '-i', db, 'psql', '-X', '-v', 'ON_ERROR_STOP=1', '-U', 'verify_bootstrap', '-d', 'postgres', data=dump)
        psql(db, 'update cron.job set active=false;', user='supabase_admin')
        print('Checking migration rollback and repeatability...', flush=True)
        expect(snapshot_all(db) == before, 'cloned business data matches source')
        existed = psql(db, "select to_regprocedure('public.growth_snapshot()') is not null;").strip()
        failed = False
        try:
            expect(before['users']['count'] > 0, 'rollback probe requires existing local users')
            psql(db, migration_bundle().replace('do $$ declare old record;', "update public.users set name=name || ' rollback probe';\ndo $$ declare old record;"))
        except RuntimeError as error:
            if 'Kid task migration changed business data' not in str(error):
                raise
            failed = True
        expect(failed and snapshot_all(db) == before, 'failed migration transaction rollback')
        expect(psql(db, "select to_regprocedure('public.growth_snapshot()') is not null;").strip() == existed, 'failed migration function rollback')
        for _ in range(2):
            psql(db, migration_bundle())
            expect(snapshot_all(db) == before, 'idempotent migration preserves all public data')
        print('PASS: transaction rollback, repeated migration and all public business data preservation', flush=True)
        api_clone('supabase_auth_lemon-kids-local', auth, net, db, 54534)
        started.append(auth)
        api_clone('supabase_rest_lemon-kids-local', rest, net, db, 54531)
        started.append(rest)
        config = info('supabase_storage_lemon-kids-local')['Config']
        env = dict(x.split('=', 1) for x in config['Env'])
        env['DATABASE_URL'] = connect_url(env['DATABASE_URL'], db)
        env.update(STORAGE_BACKEND='file', FILE_STORAGE_BACKEND_PATH='/mnt', ENABLE_IMAGE_TRANSFORMATION='false', PORT='5000')
        docker('run', '-d', '--name', storage, '--network', net, '-p', '127.0.0.1:54535:5000', '--tmpfs', '/mnt:rw,size=64m,mode=1777', '--env-file', '/dev/stdin', config['Image'], data=('\n'.join(k + '=' + v for k, v in env.items()) + '\n').encode())
        started.append(storage)
        print('Waiting for temporary Auth...', flush=True)
        wait_ready(AUTH, '/health', auth)
        print('Waiting for temporary REST...', flush=True)
        wait_ready(REST, '/', rest)
        print('Waiting for temporary Storage...', flush=True)
        wait_ready(STORAGE, '/status', storage)
        checks = run_checks(db, auth, storage)
        expect(snapshot_all(SOURCE) == before, 'source public data unchanged')
        result = {'passed_groups': ['migration rollback/idempotency/data preservation'] + checks,
                  'source_unchanged': True, 'sql_sha256': hashlib.sha256(SQL.read_bytes()).hexdigest()}
        Path('/tmp/lemon-kid-task-check-results.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
        print('ALL PASSED:', len(result['passed_groups']), 'groups; source unchanged', flush=True)
        passed = True
    except Exception:
        for name in started:
            if name == db:
                continue
            log = subprocess.run([DOCKER, 'logs', '--tail', '6', name], env=ENV,
                                 stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=20)
            diagnostic = log.stdout.decode(errors='replace')
            summaries = []
            for line in diagnostic.splitlines():
                try:
                    record = json.loads(line)
                    summaries.append(json.dumps({k: record[k] for k in ('level', 'msg', 'message', 'code', 'status', 'error_code') if k in record}))
                except ValueError:
                    summaries.append(line)
            diagnostic = '\n'.join(summaries)
            diagnostic = re.sub(r'(postgres(?:ql)?://)[^\s\"\']+', r'\1[redacted]', diagnostic)
            diagnostic = re.sub(r'eyJ[A-Za-z0-9_.-]+', '[jwt-redacted]', diagnostic)
            print('Diagnostic', name.rsplit('-', 1)[-1], diagnostic[-2200:], flush=True)
        raise
    finally:
        if args.keep_on_failure and not passed:
            context = {'network': net, 'db': db, 'auth': auth, 'rest': rest, 'storage': storage,
                       'started': started, 'source_snapshot': before}
            context_file = Path('/tmp/lemon-kid-task-verify-context.json')
            context_file.write_text(json.dumps(context))
            context_file.chmod(0o600)
            print('Kept disposable environment for diagnosis:', context_file, flush=True)
        else:
            for name in reversed(started):
                docker('rm', '-f', name, check=False)
            docker('network', 'rm', net, check=False)


if __name__ == '__main__':
    main()
