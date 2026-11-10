#!/usr/bin/env python3
"""Build and verify the local APK. Never install devices or migrate a database."""
import argparse
import base64
from datetime import datetime, timezone
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import urllib.parse

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / 'kid-task-app/build/outputs/apk/local-debug'
PACKAGE = 'com.lemonkids.kidtask.local'
APK_NAME = 'kid-task-app-local-debug.apk'
METADATA_NAME = 'kid-task-app-local-debug.json'


def properties(path):
    # Java-properties escaping for the local configuration keys used here.
    values = {}
    if path.exists():
        for line in path.read_text().splitlines():
            if line.lstrip().startswith(('#', '!')) or '=' not in line:
                continue
            key, value = line.split('=', 1)
            value = re.sub(r'\\u([0-9a-fA-F]{4})', lambda m: chr(int(m[1], 16)), value.strip())
            value = re.sub(r'\\(.)', lambda m: {'t': '\t', 'n': '\n', 'r': '\r', 'f': '\f'}.get(m[1], m[1]), value)
            values[key.strip()] = value
    return values


def validate_url(value):
    parsed = urllib.parse.urlsplit(value)
    try:
        ip = ipaddress.IPv4Address(parsed.hostname or '')
        allowed = ip == ipaddress.IPv4Address('127.0.0.1') or any(
            ip in ipaddress.IPv4Network(net) for net in ('10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16'))
        valid = (parsed.scheme == 'http' and allowed and parsed.port is not None
                 and 1 <= parsed.port <= 65535 and not parsed.username and not parsed.password
                 and parsed.path in ('', '/') and not parsed.query and not parsed.fragment)
    except ValueError:
        valid = False
    if not valid:
        raise ValueError('本地 URL 必须是 http://私有IPv4:端口 或 http://127.0.0.1:端口')
    return value.rstrip('/')


def validate_key(value):
    try:
        parts = value.split('.')
        assert len(parts) == 3 and all(parts)
        claims = json.loads(base64.urlsafe_b64decode(parts[1] + '=' * (-len(parts[1]) % 4)))
        assert claims['role'] == 'anon'
    except (ValueError, KeyError, AssertionError, TypeError):
        raise ValueError('本地客户端 key 必须是 anon 角色的公开 JWT，禁止打包管理密钥') from None
    return value


def resolve_config(config, environment, url_override=None):
    url = url_override if url_override is not None else config.get('localSupabaseUrl', environment.get('LEMON_KIDS_LOCAL_SUPABASE_URL', ''))
    key = config.get('localSupabaseAnonKey', environment.get('LEMON_KIDS_LOCAL_SUPABASE_ANON_KEY', ''))
    return validate_url(url.strip()), validate_key(key.strip())


def resource_value(dump, name):
    blocks = re.split(r'^\s*resource\s+0x[0-9a-fA-F]+\s+', dump, flags=re.M)
    matches = [b for b in blocks if b.startswith(name + '\n')]
    if len(matches) != 1:
        raise ValueError('APK 缺少或重复环境资源：' + name)
    match = re.search(r'^\s*\(\)\s+(.+)$', matches[0], re.M)
    if not match:
        raise ValueError('APK 环境资源缺少默认值：' + name)
    value = match[1].strip()
    return json.loads(value) if value.startswith('"') else value


def verify_dump(dump, url, key):
    if resource_value(dump, 'string/supabase_api_url') != url:
        raise ValueError('APK 生效的 API 地址与本地配置不一致')
    if resource_value(dump, 'string/supabase_anon_key') != key:
        raise ValueError('APK 生效的客户端 key 与本地配置不一致')
    if resource_value(dump, 'bool/supabase_is_local') not in ('true', '(bool) true', '0xffffffff'):
        raise ValueError('APK 不是本地环境构建')
    if resource_value(dump, 'string/app_name') != '柠檬任务·本地':
        raise ValueError('APK 缺少本地启动器标识')


def main():
    parser = argparse.ArgumentParser(description='打包柠檬任务独立本地调试 APK，不安装设备、不迁移数据库')
    parser.add_argument('--url', help='覆盖构建初始地址，如 http://192.168.1.10:54421')
    parser.add_argument('--check-config', action='store_true', help='仅校验配置，不构建')
    args = parser.parse_args()
    if not args.check_config:
        OUTPUT.mkdir(parents=True, exist_ok=True)
        # An unsuccessful attempt must not leave a previous APK looking like this run's result.
        for name in (APK_NAME, METADATA_NAME):
            (OUTPUT / name).unlink(missing_ok=True)
    try:
        config = properties(ROOT / 'local.properties')
        url, key = resolve_config(config, os.environ, args.url)
        print('配置校验通过：', url, '；公开 anon key 已配置', flush=True)
        if args.check_config:
            return
        env = dict(os.environ)
        if not env.get('JAVA_HOME'):
            bundled = Path('/Applications/Android Studio.app/Contents/jbr/Contents/Home')
            if bundled.exists():
                env['JAVA_HOME'] = str(bundled)
        cmd = [str(ROOT / 'gradlew'), ':kid-task-app:testDebugUnitTest', ':shared:testDebugUnitTest',
               ':kid-task-app:assembleDebug', '-PlocalSupabase=true', '--console=plain']
        if args.url:
            cmd.append('-PlocalSupabaseUrl=' + url)
        subprocess.run(cmd, cwd=ROOT, env=env, check=True)
        source = ROOT / 'kid-task-app/build/outputs/apk/debug/kid-task-app-debug.apk'
        sdk = Path(config.get('sdk.dir') or env.get('ANDROID_HOME') or env.get('ANDROID_SDK_ROOT', ''))
        versions = sorted((sdk / 'build-tools').glob('*/aapt2'), key=lambda p: tuple(int(n) if n.isdigit() else 0 for n in re.split(r'[.-]', p.parent.name)))
        if not versions:
            raise ValueError('找不到 Android SDK aapt2，不能校验 APK')
        aapt = str(versions[-1])
        dump = subprocess.run([aapt, 'dump', 'resources', str(source)], check=True, capture_output=True, text=True).stdout
        verify_dump(dump, url, key)
        badging = subprocess.run([aapt, 'dump', 'badging', str(source)], check=True, capture_output=True, text=True).stdout
        found = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
        if not found or found[1] != PACKAGE or not found[3].endswith('-local'):
            raise ValueError('APK 包名或版本不是独立本地包')
        metadata = {'package': found[1], 'version_code': found[2], 'version_name': found[3],
                    'built_at': datetime.now(timezone.utc).isoformat(), 'api_url': url,
                    'apk': str(OUTPUT / APK_NAME), 'sha256': hashlib.sha256(source.read_bytes()).hexdigest()}
        with tempfile.TemporaryDirectory(prefix='.local-apk-', dir=OUTPUT) as directory:
            staged = Path(directory)
            shutil.copyfile(source, staged / APK_NAME)
            (staged / METADATA_NAME).write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + '\n')
            os.replace(staged / APK_NAME, OUTPUT / APK_NAME)
            os.replace(staged / METADATA_NAME, OUTPUT / METADATA_NAME)
        print(json.dumps(metadata, ensure_ascii=False, indent=2))
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        if not args.check_config:
            for name in (APK_NAME, METADATA_NAME):
                (OUTPUT / name).unlink(missing_ok=True)
        # Do not include resource dumps, process arguments or credentials in diagnostics.
        message = str(error) if isinstance(error, ValueError) else type(error).__name__
        parser.exit(1, '本地打包失败：' + message + '\n')


if __name__ == '__main__':
    main()
