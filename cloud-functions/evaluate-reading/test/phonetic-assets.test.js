'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');

Object.assign(process.env, {
  SUPABASE_URL: 'https://example.supabase.co',
  SUPABASE_SERVICE_ROLE_KEY: 'test-service-role',
  LITERACY_STS_SECRET_ID: 'test-id',
  LITERACY_STS_SECRET_KEY: 'test-key',
  LITERACY_TENCENT_APP_ID: '1',
  LITERACY_TENCENT_REGION: 'ap-beijing'
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
