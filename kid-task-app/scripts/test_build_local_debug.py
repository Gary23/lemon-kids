import base64
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('local_build', Path(__file__).with_name('build_local_debug.py'))
build = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build)


def key(role='anon'):
    payload = base64.urlsafe_b64encode(json.dumps({'role': role}).encode()).rstrip(b'=').decode()
    return 'header.' + payload + '.signature'


def dump(url='http://127.0.0.1:54421', anon=None, flag='true'):
    values = {'string/supabase_api_url': json.dumps(url), 'string/supabase_anon_key': json.dumps(anon or key()),
              'bool/supabase_is_local': flag, 'string/app_name': json.dumps('柠檬任务·本地')}
    return '\n'.join(f'    resource 0x7f00000{i} {name}\n      () {value}' for i, (name, value) in enumerate(values.items()))


class BuildLocalDebugTest(unittest.TestCase):
    def test_private_url_rules(self):
        for value in ['http://127.0.0.1:54421', 'http://10.0.2.2:54421/', 'http://172.31.1.1:1', 'http://192.168.1.2:65535']:
            self.assertEqual(value.rstrip('/'), build.validate_url(value))
        for value in ['', 'https://example.supabase.co', 'http://8.8.8.8:80', 'http://127.0.0.2:80',
                      'http://10.0.0.1', 'http://10.0.0.1:0', 'http://10.0.0.1:65536',
                      'http://10.0.0.1:80/path', 'http://10.0.0.1:80?key=x', 'http://user@10.0.0.1:80']:
            with self.subTest(value=value), self.assertRaises(ValueError):
                build.validate_url(value)

    def test_only_anon_key_allowed(self):
        self.assertEqual(key(), build.validate_key(key()))
        for value in ['', 'invalid', key('service_role'), key('authenticated')]:
            with self.assertRaises(ValueError):
                build.validate_key(value)

    def test_property_then_environment_and_explicit_url_override(self):
        env = {'LEMON_KIDS_LOCAL_SUPABASE_URL': 'http://10.0.2.2:54421', 'LEMON_KIDS_LOCAL_SUPABASE_ANON_KEY': key()}
        cfg = {'localSupabaseUrl': 'http://127.0.0.1:54421', 'localSupabaseAnonKey': key()}
        self.assertEqual(build.resolve_config(cfg, env)[0], cfg['localSupabaseUrl'])
        self.assertEqual(build.resolve_config({}, env)[0], env['LEMON_KIDS_LOCAL_SUPABASE_URL'])
        self.assertEqual(build.resolve_config(cfg, env, 'http://192.168.1.1:54421')[0], 'http://192.168.1.1:54421')
        with self.assertRaises(ValueError):
            build.resolve_config({'localSupabaseUrl': ''}, env)

    def test_java_properties_url_escaping(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'local.properties'
            path.write_text('# ignored\nlocalSupabaseUrl=http\\://127.0.0.1\\:54421\n')
            self.assertEqual(build.properties(path)['localSupabaseUrl'], 'http://127.0.0.1:54421')

    def test_apk_must_match_selected_environment(self):
        build.verify_dump(dump(), 'http://127.0.0.1:54421', key())
        for data in [dump(url='https://production.supabase.co'), dump(flag='false'), dump(anon=key('service_role'))]:
            with self.assertRaises(ValueError):
                build.verify_dump(data, 'http://127.0.0.1:54421', key())

    def test_failed_config_removes_old_delivery_files_without_building(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in (build.APK_NAME, build.METADATA_NAME):
                (root / name).write_text('old')
            with patch.object(build, 'ROOT', root), patch.object(build, 'OUTPUT', root), \
                 patch('sys.argv', ['build_local_debug.py']), patch.dict('os.environ', {}, clear=True), \
                 patch.object(build.subprocess, 'run') as run:
                with self.assertRaises(SystemExit) as error:
                    build.main()
                self.assertEqual(error.exception.code, 1)
                run.assert_not_called()
            self.assertFalse((root / build.APK_NAME).exists())
            self.assertFalse((root / build.METADATA_NAME).exists())


if __name__ == '__main__':
    unittest.main()
