'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const {
  InputError,
  inputOptions,
  isMp3,
  objectPathForAsset,
  publicAudioUrl,
  sanitizeError
} = require('../lib');

test('按根任务生成受约束的对象路径', () => {
  assert.equal(objectPathForAsset({
    id: 'asset-1', voice_version: 'v1', root_literacy_character_id: 'task-id',
    recognized_character_id: null, item_type: 'word', item_order: 2
  }), 'v1/task/task-id/word-2.mp3');
});

test('手工已认识字使用独立路径', () => {
  assert.equal(objectPathForAsset({
    id: 'asset-2', voice_version: 'v1', root_literacy_character_id: null,
    recognized_character_id: 'recognized-id', item_type: 'character', item_order: 0
  }), 'v1/recognized/recognized-id/character.mp3');
});

test('公开 URL 保留对象路径层级', () => {
  assert.equal(
    publicAudioUrl('https://project.supabase.co/', 'v1/task/task id/character.mp3'),
    'https://project.supabase.co/storage/v1/object/public/literacy-audio/v1/task/task%20id/character.mp3'
  );
});

test('仅接受事件函数调用，并限制批处理参数', () => {
  assert.deepEqual(inputOptions({ source: 'recognized', limit: 50, concurrency: 5 }), {
    action: 'generate', dryRun: false, source: 'recognized', recordId: null, limit: 50, concurrency: 5,
    retryFailed: false, onlyMissingOrInvalid: true
  });
  assert.deepEqual(inputOptions({ action: 'cleanup', limit: 7, concurrency: 2 }), {
    action: 'cleanup', limit: 7, concurrency: 2
  });
  assert.deepEqual(inputOptions({ action: 'reconcile' }), {
    action: 'reconcile', limit: 1000, concurrency: 3
  });
  assert.deepEqual(inputOptions({ action: 'monitor' }), { action: 'monitor' });
  assert.deepEqual(inputOptions({ Message: '{"action":"cleanup","limit":2}' }), {
    action: 'cleanup', limit: 2, concurrency: 3
  });
  assert.throws(() => inputOptions({ httpMethod: 'POST' }), InputError);
});

test('MP3 头与敏感错误脱敏', () => {
  assert.equal(isMp3(Buffer.from([0x49, 0x44, 0x33, 0x04])), true);
  assert.equal(isMp3(Buffer.from([0xff, 0xfb, 0x90, 0x64])), true);
  assert.equal(isMp3(Buffer.from('not mp3')), false);
  assert.equal(sanitizeError(new Error('token=abc123 Bearer token-value')).includes('abc123'), false);
});
