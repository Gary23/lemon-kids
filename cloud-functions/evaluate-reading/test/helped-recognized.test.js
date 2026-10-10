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
const child = '11111111-1111-4111-8111-111111111111';
const helpId = '22222222-2222-4222-8222-222222222222';

function mockSupabase(routes) {
  const calls = [];
  const previous = global.fetch;
  global.fetch = async (url, options) => {
    const path = String(url).split('/rest/v1/')[1] || String(url);
    calls.push({ path, options });
    const route = routes.find(([prefix]) => path.startsWith(prefix));
    assert.ok(route, `unexpected request: ${path}`);
    return new Response(JSON.stringify(route[1]), { status: 200 });
  };
  return { calls, restore: () => { global.fetch = previous; } };
}

test('历史词句只从当前孩子的目标字查询并反显', async () => {
  const mock = mockSupabase([
    ['child_literacy_character_help_requests?', [{ id: helpId, requested_character: '春', character_index: 5, target_text: '春天' }]],
    ['recognized_characters?', []],
    ['child_literacy_characters?', [
      { words: [{ text: '夏天' }], sentences: [{ text: '夏天来了' }] },
      { words: [{ text: '春天' }], sentences: [{ text: '春天来了' }] }
    ]],
    ['known_characters?', []]
  ]);
  try {
    const preview = await _private.previewHelpedCharacter(child, helpId);
    assert.equal(preview.tasks[0].words[0].text, '春天');
    assert.equal(preview.tasks[0].sentence.text, '春天来了');
    assert.ok(mock.calls.every(({ path }) => path.includes(encodeURIComponent(child))));
  } finally { mock.restore(); }
});

test('历史词句不完整时调用 DeepSeek 生成目标字', async () => {
  const generated = { items: [{ character: '春', words: [{ text: '春天' }], sentence: { text: '春天' } }] };
  const mock = mockSupabase([
    ['child_literacy_character_help_requests?', [{ id: helpId, requested_character: '春', character_index: 0, target_text: '春天' }]],
    ['recognized_characters?', []],
    ['child_literacy_characters?', [{ words: [{ text: '春天' }], sentences: [] }]],
    ['known_characters?', [{ character: '天' }]],
    ['https://api.deepseek.com/', { choices: [{ message: { content: JSON.stringify(generated) } }] }]
  ]);
  try {
    const preview = await _private.previewHelpedCharacter(child, helpId);
    assert.equal(preview.tasks[0].sentence.text, '春天');
    assert.equal(mock.calls.filter(({ path }) => path.startsWith('https://api.deepseek.com/')).length, 1);
  } finally { mock.restore(); }
});

test('目标字无法在帮助文本中唯一定位时拒绝预览', async () => {
  const mock = mockSupabase([
    ['child_literacy_character_help_requests?', [{ id: helpId, requested_character: '春', character_index: 9, target_text: '春春' }]]
  ]);
  try {
    await assert.rejects(_private.previewHelpedCharacter(child, helpId), /无法定位/);
    assert.equal(mock.calls.length, 1);
  } finally { mock.restore(); }
});

test('可复用历史须有属于目标字的词和句', () => {
  assert.equal(_private.reusableHelpTask({ words: [{ text: '夏天' }], sentences: [{ text: '春天' }] }, '春'), null);
  assert.equal(_private.reusableHelpTask({ words: [{ text: '春天' }], sentences: [] }, '春'), null);
});

test('帮助入口区分已认识与仅在字库的字，查询限定当前孩子', async () => {
  const mock = mockSupabase([
    ['child_literacy_character_help_requests?', [{ id: helpId, requested_character: '春', character_index: 0, target_text: '春天' }]],
    ['recognized_characters?', []]
  ]);
  try {
    assert.deepEqual(await _private.checkHelpedCharacter(child, helpId), {
      character: '春', alreadyRecognized: false
    });
    assert.equal(mock.calls.length, 2);
    assert.ok(mock.calls.every(({ path }) => path.includes(encodeURIComponent(child))));
    assert.ok(mock.calls.every(({ path }) => !path.startsWith('known_characters?')));
  } finally { mock.restore(); }
});

test('帮助入口已认识时直接提示，不请求历史词句或 DeepSeek', async () => {
  const mock = mockSupabase([
    ['child_literacy_character_help_requests?', [{ id: helpId, requested_character: '春', character_index: 0, target_text: '春天' }]],
    ['recognized_characters?', [{ id: '33333333-3333-4333-8333-333333333333' }]]
  ]);
  try {
    assert.equal((await _private.checkHelpedCharacter(child, helpId)).alreadyRecognized, true);
    assert.equal(mock.calls.length, 2);
  } finally { mock.restore(); }
});

test('保存时发现已认识字则拒绝，且不写入或置顶', async () => {
  const mock = mockSupabase([
    ['child_literacy_character_help_requests?', [{ id: helpId, requested_character: '春', character_index: 0, target_text: '春天' }]],
    ['recognized_characters?', [{ id: '33333333-3333-4333-8333-333333333333' }]]
  ]);
  try {
    await assert.rejects(_private.saveHelpedCharacter(child, helpId,
      [{ character: '春', words: [{ text: '春天' }], sentence: { text: '春天' } }]), /无需重复添加/);
    assert.equal(mock.calls.length, 2);
  } finally { mock.restore(); }
});

test('保存前重新校验目标字，错误词句不触发写入', async () => {
  const mock = mockSupabase([
    ['child_literacy_character_help_requests?', [{ id: helpId, requested_character: '春', character_index: 0, target_text: '春天' }]],
    ['recognized_characters?', []],
    ['known_characters?', []]
  ]);
  try {
    await assert.rejects(_private.saveHelpedCharacter(child, helpId,
      [{ character: '春', words: [{ text: '夏天' }], sentence: { text: '春天' } }]), /必须包含该字/);
    assert.ok(mock.calls.every(({ options }) => !options.method || options.method === 'GET'));
  } finally { mock.restore(); }
});
