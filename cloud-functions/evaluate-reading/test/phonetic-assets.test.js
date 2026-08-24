'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

Object.assign(process.env, {
  SUPABASE_URL: 'https://example.supabase.co',
  SUPABASE_SERVICE_ROLE_KEY: 'test-service-role',
  LITERACY_STS_SECRET_ID: 'test-id',
  LITERACY_STS_SECRET_KEY: 'test-key',
  LITERACY_TENCENT_APP_ID: '1',
  LITERACY_TENCENT_REGION: 'ap-beijing',
  LITERACY_PHONETIC_BACKFILL_KEY: 'test-phonetic-backfill-key'
});

const { _private } = require('../index');

test('词组级字典会区分组长和长城的多音字', () => {
  assert.deepEqual(_private.phonemesForText('组长'), ['zu3', 'zhang3']);
  assert.deepEqual(_private.phonemesForText('长城'), ['chang2', 'cheng2']);
});

test('轻声保留位置但不伪造腾讯不支持的调号', () => {
  assert.deepEqual(_private.phonemesForText('妈妈'), ['ma1', null]);
});

test('人工音素仅允许腾讯数字拼音格式', () => {
  assert.deepEqual(_private.normalizePhonemeTokens(['zu3', 'zhang3'], '组长'), ['zu3', 'zhang3']);
  assert.throws(() => _private.normalizePhonemeTokens(['zu', 'zhang3'], '组长'), /第 1 个汉字/);
  assert.throws(() => _private.normalizePhonemeTokens(['zu3'], '组长'), /数量/);
});

test('TEXT_MODE=1 只为有标注的汉字组装 wordList，轻声省略 pron', () => {
  assert.deepEqual(
    _private.wordListForPhonemeTokens('组长妈妈', ['zu3', 'zhang3', 'ma1', null]),
    [
      { word: '组', pron: [['zu3']] },
      { word: '长', pron: [['zhang3']] },
      { word: '妈', pron: [['ma1']] },
      { word: '妈' }
    ]
  );
  assert.throws(() => _private.wordListForPhonemeTokens('组长', ['zu3']), /数量/);
});

test('音素生命周期 RPC 将主业务写入与资产转移/清理置于同一事务', () => {
  const migration = fs.readFileSync(
    path.resolve(__dirname, '../../../supabase/sql/20260823_literacy_phonetic_asset_lifecycle_atomic.sql'),
    'utf8'
  );
  assert.match(migration, /create or replace function public\.complete_literacy_character_with_phonetic_assets/i);
  assert.match(migration, /insert into public\.recognized_characters[\s\S]*insert into public\.literacy_phonetic_assets[\s\S]*delete from public\.literacy_phonetic_assets/i);
  assert.match(migration, /insert into public\.known_characters[\s\S]*delete from public\.literacy_phonetic_assets/i);
  assert.match(migration, /create or replace function public\.archive_recognized_character_with_phonetic_assets[\s\S]*archived := public\.archive_recognized_character[\s\S]*delete from public\.literacy_phonetic_assets/i);
});

test('后台回填必须使用独立密钥，不能依赖或绕过儿童登录凭证', () => {
  assert.doesNotThrow(() => _private.requirePhoneticBackfillKey({
    headers: { 'x-phonetic-backfill-key': 'test-phonetic-backfill-key' }
  }));
  assert.throws(
    () => _private.requirePhoneticBackfillKey({ headers: { 'x-phonetic-backfill-key': 'wrong-key' } }),
    /密钥不正确/
  );
  assert.throws(() => _private.requirePhoneticBackfillKey({ headers: {} }), /缺少音素回填密钥/);
});
