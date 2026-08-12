'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { _private } = require('../index');

const config = {
  supabaseUrl: 'https://project.supabase.co',
  supabaseServiceRoleKey: 'test-service-role',
  voiceType: 601009,
  modelType: 1,
  codec: 'mp3',
  sampleRate: 16000,
  voiceVersion: 'v1',
  dailyCharacterLimit: 10000
};

function response(body = '', options = {}) {
  return new Response(body, { status: options.status || 200, headers: options.headers });
}

async function withFetchMock(mock, work) {
  const originalFetch = global.fetch;
  global.fetch = mock;
  try {
    return await work();
  } finally {
    global.fetch = originalFetch;
  }
}

test('待生成筛选只读取指定来源的 pending 资产', async () => {
  const urls = [];
  await withFetchMock(async (url) => {
    urls.push(String(url));
    return response(JSON.stringify([{ id: 'asset-1', source_text: '木' }]), {
      headers: { 'content-type': 'application/json' }
    });
  }, async () => {
    const candidates = await _private.listCandidates(config, {
      source: 'task', recordId: 'task-1', retryFailed: false
    });
    assert.equal(candidates.length, 1);
  });
  assert.match(urls[0], /root_literacy_character_id=not\.is\.null/);
  assert.match(urls[0], /root_literacy_character_id=eq\.task-1/);
  assert.match(urls[0], /status=eq\.pending/);
});

test('合成严格使用原始文本，并在对象校验后回写 ready', async () => {
  const calls = [];
  const asset = {
    id: 'asset-1', source_text: '院子里有一棵大树。', item_type: 'sentence', item_order: 0,
    speed: -1, voice_version: 'v1', root_literacy_character_id: 'task-1', recognized_character_id: null
  };
  const client = {
    async TextToVoice(request) {
      calls.push({ type: 'tts', request });
      return { Audio: Buffer.from([0x49, 0x44, 0x33, 0x04]).toString('base64'), RequestId: 'request-1' };
    }
  };

  await withFetchMock(async (url, options = {}) => {
    const target = String(url);
    calls.push({ type: 'fetch', target, options });
    if (target.includes('reserve_literacy_tts_characters')) return response('true');
    if (target.includes('/storage/v1/object/literacy-audio/') && options.method === 'POST') return response('{}', { status: 201 });
    if (target.includes('/storage/v1/object/literacy-audio/') && options.method === 'HEAD') {
      return response('', { headers: { 'content-type': 'audio/mpeg', 'content-length': '4' } });
    }
    if (target.includes('mark_literacy_tts_asset_ready')) return response('{}');
    throw new Error(`未预期请求：${target}`);
  }, async () => {
    const result = await _private.processAsset(config, client, asset);
    assert.deepEqual(result, { status: 'ready', assetId: 'asset-1', characters: 9 });
  });

  const tts = calls.find((call) => call.type === 'tts');
  assert.deepEqual(tts.request, {
    Text: '院子里有一棵大树。', SessionId: 'asset-1', VoiceType: 601009, ModelType: 1,
    Codec: 'mp3', SampleRate: 16000, Speed: -1, EmotionCategory: 'neutral', EmotionIntensity: 100
  });
  const ready = calls.find((call) => call.type === 'fetch' && call.target.includes('mark_literacy_tts_asset_ready'));
  assert.match(ready.options.body, /v1\/task\/task-1\/sentence-0\.mp3/);
});

test('腾讯临时错误按指数退避重试，永久错误不重试', async () => {
  let temporaryAttempts = 0;
  const waits = [];
  const value = await _private.retry(async () => {
    temporaryAttempts += 1;
    if (temporaryAttempts < 3) {
      const error = new Error('temporary');
      error.code = 'ETIMEDOUT';
      throw error;
    }
    return 'ok';
  }, async (milliseconds) => waits.push(milliseconds));
  assert.equal(value, 'ok');
  assert.equal(temporaryAttempts, 3);
  assert.deepEqual(waits, [250, 500]);

  let permanentAttempts = 0;
  await assert.rejects(() => _private.retry(async () => {
    permanentAttempts += 1;
    const error = new Error('invalid request');
    error.status = 400;
    throw error;
  }, async () => {}));
  assert.equal(permanentAttempts, 1);
});

test('清理仅删除该资产对象；删除失败会投递可重试任务', async () => {
  const calls = [];
  const asset = {
    id: 'asset-1', object_path: 'v1/task/task-1/word-0.mp3', item_type: 'word', item_order: 0,
    voice_version: 'v1', root_literacy_character_id: 'task-1', recognized_character_id: null
  };
  await withFetchMock(async (url, options = {}) => {
    const target = String(url);
    calls.push({ target, options });
    if (target.includes('/storage/v1/object/literacy-audio/')) return response('upstream unavailable', { status: 503 });
    if (target.includes('defer_literacy_tts_asset_deletion')) return response('');
    throw new Error(`未预期请求：${target}`);
  }, async () => {
    const result = await _private.processDeletion(config, asset);
    assert.equal(result.status, 'failed');
  });
  const deletes = calls.filter((call) => call.options.method === 'DELETE');
  assert.equal(deletes.length, 3);
  assert.match(deletes[0].target, /v1\/task\/task-1\/word-0\.mp3$/);
  assert.equal(calls.some((call) => call.target.includes('defer_literacy_tts_asset_deletion')), true);
});

test('监控快照输出积压、清理时长和字符消耗，不触发腾讯合成', async () => {
  const logs = [];
  const originalInfo = console.info;
  console.info = (value) => logs.push(value);
  try {
    await withFetchMock(async (url) => {
      const target = String(url);
      if (target.includes('literacy_tts_assets')) {
        return response(JSON.stringify([
          { status: 'pending', created_at: '2026-08-07T00:00:00.000Z', updated_at: '2026-08-07T00:00:00.000Z' },
          { status: 'delete_pending', created_at: '2026-08-07T00:00:00.000Z', updated_at: '2026-08-07T00:00:00.000Z' }
        ]), { headers: { 'content-type': 'application/json' } });
      }
      if (target.includes('literacy_tts_daily_usage')) {
        return response(JSON.stringify([{ usage_date: '2026-08-07', character_count: 569 }]), {
          headers: { 'content-type': 'application/json' }
        });
      }
      throw new Error(`未预期请求：${target}`);
    }, async () => {
      const snapshot = await _private.monitor(config);
      assert.equal(snapshot.pending_assets, 1);
      assert.equal(snapshot.cleanup_pending_assets, 1);
      assert.equal(snapshot.daily_tts_character_count, 569);
    });
  } finally {
    console.info = originalInfo;
  }
  assert.match(logs[0], /literacy_tts_monitor_snapshot/);
});
