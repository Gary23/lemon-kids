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

test('存库仅在教学音频同步删除和复习记录物理移除后才成功', async () => {
  const calls = [];
  const target = { id: 'recognized-1', source_literacy_character_id: 'task-1' };
  const asset = {
    id: 'asset-1', object_path: 'v1/task/task-1/word-0.mp3', voice_version: 'v1',
    item_type: 'word', item_order: 0, root_literacy_character_id: 'task-1', recognized_character_id: 'recognized-1'
  };
  const supabaseRequest = async (path, options = {}) => {
    calls.push({ path, options });
    if (path.includes('recognized_character_id=eq.recognized-1') && path.includes('status=neq.deleted')) return [asset];
    if (path.includes('root_literacy_character_id=eq.task-1') && path.includes('status=neq.deleted')) return [asset];
    return null;
  };
  const fetchImpl = async () => ({ ok: true, status: 200, text: async () => '' });

  await _private.deleteArchivedTeachingAudio(target, { supabaseRequest, fetchImpl, waitFn: async () => {} });

  assert.equal(calls.some((call) => call.path.includes('asset-1') && call.options.method === 'PATCH'), true);
  const assetDeletion = calls.findIndex((call) => call.path.includes('literacy_tts_assets?recognized_character_id=eq.recognized-1') && call.options.method === 'DELETE');
  const recordDeletion = calls.findIndex((call) => call.path.includes('recognized_characters?id=eq.recognized-1') && call.options.method === 'DELETE');
  assert.ok(assetDeletion >= 0 && recordDeletion > assetDeletion);
});

test('教学音频删除失败时不投递后台重试，交由存库回滚处理', async () => {
  const calls = [];
  const target = { id: 'recognized-1', source_literacy_character_id: null };
  const asset = {
    id: 'asset-1', object_path: 'v1/recognized/recognized-1/character.mp3', voice_version: 'v1',
    item_type: 'character', item_order: 0, root_literacy_character_id: null, recognized_character_id: 'recognized-1'
  };
  const supabaseRequest = async (path, options = {}) => {
    calls.push({ path, options });
    if (path.includes('status=neq.deleted') && path.includes('recognized_character_id=eq.recognized-1')) return [asset];
    return null;
  };
  let attempts = 0;
  const fetchImpl = async () => {
    attempts++;
    return { ok: false, status: 503, text: async () => 'temporarily unavailable' };
  };

  await assert.rejects(
    () => _private.deleteArchivedTeachingAudio(target, { supabaseRequest, fetchImpl, waitFn: async () => {} }),
    /教学音频清理失败/
  );
  assert.equal(attempts, 3);
  assert.equal(calls.some((call) => call.options.method === 'PATCH'), false);
  assert.equal(calls.some((call) => call.path.includes('recognized_characters?') && call.options.method === 'DELETE'), false);
});

test('存库音频清理失败会恢复复习数据、音频状态并删除本次新增字库记录', async () => {
  const calls = [];
  const target = {
    id: 'recognized-1', child_id: 'child-1', character: '学', knownCharacterExisted: false,
    character_audio_url: 'https://audio/character.mp3', character_audio_version: 'v1', character_audio_hash: 'hash-1',
    words: [{ text: '学习', audio_url: 'https://audio/word.mp3' }],
    sentences: [{ text: '我们学习', audio_url: 'https://audio/sentence.mp3' }],
    sourceTask: {
      id: 'task-1', character_audio_url: 'https://audio/task-character.mp3', character_audio_version: 'v1', character_audio_hash: 'hash-task',
      words: [{ text: '学习', audio_url: 'https://audio/task-word.mp3' }],
      sentences: [{ text: '我们学习', audio_url: 'https://audio/task-sentence.mp3' }]
    },
    assets: [{ id: 'asset-ready', status: 'ready' }, { id: 'asset-deleted', status: 'deleted' }]
  };
  const supabaseRequest = async (path, options = {}) => { calls.push({ path, options }); return null; };

  await _private.rollbackRecognizedArchive(target, supabaseRequest);

  const recognizedRestore = calls.find((call) => call.path.startsWith('recognized_characters?'));
  assert.match(String(recognizedRestore.options.body), /https:\/\/audio\/character\.mp3/);
  assert.equal(calls.some((call) => call.path.startsWith('child_literacy_characters?')), true);
  assert.equal(calls.some((call) => call.path.includes('asset-ready') && String(call.options.body).includes('"ready"')), true);
  assert.equal(calls.some((call) => call.path.includes('asset-deleted')), false);
  assert.equal(calls.some((call) => call.path.startsWith('known_characters?') && call.options.method === 'DELETE'), true);
});

test('回滚不会删除本次存库前已有的同字字库记录', async () => {
  const calls = [];
  await _private.rollbackRecognizedArchive({
    id: 'recognized-1', child_id: 'child-1', character: '学', knownCharacterExisted: true,
    words: [], sentences: [], assets: []
  }, async (path, options = {}) => { calls.push({ path, options }); return null; });
  assert.equal(calls.some((call) => call.path.startsWith('known_characters?') && call.options.method === 'DELETE'), false);
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
