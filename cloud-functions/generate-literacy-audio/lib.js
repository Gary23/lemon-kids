'use strict';

const AUDIO_BUCKET = 'literacy-audio';

function requiredEnv(name) {
  const value = process.env[name];
  if (!value || !String(value).trim()) throw new Error(`缺少 SCF 环境变量 ${name}`);
  return String(value).trim();
}

function positiveInteger(value, name, maximum = Number.MAX_SAFE_INTEGER) {
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed <= 0 || parsed > maximum) {
    throw new Error(`${name} 必须是 1 到 ${maximum} 的整数`);
  }
  return parsed;
}

function parseInvocation(event) {
  if (event && (event.httpMethod || event.requestContext)) {
    throw new InputError('该函数不接受 Web/API Gateway 请求；请通过 CAM 授权的 SCF 调用或定时触发器执行', 403);
  }
  if (!event || typeof event !== 'object') return {};
  // 腾讯云定时触发器把控制台配置的“附加信息”放进 Message；解析后仍复用
  // 同一套事件函数契约，例如 {"action":"cleanup"}。
  if (typeof event.Message === 'string' && event.Message.trim()) {
    try { return JSON.parse(event.Message); }
    catch (_) { throw new InputError('定时触发器 Message 必须是 JSON 对象'); }
  }
  if (typeof event.body === 'string') {
    try { return JSON.parse(event.body); } catch (_) { throw new InputError('body 必须是 JSON 对象'); }
  }
  if (event.body && typeof event.body === 'object') return event.body;
  return event;
}

function inputOptions(event) {
  const input = parseInvocation(event);
  const action = input.action || 'generate';
  if (!['generate', 'cleanup', 'reconcile', 'monitor'].includes(action)) {
    throw new InputError('action 只能是 generate、cleanup、reconcile 或 monitor');
  }
  if (action === 'monitor') return { action };
  if (action !== 'generate') {
    return {
      action,
      limit: input.limit == null ? (action === 'reconcile' ? 1000 : 50) : positiveInteger(input.limit, 'limit', action === 'reconcile' ? 5000 : 50),
      concurrency: input.concurrency == null ? 3 : positiveInteger(input.concurrency, 'concurrency', 5)
    };
  }
  const source = input.source || 'task';
  if (!['task', 'recognized'].includes(source)) {
    throw new InputError('source 只能是 task 或 recognized');
  }
  if (input.record_id != null && (typeof input.record_id !== 'string' || !input.record_id.trim())) {
    throw new InputError('record_id 必须是非空 UUID 字符串');
  }
  if (input.only_missing_or_invalid === false) {
    throw new InputError('为保护已缓存版本，只支持 only_missing_or_invalid=true；需重新合成时请提高音色版本后生成新资产');
  }
  return {
    action,
    dryRun: input.dry_run === true,
    source,
    recordId: input.record_id || null,
    limit: input.limit == null ? 50 : positiveInteger(input.limit, 'limit', 50),
    concurrency: input.concurrency == null ? 3 : positiveInteger(input.concurrency, 'concurrency', 5),
    retryFailed: input.retry_failed === true,
    // 只抢占 pending/failed 队列；版本升级应创建新资产，不能覆盖 ready 文件。
    onlyMissingOrInvalid: true
  };
}

function objectPathForAsset(asset) {
  const filename = asset.item_type === 'character'
    ? 'character.mp3'
    : `${asset.item_type}-${asset.item_order}.mp3`;
  if (asset.root_literacy_character_id) {
    return `${asset.voice_version}/task/${asset.root_literacy_character_id}/${filename}`;
  }
  if (asset.recognized_character_id) {
    return `${asset.voice_version}/recognized/${asset.recognized_character_id}/${filename}`;
  }
  throw new Error(`资产 ${asset.id} 缺少归属记录`);
}

function encodeObjectPath(path) {
  return path.split('/').map(encodeURIComponent).join('/');
}

function publicAudioUrl(supabaseUrl, objectPath) {
  return `${supabaseUrl.replace(/\/$/, '')}/storage/v1/object/public/${AUDIO_BUCKET}/${encodeObjectPath(objectPath)}`;
}

function isMp3(buffer) {
  if (!Buffer.isBuffer(buffer) || buffer.length < 4) return false;
  // 常见 MP3 有 ID3 标签；无标签的帧以 11 个同步位开始。
  return buffer.subarray(0, 3).toString('ascii') === 'ID3'
    || (buffer[0] === 0xff && (buffer[1] & 0xe0) === 0xe0);
}

function characterCount(text) {
  return [...String(text || '')].length;
}

function sanitizeError(error) {
  const message = String(error?.message || error || '未知错误')
    .replace(/Bearer\s+[^\s,;]+/ig, 'Bearer [已隐藏]')
    .replace(/(secret(?:id|key)?|token|apikey)\s*[=:]\s*[^\s,;]+/ig, '$1=[已隐藏]');
  return message.slice(0, 1000);
}

function isRetryable(error) {
  const code = String(error?.code || error?.Code || '');
  const status = Number(error?.statusCode || error?.status || 0);
  if (status === 429 || status >= 500) return true;
  return /^(ECONNRESET|ECONNREFUSED|ETIMEDOUT|EAI_AGAIN|ENOTFOUND|RequestLimitExceeded|InternalError|FailedOperation)/i.test(code);
}

class InputError extends Error {
  constructor(message, statusCode = 400) {
    super(message);
    this.statusCode = statusCode;
  }
}

class DailyLimitError extends Error {
  constructor() {
    super('已达到当天腾讯 TTS 字符硬上限，资产已留待次日处理');
  }
}

module.exports = {
  AUDIO_BUCKET,
  DailyLimitError,
  InputError,
  characterCount,
  encodeObjectPath,
  inputOptions,
  isMp3,
  isRetryable,
  objectPathForAsset,
  publicAudioUrl,
  requiredEnv,
  sanitizeError,
  positiveInteger
};
