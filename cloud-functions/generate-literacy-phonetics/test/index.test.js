'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { _private } = require('../index');

test('定时触发器从 Message 读取生成参数，且限制单批大小', () => {
  assert.deepEqual(_private.inputOptions({ Message: '{"action":"generate","limit":30}' }), { limit: 30 });
  assert.deepEqual(_private.inputOptions({}), { limit: 50 });
  assert.throws(() => _private.inputOptions({ Message: '{"limit":51}' }), /limit/);
});

test('事件函数拒绝 Web 请求，避免暴露 service_role 队列', () => {
  assert.throws(() => _private.inputOptions({ httpMethod: 'POST', body: '{}' }), /不接受 Web/);
});

test('生成器与评测函数一致地处理多音字和轻声', () => {
  assert.deepEqual(_private.phonemesForText('组长'), ['zu3', 'zhang3']);
  assert.deepEqual(_private.phonemesForText('妈妈'), ['ma1', null]);
});
