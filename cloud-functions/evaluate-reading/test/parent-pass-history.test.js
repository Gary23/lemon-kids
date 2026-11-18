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

const { main_handler, _private } = require('../index');
const childId = '11111111-1111-4111-8111-111111111111';
const recordId = '22222222-2222-4222-8222-222222222222';
const character = {
  id: 'character-1', child_id: childId, family_id: 'family-1', character: '春',
  // 空项被过滤后仍必须使用原始 sortOrder，而不是快照数组下标。
  words: ['', { text: '春天' }, '', { text: '春风' }],
  sentences: [{ text: '春天来了' }]
};

function snapshot(stored = false) {
  return {
    character: { text: '春', earned: 2, required: 2, ...(stored ? { storedEarned: 3 } : {}) },
    words: [
      { text: '春天', earned: 1, required: 1, ...(stored ? { storedEarned: 2 } : {}) },
      { text: '春风', earned: 0, required: 1 }
    ],
    sentences: [{ text: '春天来了', earned: 0, required: 1 }]
  };
}

function mockDatabase(contentSource, initialSnapshot, initialProgress = [], failProgress = false) {
  const previousFetch = global.fetch;
  const calls = [];
  let record = initialSnapshot ? {
    id: recordId, literacy_character_id: character.id, content_source: contentSource,
    character: character.character, star_snapshot: initialSnapshot, passed_at: new Date().toISOString(), undone_at: null
  } : null;
  let progress = initialProgress;
  const progressWrites = [];
  global.fetch = async (url, options = {}) => {
    const parsed = new URL(url);
    const path = parsed.pathname;
    const query = parsed.searchParams;
    const method = options.method || 'GET';
    calls.push({ url: String(url), method });
    if (path === '/auth/v1/user') return Response.json({ id: childId });
    assert.equal(query.get('child_id') || (method === 'POST' ? `eq.${childId}` : null), `eq.${childId}`);
    if (path.endsWith('/child_literacy_characters') || path.endsWith('/recognized_characters')) {
      assert.equal(query.get('id'), `eq.${character.id}`);
      assert.equal(path.endsWith('/recognized_characters'), contentSource === 'recognized');
      return Response.json([character]);
    }
    if (path.endsWith('/child_literacy_practice_progress')) {
      if (method === 'GET') {
        assert.match(query.get('progress_date'), /^eq\.\d{4}-\d{2}-\d{2}$/);
        if (failProgress) return new Response('unavailable', { status: 503 });
        return Response.json(progress);
      }
      assert.equal(method, 'POST');
      const entries = JSON.parse(options.body);
      progressWrites.push(entries);
      progress = entries;
      return new Response(null, { status: 204 });
    }
    if (path.endsWith('/literacy_parent_pass_records')) {
      if (method === 'POST') {
        record = { ...JSON.parse(options.body), id: recordId, passed_at: new Date().toISOString(), undone_at: null };
        return new Response(null, { status: 204 });
      }
      if (method === 'PATCH') {
        record = { ...record, ...JSON.parse(options.body) };
        return Response.json([record]);
      }
      if (query.has('passed_at')) return Response.json([]);
      if (query.has('undone_at') && query.get('undone_at').startsWith('gte.')) {
        return Response.json(record?.undone_at ? [record] : []);
      }
      return Response.json(record ? [record] : []);
    }
    assert.fail(`unexpected request: ${url}`);
  };
  return {
    calls, progressWrites, getRecord: () => record,
    restore: () => { global.fetch = previousFetch; }
  };
}

async function action(body) {
  const result = await main_handler({ headers: { Authorization: 'Bearer test-token' }, body: JSON.stringify(body) });
  return { status: result.statusCode, body: JSON.parse(result.body) };
}

function counts(entries) {
  return entries.map((row) => [row.target_type || row.targetType, row.item_order ?? row.itemOrder,
    row.correct_readings ?? row.correctReadings]);
}

test('storedEarned 可高于展示门槛，缺失字段保持旧快照结构', () => {
  const old = _private.normalizeParentPassStarSnapshot(snapshot(), character);
  assert.equal(Object.hasOwn(old.character, 'storedEarned'), false);
  const normalized = _private.normalizeParentPassStarSnapshot(snapshot(true), character);
  assert.deepEqual(normalized.character, { text: '春', earned: 2, required: 2, storedEarned: 3 });
  assert.deepEqual(normalized.words[0], { text: '春天', earned: 1, required: 1, storedEarned: 2 });
  const zero = snapshot();
  zero.words[1].storedEarned = 0;
  assert.equal(_private.normalizeParentPassStarSnapshot(zero, character).words[1].storedEarned, 0);
});

test('所有快照状态严格校验 storedEarned 整数 0..3 且不低于 earned', () => {
  for (const field of ['character', 'word', 'sentence']) {
    for (const invalid of [-1, 4, 1.5, '2', null, NaN, Infinity]) {
      const input = snapshot();
      const state = field === 'character' ? input.character : field === 'word' ? input.words[0] : input.sentences[0];
      state.storedEarned = invalid;
      assert.throws(() => _private.normalizeParentPassStarSnapshot(input, character), /历史次数不合法/);
    }
  }
  const input = snapshot();
  input.character.storedEarned = 1;
  assert.throws(() => _private.normalizeParentPassStarSnapshot(input, character), /历史次数不合法/);
  input.character = { text: '春', earned: 3, required: 2, storedEarned: 3 };
  assert.throws(() => _private.normalizeParentPassStarSnapshot(input, character), /数值不合法/);
});

for (const contentSource of ['task', 'recognized']) {
  test(`${contentSource} 通过保存较高历史次数，撤销和同步精确回退而不保留补满星`, async () => {
    const row = (targetType, itemOrder, correctReadings, extras = {}) => ({
      content_source: contentSource, literacy_character_id: character.id,
      target_type: targetType, item_order: itemOrder, correct_readings: correctReadings, ...extras
    });
    const input = snapshot();
    // 两端各自可能持有较高值：recognized 主字以客户端 3 为准，task 主字由服务器恢复 3。
    input.character.storedEarned = contentSource === 'recognized' ? 3 : 2;
    input.words[0].storedEarned = 1;
    if (contentSource === 'recognized') input.sentences = [];
    const mock = mockDatabase(contentSource, null, [
      row('character', 0, contentSource === 'recognized' ? 2 : 3), row('word', 1, 2),
      row('word', 0, 3), row('word', 2, 3), row('word', '3', 3),
      row('sentence', 3, 3), row('word', 3, 3, { content_source: contentSource === 'task' ? 'recognized' : 'task' }),
      row('word', 3, 3, { literacy_character_id: 'other-character' })
    ]);
    try {
      const passed = await action({ action: 'record_parent_pass', contentSource, literacyCharacterId: character.id, starSnapshot: input });
      assert.equal(passed.status, 201);
      const audit = mock.getRecord().star_snapshot;
      assert.deepEqual(audit.character, { text: '春', earned: 2, required: 2, storedEarned: 3 });
      assert.deepEqual(audit.words[0], { text: '春天', earned: 1, required: 1, storedEarned: 2 });
      assert.equal(audit.words[1].storedEarned, 0);
      const filled = [['character', 0, 3], ['word', 1, 2], ['word', 3, 1]];
      const restored = [['character', 0, 3], ['word', 1, 2], ['word', 3, 0]];
      if (contentSource === 'task') {
        filled.push(['sentence', 0, 1]);
        restored.push(['sentence', 0, 0]);
      }
      assert.deepEqual(counts(mock.progressWrites[0]), filled);
      const undone = await action({ action: 'undo_parent_pass', recordId });
      assert.equal(undone.status, 200);
      assert.ok(mock.getRecord().undone_at);
      assert.deepEqual(counts(mock.progressWrites[1]), restored);
      const synced = await action({ action: 'get_literacy_practice_progress' });
      assert.equal(synced.status, 200);
      assert.deepEqual(counts(synced.body.resets), restored);
      assert.deepEqual(counts(synced.body.progress), restored);
      assert.ok(synced.body.resets.every((entry) => !Object.hasOwn(entry, 'childId')));
      // 撤销本身没有读取当前进度来取最大值；只有通过前和随后同步各读取一次。
      assert.equal(mock.calls.filter((call) => call.url.includes('/child_literacy_practice_progress?') && call.method === 'GET').length, 2);
    } finally { mock.restore(); }
  });
}

test('旧审计快照撤销与同步仍恢复 earned，不用当前补满值覆盖', async () => {
  const old = snapshot();
  const mock = mockDatabase('task', old);
  try {
    assert.equal((await action({ action: 'undo_parent_pass', recordId })).status, 200);
    const expected = [['character', 0, 2], ['word', 1, 1], ['word', 3, 0], ['sentence', 0, 0]];
    assert.deepEqual(counts(mock.progressWrites[0]), expected);
    const synced = await action({ action: 'get_literacy_practice_progress' });
    assert.equal(synced.status, 200);
    assert.deepEqual(counts(synced.body.resets), expected);
    assert.equal(Object.hasOwn(mock.getRecord().star_snapshot.character, 'storedEarned'), false);
  } finally { mock.restore(); }
});

test('旧客户端未提供 storedEarned 仍合并云端历史，读取失败时不写审计或进度', async () => {
  const mock = mockDatabase('task', null, [], true);
  try {
    const result = await action({ action: 'record_parent_pass', contentSource: 'task', literacyCharacterId: character.id, starSnapshot: snapshot() });
    assert.equal(result.status, 500);
    assert.match(result.body.error, /Supabase 503/);
    assert.equal(mock.getRecord(), null);
    assert.equal(mock.progressWrites.length, 0);
  } finally { mock.restore(); }
});

test('旧客户端通过时从云端补充 storedEarned，仍保留封顶展示值', async () => {
  const mock = mockDatabase('task', null, [{
    content_source: 'task', literacy_character_id: character.id,
    target_type: 'word', item_order: 1, correct_readings: 2
  }]);
  try {
    const result = await action({ action: 'record_parent_pass', contentSource: 'task', literacyCharacterId: character.id, starSnapshot: snapshot() });
    assert.equal(result.status, 201);
    assert.deepEqual(mock.getRecord().star_snapshot.words[0], { text: '春天', earned: 1, required: 1, storedEarned: 2 });
    assert.equal(mock.progressWrites[0][1].correct_readings, 2);
  } finally { mock.restore(); }
});
