'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');

Object.assign(process.env, {
  SUPABASE_URL: 'https://example.supabase.co',
  SUPABASE_SERVICE_ROLE_KEY: 'test-service-role',
  LITERACY_STS_SECRET_ID: 'test-id',
  LITERACY_STS_SECRET_KEY: 'test-key',
  LITERACY_TENCENT_APP_ID: '1',
  LITERACY_TENCENT_REGION: 'ap-beijing',
  DEEPSEEK_API_KEY: 'test-deepseek'
});

const { _private } = require('../index');
const childId = '11111111-1111-4111-8111-111111111111';
const baseItem = { character: '春', words: [{ text: '春天' }], sentence: { text: '春天来了' } };

async function withPreviewMock(item, run) {
  const originalFetch = global.fetch;
  const originalWarn = console.warn;
  const requests = [];
  const warnings = [];
  console.warn = (...values) => warnings.push(values.join(' '));
  global.fetch = async (url, options) => {
    const path = String(url);
    if (path.includes('api.deepseek.com')) {
      requests.push(JSON.parse(options.body));
      return new Response(JSON.stringify({ choices: [{ message: { content: JSON.stringify({ items: [item] }) } }] }), { status: 200 });
    }
    if (path.includes('known_characters?')) return new Response(JSON.stringify([{ character: '天' }, { character: '来' }, { character: '了' }]));
    if (path.includes('child_literacy_characters?') || path.includes('recognized_characters?')) return new Response('[]');
    throw new Error(`unexpected request: ${path}`);
  };
  try { await run({ requests, warnings }); }
  finally { global.fetch = originalFetch; console.warn = originalWarn; }
}

test('预览接受对象句子并保持返回结构', async () => {
  await withPreviewMock(baseItem, async ({ requests }) => {
    const preview = await _private.previewGeneratedLiteracyTasks(childId, '春');
    assert.deepEqual(preview.tasks[0].sentence, { text: '春天来了' });
    assert.equal(requests.length, 1);
  });
});

test('预览接受字符串句子并归一化为对象', async () => {
  await withPreviewMock({ ...baseItem, sentence: ' 春天来了 ' }, async ({ requests }) => {
    const preview = await _private.previewGeneratedLiteracyTasks(childId, '春');
    assert.deepEqual(preview.tasks[0].sentence, { text: '春天来了' });
    assert.equal(requests.length, 1);
  });
});

test('缺失句子重试后返回格式错误，诊断只记录类型', async () => {
  await withPreviewMock({ character: '春', words: baseItem.words }, async ({ requests, warnings }) => {
    await assert.rejects(_private.previewGeneratedLiteracyTasks(childId, '春'),
      (error) => error.statusCode === 422 && /格式不正确/.test(error.message));
    assert.equal(requests.length, 10);
    assert.match(requests[1].messages[0].content, /句子必须使用 \{"text":"完整句子"\} 对象/);
    assert.ok(warnings.some((message) => message.includes('字段类型=undefined')));
    assert.ok(warnings.every((message) => !message.includes('春天')));
  });
});

test('字符串句子仍须包含目标字且只允许指定字符', async () => {
  for (const sentence of ['夏天来了', '春天A']) {
    await withPreviewMock({ ...baseItem, sentence }, async ({ requests }) => {
      await assert.rejects(_private.previewGeneratedLiteracyTasks(childId, '春'), /字库范围/);
      assert.equal(requests.length, 10);
    });
  }
});

test('句子字库越界仍被拒绝', async () => {
  await withPreviewMock({ ...baseItem, sentence: '春夏秋冬好' }, async ({ requests }) => {
    await assert.rejects(_private.previewGeneratedLiteracyTasks(childId, '春'), /字库范围/);
    assert.equal(requests.length, 10);
  });
});

test('保存接口仍拒绝字符串句子', () => {
  assert.throws(() => _private.validateGeneratedTasks(
    { items: [{ ...baseItem, sentence: '春天来了' }] }, ['春'], new Set(['春', '天', '来', '了'])
  ), /格式不正确/);
});
