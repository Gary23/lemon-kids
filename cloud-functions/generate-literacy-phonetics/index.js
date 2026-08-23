'use strict';

/**
 * 腾讯云 SCF 事件函数：认字词句音素资产后台生成。
 *
 * 只允许 SCF 定时触发器或拥有 scf:InvokeFunction 的管理端调用；不提供 Web/API Gateway。
 * 新建任务时 evaluate-reading 会即时生成，本函数以低频扫描兜底 pending 和可重试 failed 资产。
 */
const { pinyin } = require('pinyin-pro');

function requiredEnv(name) {
  const value = process.env[name];
  if (!value || !String(value).trim()) throw new Error(`缺少 SCF 环境变量 ${name}`);
  return String(value).trim();
}

function positiveInteger(value, name, maximum) {
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed <= 0 || parsed > maximum) {
    throw new Error(`${name} 必须是 1 到 ${maximum} 的整数`);
  }
  return parsed;
}

function configuration() {
  return {
    supabaseUrl: requiredEnv('SUPABASE_URL').replace(/\/$/, ''),
    supabaseServiceRoleKey: requiredEnv('SUPABASE_SERVICE_ROLE_KEY')
  };
}

function parseInvocation(event) {
  if (event && (event.httpMethod || event.requestContext)) {
    throw new InputError('该函数不接受 Web/API Gateway 请求；请通过 CAM 授权的 SCF 调用或定时触发器执行', 403);
  }
  if (!event || typeof event !== 'object') return {};
  // 腾讯云定时触发器会将控制台的“附加信息”传入 Message。
  if (typeof event.Message === 'string' && event.Message.trim()) {
    try { return JSON.parse(event.Message); }
    catch (_) { throw new InputError('定时触发器 Message 必须是 JSON 对象'); }
  }
  if (typeof event.body === 'string' && event.body.trim()) {
    try { return JSON.parse(event.body); }
    catch (_) { throw new InputError('body 必须是 JSON 对象'); }
  }
  if (event.body && typeof event.body === 'object') return event.body;
  return event;
}

function inputOptions(event) {
  const input = parseInvocation(event);
  const action = input.action || 'generate';
  if (action !== 'generate') throw new InputError('action 只能是 generate');
  return {
    limit: input.limit == null ? 50 : positiveInteger(input.limit, 'limit', 50)
  };
}

class InputError extends Error {
  constructor(message, statusCode = 400) {
    super(message);
    this.statusCode = statusCode;
  }
}

async function supabase(config, path, options = {}) {
  const response = await fetch(`${config.supabaseUrl}/rest/v1/${path}`, {
    ...options,
    headers: {
      apikey: config.supabaseServiceRoleKey,
      Authorization: `Bearer ${config.supabaseServiceRoleKey}`,
      ...(options.headers || {})
    }
  });
  const text = await response.text();
  if (!response.ok) throw new Error(`Supabase ${response.status}: ${text.slice(0, 500)}`);
  if (!text) return null;
  try { return JSON.parse(text); } catch (_) { return text; }
}

function chineseCharacters(text) {
  return [...String(text || '')].filter((character) => /[\u4E00-\u9FFF]/.test(character));
}

function phonemesForText(text) {
  const sourceCharacters = [...String(text || '')];
  const generated = pinyin(text, { toneType: 'num', type: 'array', v: true });
  if (!Array.isArray(generated) || generated.length !== sourceCharacters.length) {
    throw new Error('词组拼音词典返回长度与原文不一致');
  }
  const tokens = [];
  sourceCharacters.forEach((character, index) => {
    if (!/[\u4E00-\u9FFF]/.test(character)) return;
    const token = typeof generated[index] === 'string' ? generated[index].toLowerCase() : '';
    // pinyin-pro 用 0 表示轻声；腾讯不支持指定轻声，因此保留 null 位置。
    if (/^[a-zv]+0$/.test(token)) tokens.push(null);
    else if (/^[a-zv]+[1-4]$/.test(token)) tokens.push(token);
    else throw new Error(`词典无法为“${character}”生成腾讯支持的数字拼音`);
  });
  if (tokens.length !== chineseCharacters(text).length) throw new Error('音素数量与汉字数量不一致');
  return tokens;
}

async function generatePhoneticAssets(config, limit) {
  const assets = await supabase(config, 'rpc/claim_literacy_phonetic_assets', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ p_limit: limit })
  });
  const result = { claimed: (assets || []).length, ready: 0, failed: 0 };
  for (const asset of assets || []) {
    try {
      await supabase(config, 'rpc/complete_literacy_phonetic_asset', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          p_asset_id: asset.id,
          p_phoneme_tokens: phonemesForText(asset.item_text),
          p_generator_version: 'pinyin-pro-3.27.0'
        })
      });
      result.ready += 1;
    } catch (error) {
      await supabase(config, 'rpc/fail_literacy_phonetic_asset', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ p_asset_id: asset.id, p_reason: String(error.message || error).slice(0, 1000) })
      });
      result.failed += 1;
    }
  }
  return result;
}

async function mainHandler(event) {
  const options = inputOptions(event);
  const generated = await generatePhoneticAssets(configuration(), options.limit);
  console.info('音素资产定时回填完成', generated);
  return { generated };
}

exports.main_handler = mainHandler;
exports._private = {
  chineseCharacters,
  configuration,
  generatePhoneticAssets,
  inputOptions,
  parseInvocation,
  phonemesForText
};
