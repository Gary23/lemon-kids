'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
Object.assign(process.env, {
  SUPABASE_URL: 'https://example.supabase.co', SUPABASE_SERVICE_ROLE_KEY: 'test-service-role',
  LITERACY_STS_SECRET_ID: 'test-id', LITERACY_STS_SECRET_KEY: 'test-key',
  LITERACY_TENCENT_APP_ID: '1', LITERACY_TENCENT_REGION: 'ap-beijing'
});
const { _private } = require('../index');
const target = { targetType: 'word', targetText: '花朵', examples: [{ text: '花朵', sortOrder: 1 }] };
const body = { attemptId: '11111111-1111-4111-8111-111111111111', itemOrder: 1,
  evaluationTextLength: 2, textMode: 0, wrongCharacterIndexes: [1],
  failureReason: ['low_pron_accuracy'], words: [{ Word: '朵', PronAccuracy: '40', MatchTag: '0' }] };

test('只接受与教学内容相符的失败评测摘要', () => {
  assert.deepEqual(_private.validateDiagnostic(body, target).wrongCharacterIndexes, [1]);
  assert.throws(() => _private.validateDiagnostic({ ...body, itemOrder: 2 }, target));
  assert.throws(() => _private.validateDiagnostic({ ...body, wrongCharacterIndexes: [2] }, target));
  assert.throws(() => _private.validateDiagnostic({ ...body, words: [{ Token: 'secret' }] }, target));
  assert.throws(() => _private.validateDiagnostic({ ...body, words: Array(81).fill({ Word: '花' }) }, target));
});

test('腾讯评测 JSON 保留层级和数值，并剔除敏感及音频字段', () => {
  const rawEvaluation = { Result: { TotalScore: 56.5, Words: [{ Word: '花', PronAccuracy: 42 }] },
    Token: 'private-token', nested: { AudioData: 'private-audio', RefText: '花朵' } };
  const diagnostic = _private.validateDiagnostic({ ...body, rawEvaluation,
    rawEvaluationStatus: 'complete' }, target);
  assert.deepEqual(diagnostic.rawEvaluation,
    { Result: rawEvaluation.Result, nested: { RefText: '花朵' } });
  assert.equal(diagnostic.rawEvaluationStatus, 'redacted');
  assert.equal(_private.validateDiagnostic({ ...body, rawEvaluationStatus: 'too_large' }, target)
    .rawEvaluationStatus, 'too_large');
  assert.equal(_private.validateDiagnostic({ ...body, rawEvaluation: { Words: ['a'.repeat(70000)] } }, target)
    .rawEvaluationStatus, 'too_large');
});

test('孩子关联哈希稳定且不暴露原始 ID', () => {
  const first = _private.diagnosticChildHash('child-1');
  assert.equal(first, _private.diagnosticChildHash('child-1'));
  assert.notEqual(first, _private.diagnosticChildHash('child-2'));
  assert.ok(!first.includes('child-1'));
});

test('查询拒绝跨家庭和非家长身份，且不触达 CLS', async () => {
  const originalFetch = global.fetch;
  const parentId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
  const childId = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
  let role = 'parent';
  global.fetch = async (url) => ({ ok: true, status: 200,
    json: async () => ({ id: parentId }),
    text: async () => JSON.stringify([
      { uid: parentId, role, family_id: 'family-1' },
      { uid: childId, role: 'child', family_id: 'family-2' }
    ])
  });
  try {
    const { main_handler } = require('../index');
    const event = { headers: { authorization: 'Bearer test' },
      body: JSON.stringify({ action: 'list_failed_reading_diagnostics', childId }) };
    assert.equal((await main_handler(event)).statusCode, 403);
    role = 'child';
    assert.equal((await main_handler(event)).statusCode, 403);
  } finally { global.fetch = originalFetch; }
});

test('CLS 查询复用现有地域和密钥，只额外要求主题 ID', async () => {
  const ClsClient = require('tencentcloud-sdk-nodejs-cls').cls.v20201016.Client;
  const originalFetch = global.fetch;
  const originalSearchLog = ClsClient.prototype.SearchLog;
  const originalConsoleError = console.error;
  const originalConsoleInfo = console.info;
  const parentId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
  const childId = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
  const event = { headers: { authorization: 'Bearer test' },
    body: JSON.stringify({ action: 'list_failed_reading_diagnostics', childId }) };
  const oldTopicId = process.env.LITERACY_CLS_TOPIC_ID;
  const calls = [];
  const errors = [];
  const infos = [];
  let searchResult = { Results: [] };
  let searchError;
  delete process.env.LITERACY_CLS_REGION;
  delete process.env.LITERACY_CLS_SECRET_ID;
  delete process.env.LITERACY_CLS_SECRET_KEY;
  global.fetch = async (url) => ({ ok: true, status: 200,
    json: async () => ({ id: parentId }),
    text: async () => JSON.stringify([
      { uid: parentId, role: 'parent', family_id: 'family-1' },
      { uid: childId, role: 'child', family_id: 'family-1' }
    ])
  });
  ClsClient.prototype.SearchLog = async function (request) {
    calls.push({ request, region: this.region, credential: this.credential });
    if (searchError) throw searchError;
    return searchResult;
  };
  console.error = value => errors.push(value);
  console.info = value => infos.push(value);
  try {
    const { main_handler } = require('../index');
    delete process.env.LITERACY_CLS_TOPIC_ID;
    const unconfigured = await main_handler(event);
    assert.equal(unconfigured.statusCode, 503);
    assert.match(JSON.parse(unconfigured.body).error, /尚未配置/);
    assert.equal(calls.length, 0);

    process.env.LITERACY_CLS_TOPIC_ID = 'topic-1';
    const configured = await main_handler(event);
    assert.equal(configured.statusCode, 200);
    assert.deepEqual(JSON.parse(configured.body).records, []);
    assert.equal(calls.length, 1);
    assert.equal(calls[0].request.TopicId, 'topic-1');
    assert.equal(calls[0].request.QueryString, 'READING_EVAL_FAILED');
    assert.equal(calls[0].request.Limit, 1000);
    assert.equal(calls[0].region, 'ap-beijing');
    assert.equal(calls[0].credential.secretId, 'test-id');
    assert.equal(calls[0].credential.secretKey, 'test-key');

    const hash = _private.diagnosticChildHash(childId);
    searchResult = { Results: [{ LogJson: JSON.stringify({ SCF_Message: JSON.stringify({
      event: 'READING_EVAL_FAILED', childHash: hash, targetType: 'word', targetText: '桂花',
      wrongCharacterIndexes: [0, 1], words: [{ Word: '桂', PronAccuracy: '8' }]
    }) }) }, { LogJson: JSON.stringify({ SCF_Message: JSON.stringify({
      event: 'READING_EVAL_FAILED', childHash: 'another-child', targetText: '私密'
    }) }) }] };
    const found = await main_handler(event);
    assert.equal(found.statusCode, 200);
    assert.equal(JSON.parse(found.body).records.length, 1);
    assert.equal(JSON.parse(found.body).records[0].targetText, '桂花');
    assert.equal(JSON.parse(found.body).records[0].childHash, undefined);

    searchResult = { Results: [{ LogJson: JSON.stringify({ SCF_Message: JSON.stringify({
      event: 'READING_EVAL_FAILED', childHash: hash, rawEvaluationStatus: 'complete',
      rawEvaluation: { Result: { PronAccuracy: 45, Words: [{ Word: '桂' }] }, SecretKey: 'private' }
    }) }) }] };
    const rawFound = JSON.parse((await main_handler(event)).body).records[0];
    assert.deepEqual(rawFound.rawEvaluation, { Result: { PronAccuracy: 45, Words: [{ Word: '桂' }] } });
    assert.equal(rawFound.rawEvaluationStatus, 'redacted');
    assert.ok(!JSON.stringify(rawFound).includes('private'));

    // CLS 结构化字段可能带有另一个顶层 event，不能因此跳过 SCF_Message。
    searchResult = { Results: [{ LogJson: JSON.stringify({ event: 'wrapper',
      SCF_Message: JSON.stringify({ event: 'READING_EVAL_FAILED', childHash: hash,
        attemptId: body.attemptId, targetType: 'word', targetText: '杉树' }) }) }] };
    const wrapped = await main_handler(event);
    assert.equal(wrapped.statusCode, 200);
    assert.equal(JSON.parse(wrapped.body).records[0].targetText, '杉树');

    searchResult = { Results: [{ LogJson: JSON.stringify({ __CONTENT__:
      `SCF_Message:${JSON.stringify({ event: 'READING_EVAL_FAILED', childHash: hash,
        attemptId: body.attemptId, targetType: 'word', targetText: '杉树' })}` }) }] };
    const contentWrapped = await main_handler(event);
    assert.equal(JSON.parse(contentWrapped.body).records[0].targetText, '杉树');

    searchResult = { Results: [{ LogJson: '{broken' }, { LogJson: JSON.stringify({
      SCF_Message: JSON.stringify({ event: 'READING_EVAL_FAILED', childHash: 'another-child' })
    }) }] };
    const empty = await main_handler(event);
    assert.deepEqual(JSON.parse(empty.body).records, []);
    assert.deepEqual(JSON.parse(infos.at(-1)), { event: 'READING_DIAGNOSTIC_CLS_EMPTY',
      returned: 2, parsed: 1, eventMatched: 1, childMatched: 0,
      shapes: { scfMessage: 1, content: 0, rootEvent: 0, other: 0 } });

    searchError = Object.assign(new Error('sensitive test-key'), {
      code: 'AuthFailure.UnauthorizedOperation', requestId: '11111111-2222-3333-4444-555555555555'
    });
    const failed = await main_handler(event);
    assert.equal(failed.statusCode, 503);
    assert.match(JSON.parse(failed.body).error, /暂不可用/);
    const clsErrors = errors.filter(value => typeof value === 'string' && value.includes('READING_DIAGNOSTIC_CLS_SEARCH_FAILED'));
    assert.equal(clsErrors.length, 1);
    assert.deepEqual(JSON.parse(clsErrors[0]), { event: 'READING_DIAGNOSTIC_CLS_SEARCH_FAILED',
      code: 'AuthFailure.UnauthorizedOperation', requestId: '11111111-2222-3333-4444-555555555555' });
    assert.ok(!clsErrors[0].includes('test-key'));
  } finally {
    global.fetch = originalFetch;
    ClsClient.prototype.SearchLog = originalSearchLog;
    console.error = originalConsoleError;
    console.info = originalConsoleInfo;
    if (oldTopicId === undefined) delete process.env.LITERACY_CLS_TOPIC_ID;
    else process.env.LITERACY_CLS_TOPIC_ID = oldTopicId;
  }
});
