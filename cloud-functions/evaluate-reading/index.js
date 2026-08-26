'use strict';

/**
 * 腾讯云 SCF Web 函数：认字口语评测的可信服务端。
 *
 * 路由由 JSON body.action 区分：
 * - issue_credentials：验证 Supabase JWT 后签发短期腾讯临时凭证。凭证在认字页内
 *   复用，参考文本仍由客户端从已授权读取的教学内容中按当前字、词、句单独传给腾讯。
 * - issue_session：旧版客户端兼容接口；同时校验指定教学内容并签发短期凭证。
 * - record_help_request：按被长按的字是否属于对应字库，记录孩子请求朗读的动作。
 * - complete_literacy_character：本地完成字、词、句练习后，按主字是否点读转入已认识字表或字库。
 * - archive_recognized_character：将一条已认识字存入字库，并移除其复习卡。
 * - preview_literacy_tasks：基于字库和输入汉字生成可编辑的词、句预览。
 * - save_literacy_tasks：校验家长确认后的预览内容、创建待认识任务，并异步投递音频生成。
 *
 * 所有朗读文字均以 Supabase 数据为准；绝不信任客户端上传的内容。
 */
const tencentcloud = require('tencentcloud-sdk-nodejs-sts');
const StsClient = tencentcloud.sts.v20180813.Client;
const scf = require('tencentcloud-sdk-nodejs-scf');
const ScfClient = scf.scf.v20180416.Client;
const { pinyin } = require('pinyin-pro');
const crypto = require('crypto');

const SUPABASE_URL = requiredEnv('SUPABASE_URL').replace(/\/$/, '');
const SUPABASE_SERVICE_ROLE_KEY = requiredEnv('SUPABASE_SERVICE_ROLE_KEY');
const STS_SECRET_ID = requiredEnv('LITERACY_STS_SECRET_ID');
const STS_SECRET_KEY = requiredEnv('LITERACY_STS_SECRET_KEY');
const TENCENT_APP_ID = Number(requiredEnv('LITERACY_TENCENT_APP_ID'));
const TENCENT_REGION = requiredEnv('LITERACY_TENCENT_REGION');
const DEEPSEEK_API_URL = 'https://api.deepseek.com/chat/completions';
// 识字内容固定使用 Flash 模型，避免函数环境变量意外切换到 Pro 模型。
const DEEPSEEK_MODEL = 'deepseek-v4-flash';
const MAX_GENERATED_CHARACTERS_PER_REQUEST = 12;
// 首次先批量生成；其中词语越界的字再逐个请求九次。这样一个字最多有十次机会，
// 同时不会让某个字的失败拖累同批已经合规的结果。
const DEEPSEEK_PER_CHARACTER_RETRY_COUNT = 9;
// 识字生成是受严格字库和格式约束的短 JSON 任务。实测在 1600 token 的响应上限内，
// 思考模式会将全部预算消耗在 reasoning_content，导致没有最终 content；因此这里
// 保持同一个 V4 Flash 模型，但显式关闭思考模式，把预算全部留给可校验的最终 JSON。
const DEEPSEEK_MAX_TOKENS = 1_600;
// Web 函数的总超时为 90 秒。单次上游调用必须主动截止，避免某次重试一直等待到
// 平台强制终止；平台终止会让客户端只能收到无业务语义的 433。
const DEEPSEEK_REQUEST_TIMEOUT_MILLIS = 20_000;
// 多个字都需要逐字修复时，仍要给鉴权和返回响应预留时间，不能再次触发 SCF 的
// 90 秒硬超时。正常的快速校验失败仍可完成一个字的十次生成尝试。
const DEEPSEEK_GENERATION_BUDGET_MILLIS = 65_000;
// Supabase 调用同样必须主动超时。否则鉴权或字库读取连接卡住时，函数仍会被
// SCF 在 90 秒后强制终止，并在客户端表现为没有业务语义的 433。
const SUPABASE_REQUEST_TIMEOUT_MILLIS = 15_000;
const AUDIO_BUCKET = 'literacy-audio';
const AUDIO_DELETE_MAX_ATTEMPTS = 3;
// 音频函数保持事件函数形态，名称可按部署环境覆盖；默认与现有函数名一致。
const LITERACY_AUDIO_GENERATOR_FUNCTION = process.env.LITERACY_AUDIO_GENERATOR_FUNCTION || 'generate-literacy-audio';

function requiredEnv(name) {
  const value = process.env[name];
  if (!value) throw new Error(`缺少 SCF 环境变量 ${name}`);
  return value;
}

function response(statusCode, body) {
  return {
    statusCode,
    headers: { 'content-type': 'application/json; charset=utf-8' },
    body: JSON.stringify(body)
  };
}

function requestBody(event) {
  if (!event.body) return {};
  if (typeof event.body === 'object') return event.body;
  try { return JSON.parse(event.body); } catch (_) { throw new HttpError(400, '请求 JSON 格式不正确'); }
}

function bearer(event) {
  const headers = event.headers || {};
  const authorization = headers.authorization || headers.Authorization;
  if (!authorization || !authorization.startsWith('Bearer ')) {
    throw new HttpError(401, '缺少登录凭证');
  }
  return authorization.slice('Bearer '.length).trim();
}

/**
 * 历史音素回填不应依赖某个孩子的短期登录态。该入口只接受部署时单独配置的密钥，
 * 便于安全地由运维脚本或定时 HTTP 调用持续处理队列；未配置密钥时入口默认关闭。
 */
function requirePhoneticBackfillKey(event) {
  const expected = process.env.LITERACY_PHONETIC_BACKFILL_KEY;
  if (!expected) throw new HttpError(503, '未配置音素回填密钥，后台回填入口未启用');
  const headers = event.headers || {};
  const received = headers['x-phonetic-backfill-key'] || headers['X-Phonetic-Backfill-Key'];
  if (typeof received !== 'string') throw new HttpError(401, '缺少音素回填密钥');
  const expectedBuffer = Buffer.from(expected, 'utf8');
  const receivedBuffer = Buffer.from(received, 'utf8');
  if (expectedBuffer.length !== receivedBuffer.length || !crypto.timingSafeEqual(expectedBuffer, receivedBuffer)) {
    throw new HttpError(401, '音素回填密钥不正确');
  }
}

class HttpError extends Error {
  constructor(statusCode, message) { super(message); this.statusCode = statusCode; }
}

async function supabase(path, options = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), SUPABASE_REQUEST_TIMEOUT_MILLIS);
  try {
    const result = await fetch(`${SUPABASE_URL}/rest/v1/${path}`, {
      ...options,
      headers: {
        apikey: SUPABASE_SERVICE_ROLE_KEY,
        Authorization: `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
        ...(options.headers || {})
      },
      signal: controller.signal
    });
    if (!result.ok) throw new Error(`Supabase ${result.status}: ${await result.text()}`);
    if (result.status === 204) return null;
    const text = await result.text();
    return text ? JSON.parse(text) : null;
  } catch (error) {
    if (controller.signal.aborted) {
      console.error(`Supabase 请求超时（${SUPABASE_REQUEST_TIMEOUT_MILLIS}ms）`, { path });
      throw new HttpError(504, '数据服务响应超时，请稍后重试');
    }
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

async function authenticatedChild(event) {
  const token = bearer(event);
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), SUPABASE_REQUEST_TIMEOUT_MILLIS);
  try {
    const result = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
      headers: { apikey: SUPABASE_SERVICE_ROLE_KEY, Authorization: `Bearer ${token}` },
      signal: controller.signal
    });
    if (!result.ok) throw new HttpError(401, '登录已过期，请重新进入应用');
    const user = await result.json();
    if (!user.id) throw new HttpError(401, '无法识别当前用户');
    return user.id;
  } catch (error) {
    if (controller.signal.aborted) {
      console.error(`Supabase 鉴权请求超时（${SUPABASE_REQUEST_TIMEOUT_MILLIS}ms）`);
      throw new HttpError(504, '登录服务响应超时，请稍后重试');
    }
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

function chineseCharacters(text) {
  return [...text].filter((character) => /[\u4E00-\u9FFF]/.test(character));
}

function normalizeRequestedCharacters(value) {
  if (typeof value !== 'string') throw new HttpError(400, 'characters 必须是汉字文本');
  const text = value.trim();
  if (!text) throw new HttpError(400, '请至少输入一个汉字');
  if ([...text].some((character) => !/[\u4E00-\u9FFF]/.test(character))) {
    throw new HttpError(400, '只能输入汉字，不能包含空格、标点或其他字符');
  }
  const characters = [...new Set(text)];
  if (characters.length > MAX_GENERATED_CHARACTERS_PER_REQUEST) {
    throw new HttpError(400, `一次最多生成 ${MAX_GENERATED_CHARACTERS_PER_REQUEST} 个汉字`);
  }
  return characters;
}

async function loadKnownCharacterSet(childId) {
  const knownCharacters = new Set();
  const pageSize = 1000;
  for (let offset = 0; ; offset += pageSize) {
    const rows = await supabase(
      `known_characters?user_id=eq.${encodeURIComponent(childId)}&select=character&order=character.asc&offset=${offset}&limit=${pageSize}`
    );
    for (const row of rows || []) {
      if (typeof row.character === 'string' && /^[\u4E00-\u9FFF]$/.test(row.character)) {
        knownCharacters.add(row.character);
      }
    }
    if (!Array.isArray(rows) || rows.length < pageSize) break;
  }
  return knownCharacters;
}

async function loadExistingLiteracyCharacters(childId) {
  const rows = await supabase(
    `child_literacy_characters?child_id=eq.${encodeURIComponent(childId)}&select=character`
  );
  return new Set((rows || []).map((row) => row.character).filter((character) => typeof character === 'string'));
}

async function loadChildFamilyId(childId) {
  const rows = await supabase(
    `users?uid=eq.${encodeURIComponent(childId)}&select=family_id&limit=1`
  );
  const familyId = rows?.[0]?.family_id;
  if (typeof familyId !== 'string' || !familyId) throw new HttpError(422, '当前孩子未加入家庭，不能创建识字任务');
  return familyId;
}

function stripMarkdownCodeFence(text) {
  return text.trim()
    .replace(/^```(?:json)?\s*/i, '')
    .replace(/\s*```$/, '')
    .trim();
}

function parseDeepSeekJson(content) {
  if (typeof content !== 'string' || !content.trim()) throw new Error('DeepSeek 未返回生成内容');
  try {
    return JSON.parse(stripMarkdownCodeFence(content));
  } catch (_) {
    throw new Error('DeepSeek 返回的不是 JSON');
  }
}

function deepSeekContentOrError(responseBody) {
  const choice = responseBody?.choices?.[0];
  const content = choice?.message?.content;
  if (typeof content === 'string' && content.trim()) return content;

  // 思考模式下 reasoning_content 与最终 content 是两个同级字段。不能把思维链当作
  // 业务 JSON 使用；仅记录长度和结束原因，避免把模型的思维链写入云函数日志。
  const finishReason = typeof choice?.finish_reason === 'string' ? choice.finish_reason : 'unknown';
  const reasoningLength = typeof choice?.message?.reasoning_content === 'string'
    ? choice.message.reasoning_content.length
    : 0;
  const completionTokens = Number.isFinite(responseBody?.usage?.completion_tokens)
    ? responseBody.usage.completion_tokens
    : 'unknown';
  throw new Error(
    `DeepSeek 未返回最终生成内容（finish_reason=${finishReason}，思考文本长度=${reasoningLength}，completion_tokens=${completionTokens}）`
  );
}

function retryConstraintPrompt(previousError, previousContent) {
  if (!previousError) return '';
  const forbiddenCharacters = [...new Set(
    [...previousError.matchAll(/字库外汉字“([\u4E00-\u9FFF])”/g)].map((match) => match[1])
  )];
  const rejectedCharacters = [...new Set(
    [...previousError.matchAll(/“([\u4E00-\u9FFF])”的(?:词语|句子)/g)].map((match) => match[1])
  )];
  const hasForbiddenCharacters = forbiddenCharacters.length > 0;
  return [
    '【服务端硬性修复指令】上一次 JSON 已被逐字校验拒绝，不能原样重复。',
    hasForbiddenCharacters
      ? `本轮输出中严禁出现这些字：${forbiddenCharacters.join('、')}。${rejectedCharacters.length ? `请重写目标字“${rejectedCharacters.join('、')}”的违规内容。` : ''}不要复用上一次的违规词句。`
      : `拒绝原因：${previousError}`,
    '重新生成时仍须提供有学习意义的词句。不要把单独的目标字同时当作词语和句子；绝不能为了通顺加入字库外汉字。',
    '必须输出一个新的完整 JSON 对象；输出前逐字检查：words 不应含字库外汉字，sentence 最多可含 2 个字库外汉字。',
    // 对于字库外字，不把原句再次贴进上下文。模型会强烈倾向复制它，反而更难修复。
    hasForbiddenCharacters ? '' : `上次被拒绝的 JSON：\n${previousContent || '（无可用 JSON，请完整重写）'}`
  ].filter(Boolean).join('\n');
}

function hasOnlyPermittedCharacters(text) {
  return [...text].every((character) =>
    /[\u4E00-\u9FFF]/.test(character) || /^[，。！？、]$/.test(character)
  );
}

function outOfLibraryChineseCharacters(text, allowedCharacters) {
  return [...text].filter((character) =>
    /[\u4E00-\u9FFF]/.test(character) && !allowedCharacters.has(character)
  );
}

function textForExample(rawExample, label) {
  if (!rawExample || typeof rawExample !== 'object' || Array.isArray(rawExample)) {
    throw new Error(`${label}必须包含 text`);
  }
  const text = typeof rawExample.text === 'string' ? rawExample.text.trim() : '';
  if (!text) throw new Error(`${label}不能为空`);
  return { text };
}

function validateGeneratedTasks(payload, requestedCharacters, allowedCharacters, options = {}) {
  const { allowOutOfLibraryWords = false } = options;
  if (!payload || !Array.isArray(payload.items)) throw new Error('返回中缺少 items 数组');
  if (payload.items.length !== requestedCharacters.length) {
    throw new Error(`返回的任务数量不正确：需要 ${requestedCharacters.length} 条，实际 ${payload.items.length} 条`);
  }

  const itemsByCharacter = new Map();
  for (const item of payload.items) {
    const character = typeof item?.character === 'string' ? item.character : '';
    if (!requestedCharacters.includes(character) || itemsByCharacter.has(character)) {
      throw new Error('返回了重复或未请求的汉字');
    }
    // 词数、词长和句长是内容质量建议，不是生成失败条件；但每个词、句都必须
    // 包含对应目标字，避免把不相关的内容写入该字的学习任务。
    // 仅保留可显示的字符串；空词不写入任务。
    const words = Array.isArray(item.words) ? item.words : [];
    const sentence = textForExample(item.sentence, `“${character}”的句子`);
    const normalizedWords = words
      .map((word) => textForExample(word, `“${character}”的词语`));
    for (const word of normalizedWords) {
      if (!word.text.includes(character)) {
        throw new Error(`“${character}”的词语“${word.text}”必须包含该字`);
      }
      if (!hasOnlyPermittedCharacters(word.text)) {
        throw new Error(`“${character}”的词语“${word.text}”含有不支持的字符`);
      }
      const outOfLibraryCharacters = outOfLibraryChineseCharacters(word.text, allowedCharacters);
      if (!allowOutOfLibraryWords && outOfLibraryCharacters.length) {
        throw new Error(`“${character}”的词语“${word.text}”含有字库外汉字“${outOfLibraryCharacters.join('')}”`);
      }
    }
    if (!sentence.text.includes(character)) {
      throw new Error(`“${character}”的句子必须包含该字`);
    }
    if (!hasOnlyPermittedCharacters(sentence.text)) {
      throw new Error(`“${character}”的句子“${sentence.text}”含有不支持的字符`);
    }
    const sentenceOutOfLibraryCharacters = outOfLibraryChineseCharacters(sentence.text, allowedCharacters);
    if (sentenceOutOfLibraryCharacters.length > 2) {
      throw new Error(`“${character}”的句子“${sentence.text}”含有 ${sentenceOutOfLibraryCharacters.length} 个字库外汉字，最多允许 2 个（${sentenceOutOfLibraryCharacters.join('')}）`);
    }
    itemsByCharacter.set(character, { character, words: normalizedWords, sentence });
  }
  return requestedCharacters.map((character) => itemsByCharacter.get(character));
}

// 只记录生成质量的统计信息，不记录儿童的完整词句内容，便于从 SCF 日志诊断模型
// 是否退化成“目标字本身”的最小输出。
function literacyTaskQualitySummary(tasks) {
  return (tasks || []).map((task) => ({
    character: task.character,
    wordCount: Array.isArray(task.words) ? task.words.length : 0,
    targetOnlyWordCount: (task.words || []).filter((word) => word.text === task.character).length,
    sentenceChineseLength: chineseCharacters(task.sentence?.text || '').length,
    targetOnlySentence: task.sentence?.text === task.character
  }));
}

/**
 * 从一次批量生成中收集合规项。遇到个别字生成越界时，保留其他合规项，下一轮只让
 * DeepSeek 重生成这些不合规或缺失的目标字，绝不对词句做删字修补。
 */
function collectValidGeneratedTasks(payload, requestedCharacters, allowedCharacters) {
  if (!payload || !Array.isArray(payload.items)) {
    throw new Error('返回中缺少 items 数组');
  }
  const validTasks = new Map();
  const errors = [];
  for (const item of payload.items) {
    const character = typeof item?.character === 'string' ? item.character : '';
    if (!requestedCharacters.includes(character) || validTasks.has(character)) continue;
    try {
      const [task] = validateGeneratedTasks({ items: [item] }, [character], allowedCharacters);
      validTasks.set(character, task);
    } catch (error) {
      errors.push(error.message || `“${character || '未知'}”生成内容不合规`);
    }
  }
  const missingCharacters = requestedCharacters.filter((character) => !validTasks.has(character));
  if (missingCharacters.length && !errors.length) {
    errors.push(`未返回目标字“${missingCharacters.join('、')}”的内容`);
  }
  return { validTasks, missingCharacters, error: errors[0] || '' };
}

/**
 * 词语越界时，句子仍可能已符合“至多两个字库外字”的规则。保留该轮的完整
 * DeepSeek 结果，供同一目标字累计十次重试仍无合规词语时原样采用；不删字、不拼接。
 */
function collectWordFallbackTasks(payload, requestedCharacters, allowedCharacters) {
  const fallbackTasks = new Map();
  if (!payload || !Array.isArray(payload.items)) return fallbackTasks;
  for (const item of payload.items) {
    const character = typeof item?.character === 'string' ? item.character : '';
    if (!requestedCharacters.includes(character) || fallbackTasks.has(character)) continue;
    try {
      const [task] = validateGeneratedTasks(
        { items: [item] },
        [character],
        allowedCharacters,
        { allowOutOfLibraryWords: true }
      );
      if (task.words.some((word) => outOfLibraryChineseCharacters(word.text, allowedCharacters).length)) {
        fallbackTasks.set(character, task);
      }
    } catch (_) {
      // 句子超过两个字库外汉字或含非法字符时，不能作为词语兜底结果。
    }
  }
  return fallbackTasks;
}

function literacyGenerationPrompt(requestedCharacters, allowedCharacters, previousError, previousContent) {
  return [
    '你是儿童识字教材编辑。只输出一个 JSON 对象，不要 Markdown、解释或额外字段。',
    '请为每个目标汉字生成一条识字任务。JSON 格式必须为：{"items":[{"character":"字","words":[{"text":"词语"}],"sentence":{"text":"句子"}}]}。',
    '每个目标字必须恰好出现一次。每个 words 提供 1 到 3 个词语即可，不要求凑满 3 个；优先提供至少两个汉字的、有学习意义的词语，不要只返回目标字本身。',
    '每个词语和句子都必须包含对应目标字。这是硬性要求。句子不限制长度，但应是可供儿童朗读的完整短句，不要只返回目标字本身。绝对不要返回拼音、注音、pinyins 或其他发音字段。',
    '词语中的汉字应全部取自“允许汉字”；句子最多可出现 2 个“允许汉字”之外的汉字。不得使用英文、数字、空格或任何其他符号。句中只可使用中文逗号、句号、问号、叹号、顿号，标点可省略。',
    '输出前请逐项自检：items 数量等于目标字数量；每个词语和句子均包含对应目标字；词语不含字库外汉字，句子最多含 2 个字库外汉字。不要输出自检过程。',
    `目标汉字：${requestedCharacters.join('')}`,
    `允许汉字：${[...allowedCharacters].sort().join('')}`,
    retryConstraintPrompt(previousError, previousContent)
  ].filter(Boolean).join('\n');
}

async function generateWithDeepSeek(requestedCharacters, allowedCharacters) {
  const apiKey = process.env.DEEPSEEK_API_KEY;
  if (!apiKey) throw new HttpError(503, '尚未配置 DeepSeek 服务，请联系管理员');
  const completedTasks = new Map();
  const generationDeadline = Date.now() + DEEPSEEK_GENERATION_BUDGET_MILLIS;
  async function requestTasks(characters, attempt, previousError = '', previousContent = '') {
    const remainingMillis = generationDeadline - Date.now();
    if (remainingMillis <= 0) {
      throw new Error('DeepSeek 逐字重试时间已用完');
    }
    const requestTimeoutMillis = Math.min(DEEPSEEK_REQUEST_TIMEOUT_MILLIS, remainingMillis);
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), requestTimeoutMillis);
    let responseBody;
    try {
      const result = await fetch(DEEPSEEK_API_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${apiKey}` },
        body: JSON.stringify({
          model: DEEPSEEK_MODEL,
          // 修复轮适度提高采样差异，防止模型连续原样输出已被拒绝的常用句。
          temperature: attempt === 1 ? 0.4 : 0.7,
          max_tokens: DEEPSEEK_MAX_TOKENS,
          thinking: { type: 'disabled' },
          response_format: { type: 'json_object' },
          messages: [{ role: 'user', content: literacyGenerationPrompt(characters, allowedCharacters, previousError, previousContent) }]
        }),
        signal: controller.signal
      });
      if (!result.ok) {
        const detail = await result.text();
        throw new HttpError(502, `DeepSeek 生成失败（${result.status}）：${detail.slice(0, 300)}`);
      }
      // 定时器要覆盖读取响应体，不能在仅收到响应头时提前清理。
      responseBody = await result.json();
    } catch (error) {
      if (controller.signal.aborted) {
        console.error(`DeepSeek 第 ${attempt} 次请求超时（${requestTimeoutMillis}ms）`);
        throw new Error(`DeepSeek 响应超时（${requestTimeoutMillis}ms）`);
      }
      if (error instanceof HttpError) throw error;
      throw new HttpError(502, `DeepSeek 服务请求失败：${String(error.message || error).slice(0, 200)}`);
    } finally {
      clearTimeout(timeout);
    }
    const content = deepSeekContentOrError(responseBody);
    const payload = parseDeepSeekJson(content);
    return {
      content,
      collected: collectValidGeneratedTasks(payload, characters, allowedCharacters),
      wordFallbackTasks: collectWordFallbackTasks(payload, characters, allowedCharacters)
    };
  }

  // 首次仍批量生成，先尽量减少 API 调用；合规项立即保留。
  let firstFailure = '';
  let firstContent = '';
  const wordFallbackTasks = new Map();
  try {
    const { content, collected, wordFallbackTasks: initialFallbackTasks } = await requestTasks(requestedCharacters, 1);
    for (const [character, task] of collected.validTasks) completedTasks.set(character, task);
    for (const [character, task] of initialFallbackTasks) wordFallbackTasks.set(character, task);
    firstFailure = collected.error;
    firstContent = content.slice(0, 12_000);
    if (!collected.missingCharacters.length) {
      return requestedCharacters.map((character) => completedTasks.get(character));
    }
    console.warn(`DeepSeek 第 1 次结果未通过校验：${firstFailure || `仍需生成目标字“${collected.missingCharacters.join('、')}”的合规内容`}`);
  } catch (error) {
    if (error instanceof HttpError) throw error;
    firstFailure = error.message || '生成内容不符合约束';
    console.warn(`DeepSeek 第 1 次结果未通过校验：${firstFailure}`);
  }

  // 不合规字逐个重试，避免一个字反复失败时让已合规字或其他待生成字陪同重试。
  retryCharacters: for (const character of requestedCharacters.filter((item) => !completedTasks.has(item))) {
    let previousError = firstFailure || `未返回目标字“${character}”的内容`;
    let previousContent = firstContent;
    let generated = false;
    for (let retry = 1; retry <= DEEPSEEK_PER_CHARACTER_RETRY_COUNT; retry += 1) {
      if (Date.now() >= generationDeadline) {
        console.warn('DeepSeek 逐字重试已达到 65 秒总时间预算，停止继续请求以避免 SCF 超时');
        break retryCharacters;
      }
      const attempt = retry + 1;
      try {
        const { content, collected, wordFallbackTasks: retryFallbackTasks } = await requestTasks([character], attempt, previousError, previousContent);
        const wordFallbackTask = retryFallbackTasks.get(character);
        if (wordFallbackTask) wordFallbackTasks.set(character, wordFallbackTask);
        const task = collected.validTasks.get(character);
        if (task) {
          completedTasks.set(character, task);
          generated = true;
          break;
        }
        previousError = collected.error || `未返回目标字“${character}”的内容`;
        previousContent = content.slice(0, 12_000);
      } catch (error) {
        if (error instanceof HttpError) throw error;
        previousError = error.message || '生成内容不符合约束';
        previousContent = '';
      }
      console.warn(`DeepSeek “${character}”第 ${attempt} 次结果未通过校验：${previousError}`);
    }
    if (!generated) {
      const wordFallbackTask = wordFallbackTasks.get(character);
      if (wordFallbackTask) {
        completedTasks.set(character, wordFallbackTask);
        const outOfLibraryWords = wordFallbackTask.words
          .filter((word) => outOfLibraryChineseCharacters(word.text, allowedCharacters).length)
          .map((word) => word.text)
          .join('、');
        console.warn(`DeepSeek “${character}”已完成 ${DEEPSEEK_PER_CHARACTER_RETRY_COUNT + 1} 次生成尝试；词语“${outOfLibraryWords}”仍含字库外汉字，按规则采用最后一版结果`);
      } else {
        console.warn(`DeepSeek “${character}”已完成 ${DEEPSEEK_PER_CHARACTER_RETRY_COUNT + 1} 次生成尝试，仍未得到句子至多含两个字库外汉字的可用内容`);
      }
    }
  }
  if (completedTasks.size === requestedCharacters.length) {
    return requestedCharacters.map((character) => completedTasks.get(character));
  }
  throw new HttpError(422, '未能生成符合字库范围的词句，请稍后重试或先扩充字库');
}

async function previewGeneratedLiteracyTasks(childId, rawCharacters) {
  const requestedCharacters = normalizeRequestedCharacters(rawCharacters);
  const [knownCharacters, existingCharacters] = await Promise.all([
    loadKnownCharacterSet(childId),
    loadExistingLiteracyCharacters(childId)
  ]);
  // 字库已有字仍生成可编辑的字词句，只在预览中标记；已有待认识任务的同字才跳过，避免重复创建。
  const knownCharactersInRequest = requestedCharacters.filter((character) => knownCharacters.has(character));
  const skippedExistingCharacters = requestedCharacters.filter((character) => existingCharacters.has(character));
  const charactersToCreate = requestedCharacters.filter((character) => !existingCharacters.has(character));
  if (!charactersToCreate.length) {
    return { tasks: [], knownCharacters: knownCharactersInRequest, skippedExistingCharacters };
  }
  const allowedCharacters = new Set([...knownCharacters, ...requestedCharacters]);
  const tasks = await generateWithDeepSeek(charactersToCreate, allowedCharacters);
  return { tasks, knownCharacters: knownCharactersInRequest, skippedExistingCharacters };
}

async function saveGeneratedLiteracyTasks(childId, rawCharacters, rawItems) {
  const requestedCharacters = normalizeRequestedCharacters(rawCharacters);
  if (!Array.isArray(rawItems)) throw new HttpError(400, 'items 必须是识字任务数组');
  if (rawItems.some((item) => !requestedCharacters.includes(item?.character))) {
    throw new HttpError(400, '提交内容包含未输入的汉字');
  }
  const [knownCharactersAtStart, existingCharacters] = await Promise.all([
    loadKnownCharacterSet(childId),
    loadExistingLiteracyCharacters(childId)
  ]);
  // 字库已有字允许继续创建待认识任务；保存时只跳过已有待认识任务的同字。
  const knownCharactersInRequest = requestedCharacters.filter((character) => knownCharactersAtStart.has(character));
  const skippedExistingCharacters = requestedCharacters.filter((character) => existingCharacters.has(character));
  const charactersToCreate = requestedCharacters.filter((character) => !existingCharacters.has(character));

  const [familyId, knownCharacters, sortRows] = await Promise.all([
    loadChildFamilyId(childId),
    loadKnownCharacterSet(childId),
    supabase(`child_literacy_characters?child_id=eq.${encodeURIComponent(childId)}&select=sort_order&order=sort_order.desc&limit=1`)
  ]);
  const allowedCharacters = new Set([...knownCharacters, ...requestedCharacters]);
  // 家长可删除整组字词句；只校验并保存仍在提交列表中的项目。
  const itemsToSave = rawItems.filter((item) => charactersToCreate.includes(item.character));
  const charactersToSave = itemsToSave.map((item) => item.character);
  // 已在预览中经历十次生成后保留的词语可含字库外汉字；句子仍严格限制最多两个。
  const generatedTasks = validateGeneratedTasks(
    { items: itemsToSave },
    charactersToSave,
    allowedCharacters,
    { allowOutOfLibraryWords: true }
  );
  if (!generatedTasks.length) {
    return { created: [], knownCharacters: knownCharactersInRequest, skippedExistingCharacters };
  }
  const nextSortOrder = Number(sortRows?.[0]?.sort_order || 0) + 1;
  const rows = generatedTasks.map((task, index) => ({
    family_id: familyId,
    child_id: childId,
    character: task.character,
    words: task.words,
    sentences: [task.sentence],
    sort_order: nextSortOrder + index
  }));
  // 任务正文与 pending 音素资产必须由同一个 RPC 落库，避免新任务在异步生成器
  // 第一次扫描时丢失固定索引，也避免请求中断留下无法评测的半成品任务。
  const created = await supabase('rpc/create_literacy_tasks_with_phonetic_assets', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      p_child_id: childId,
      p_family_id: familyId,
      p_rows: rows
    })
  });
  return {
    created: (created || []).map((row) => ({ id: row.id, character: row.character })),
    knownCharacters: knownCharactersInRequest,
    skippedExistingCharacters
  };
}

function normalizePhonemeTokens(value, itemText) {
  if (!Array.isArray(value)) throw new HttpError(400, 'phonemeTokens 必须是数组');
  const characters = chineseCharacters(itemText);
  if (value.length !== characters.length) {
    throw new HttpError(400, `音素数量必须与汉字数量一致（应为 ${characters.length} 个）`);
  }
  return value.map((token, index) => {
    if (token === null) return null;
    if (typeof token !== 'string' || !/^[a-zv]+[1-4]$/.test(token.trim().toLowerCase())) {
      throw new HttpError(400, `第 ${index + 1} 个汉字“${characters[index]}”的数字拼音格式不正确`);
    }
    return token.trim().toLowerCase();
  });
}

function wordListForPhonemeTokens(itemText, tokens) {
  const normalized = normalizePhonemeTokens(tokens, itemText);
  return chineseCharacters(itemText).map((word, index) =>
    normalized[index] === null ? { word } : { word, pron: [[normalized[index]]] }
  );
}

function phonemesForText(text) {
  const sourceCharacters = [...text];
  const generated = pinyin(text, { toneType: 'num', type: 'array', v: true });
  if (!Array.isArray(generated) || generated.length !== sourceCharacters.length) {
    throw new Error('词组拼音词典返回长度与原文不一致');
  }
  const tokens = [];
  sourceCharacters.forEach((character, index) => {
    if (!/[\u4E00-\u9FFF]/.test(character)) return;
    const token = typeof generated[index] === 'string' ? generated[index].toLowerCase() : '';
    // pinyin-pro 以 0 表示轻声；腾讯不支持该标注，因此保留位置但让组装器省略 pron。
    if (/^[a-zv]+0$/.test(token)) {
      tokens.push(null);
    } else if (/^[a-zv]+[1-4]$/.test(token)) {
      tokens.push(token);
    } else {
      throw new Error(`词典无法为“${character}”生成腾讯支持的数字拼音`);
    }
  });
  if (tokens.length !== chineseCharacters(text).length) throw new Error('音素数量与汉字数量不一致');
  return tokens;
}

async function generatePhoneticAssets(limit = 50) {
  const assets = await supabase('rpc/claim_literacy_phonetic_assets', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ p_limit: Math.max(1, Math.min(Number(limit) || 50, 50)) })
  });
  const result = { ready: 0, failed: 0 };
  for (const asset of assets || []) {
    try {
      const tokens = phonemesForText(asset.item_text);
      await supabase('rpc/complete_literacy_phonetic_asset', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ p_asset_id: asset.id, p_phoneme_tokens: tokens, p_generator_version: 'pinyin-pro-3.27.0' })
      });
      result.ready++;
    } catch (error) {
      await supabase('rpc/fail_literacy_phonetic_asset', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ p_asset_id: asset.id, p_reason: String(error.message || error).slice(0, 1000) })
      });
      result.failed++;
    }
  }
  return result;
}

function audioGeneratorClient() {
  // 复用评测服务端既有的腾讯云服务端凭证；该 CAM 身份需仅对目标音频函数拥有 InvokeFunction 权限。
  return new ScfClient({
    credential: { secretId: STS_SECRET_ID, secretKey: STS_SECRET_KEY },
    region: TENCENT_REGION,
    profile: { httpProfile: { endpoint: 'scf.tencentcloudapi.com' } }
  });
}

/**
 * 任务已入库后，以异步事件逐条定向唤醒音频函数。仅等待腾讯云受理事件，绝不等待
 * TTS 合成完成；投递失败也不回滚识字任务，原有定时扫描仍会兜底生成。
 */
async function triggerNewLiteracyTaskAudio(createdTasks, client = audioGeneratorClient()) {
  const recordIds = [...new Set((createdTasks || []).map((task) => task?.id).filter((id) => typeof id === 'string' && id))];
  if (!recordIds.length) return { accepted: 0, failed: 0 };
  const requests = recordIds.map((recordId) => Promise.resolve().then(() => client.Invoke({
    FunctionName: LITERACY_AUDIO_GENERATOR_FUNCTION,
    InvocationType: 'Event',
    // ClientContext 会原样作为事件函数 event 入参，生成器据此只处理本次新建的任务。
    ClientContext: JSON.stringify({
      source: 'task',
      record_id: recordId,
      limit: 50,
      concurrency: 3,
      only_missing_or_invalid: true
    })
  })));
  const results = await Promise.allSettled(requests);
  const failures = results.filter((result) => result.status === 'rejected');
  failures.forEach((result) => {
    console.error('识字任务音频异步投递失败', {
      function: LITERACY_AUDIO_GENERATOR_FUNCTION,
      error: String(result.reason?.message || result.reason || '未知错误').slice(0, 500)
    });
  });
  return { accepted: results.length - failures.length, failed: failures.length };
}

function examplesFromJson(value) {
  if (!Array.isArray(value)) return [];
  return value
    .map((item, sortOrder) => ({
      text: typeof item === 'string' ? item.trim() : String(item?.text || '').trim(),
      sortOrder
    }))
    .filter((item) => item.text);
}

async function loadTarget(childId, literacyCharacterId, targetType, sentenceText, wordText, contentSource = 'task') {
  if (!['character', 'word', 'sentence'].includes(targetType)) {
    throw new HttpError(400, 'targetType 必须是 character、word 或 sentence');
  }
  if (!['task', 'recognized'].includes(contentSource)) {
    throw new HttpError(400, 'contentSource 必须是 task 或 recognized');
  }
  const table = contentSource === 'recognized'
    ? 'recognized_characters'
    : 'child_literacy_characters';
  const characters = await supabase(
    // 每类内容仅从自己的主表读取，客户端不能自行提供朗读文本。
    `${table}?id=eq.${encodeURIComponent(literacyCharacterId)}&child_id=eq.${encodeURIComponent(childId)}&select=id,family_id,child_id,character,words,sentences`
  );
  const character = characters[0];
  if (!character) throw new HttpError(404, contentSource === 'recognized' ? '未找到该已认识汉字' : '未找到该识字任务');

  if (targetType === 'character') {
    return {
      character,
      contentSource,
      targetText: character.character,
      targetType,
      examples: []
    };
  }

  let examples = targetType === 'word'
    ? examplesFromJson(character.words)
    : examplesFromJson(character.sentences);
  if (targetType === 'sentence') {
    if (!sentenceText) throw new HttpError(400, '句子评测必须传 sentenceText');
    // 客户端只用于选择已有句子；实际内容仍由数据库查询结果决定，不能任意上送。
    examples = examples.filter((item) => item.text === sentenceText);
  }
  if (targetType === 'word' && wordText !== undefined) {
    if (typeof wordText !== 'string' || !wordText.trim()) {
      throw new HttpError(400, 'wordText 必须是一个词');
    }
    // 不接受客户端自定义文本，只允许从当前识字任务已有的词中选择一项。
    examples = examples.filter((item) => item.text === wordText);
  }
  if (!examples.length) throw new HttpError(422, targetType === 'word' ? '暂无可评测的词' : '未找到该句子');

  return {
    character,
    contentSource,
    targetText: examples.map((item) => item.text).join('，'),
    targetType,
    examples
  };
}

async function prepareEvaluation(childId, body) {
  const target = await loadTarget(
    childId,
    body.literacyCharacterId,
    body.targetType,
    body.sentenceText,
    body.wordText,
    body.contentSource
  );
  const repeatCount = Number.isInteger(body.repeatCount) ? body.repeatCount : 1;
  if (repeatCount < 1 || repeatCount > 3) throw new HttpError(400, 'repeatCount 必须在 1 到 3 之间');
  if (target.targetType === 'character') {
    return { refText: target.targetText.repeat(repeatCount), textMode: 0, targetText: target.targetText };
  }
  // loadTarget 已将 wordText / sentenceText 收窄为单条数据库内容，因此固定索引可
  // 与音素资产精确对应。任何未就绪资产均不可静默回退到 TEXT_MODE=0。
  const item = target.examples[0];
  const assets = await supabase(
    `literacy_phonetic_assets?content_source=eq.${encodeURIComponent(target.contentSource === 'task' ? 'pending' : 'recognized')}` +
    `&literacy_character_id=eq.${encodeURIComponent(target.character.id)}` +
    `&item_type=eq.${encodeURIComponent(target.targetType)}` +
    `&item_index=eq.${item.sortOrder}&status=eq.ready&select=id,item_text,phoneme_tokens&limit=1`
  );
  const asset = assets?.[0];
  if (!asset || asset.item_text !== item.text || !Array.isArray(asset.phoneme_tokens)) {
    throw new HttpError(409, '正在准备发音，请稍后再试');
  }
  const wordList = wordListForPhonemeTokens(item.text, asset.phoneme_tokens);
  return { refText: JSON.stringify({ wordList }), textMode: 1, targetText: item.text };
}

async function loadPhoneticAssets(childId, literacyCharacterId, contentSource = 'task') {
  if (!['task', 'recognized'].includes(contentSource)) throw new HttpError(400, 'contentSource 必须是 task 或 recognized');
  const target = await loadTarget(childId, literacyCharacterId, 'character', undefined, undefined, contentSource);
  const source = contentSource === 'task' ? 'pending' : 'recognized';
  const assets = await supabase(
    `literacy_phonetic_assets?content_source=eq.${source}&literacy_character_id=eq.${encodeURIComponent(target.character.id)}` +
    '&select=id,item_type,item_index,item_text,phoneme_tokens,status,last_error&order=item_type.asc,item_index.asc'
  );
  return { character: target.character.character, assets: assets || [] };
}

async function savePhoneticAsset(childId, body) {
  if (typeof body.assetId !== 'string' || !body.assetId.trim()) throw new HttpError(400, 'assetId 必填');
  const assets = await supabase(
    `literacy_phonetic_assets?id=eq.${encodeURIComponent(body.assetId)}&select=id,content_source,literacy_character_id,item_text&limit=1`
  );
  const asset = assets?.[0];
  if (!asset) throw new HttpError(404, '未找到音素资产');
  await loadTarget(childId, asset.literacy_character_id, 'character', undefined, undefined,
    asset.content_source === 'pending' ? 'task' : 'recognized');
  const tokens = normalizePhonemeTokens(body.phonemeTokens, asset.item_text);
  await supabase(`literacy_phonetic_assets?id=eq.${encodeURIComponent(asset.id)}`, {
    method: 'PATCH', headers: { 'Content-Type': 'application/json', Prefer: 'return=minimal' },
    body: JSON.stringify({ phoneme_tokens: tokens, status: 'ready', last_error: null, updated_at: new Date().toISOString() })
  });
  return { saved: true };
}

function resolveHelpContext(target, characterIndex) {
  if (target.targetType === 'character') return target.targetText;
  let start = 0;
  for (const example of target.examples) {
    const end = start + example.text.length;
    if (characterIndex >= start && characterIndex < end) return example.text;
    start = end + 1; // targetText 用一个中文逗号拼接多个词。
  }
  return target.targetText;
}

/**
 * 求助记录的判定与待认识/已认识入口及当前内容类型都无关：
 * 只有被长按的单字已存在于孩子的字库（known_characters）时才记录。
 *
 * 这样词句中临时出现的字库外汉字，以及尚未存入字库的已认识主字，都会只朗读而
 * 不写入帮助表。
 */
async function shouldRecordHelpRequest(childId, character) {
  const rows = await supabase(
    `known_characters?user_id=eq.${encodeURIComponent(childId)}&character=eq.${encodeURIComponent(character)}&select=character&limit=1`
  );
  return Array.isArray(rows) && rows.length > 0;
}

async function issueStsCredentials() {
  const client = new StsClient({
    credential: { secretId: STS_SECRET_ID, secretKey: STS_SECRET_KEY },
    region: TENCENT_REGION,
    profile: { httpProfile: { endpoint: 'sts.tencentcloudapi.com' } }
  });
  const policy = {
    version: '2.0',
    statement: [{ effect: 'allow', action: ['name/soe:*'], resource: ['*'] }]
  };
  const data = await client.GetFederationToken({
    Name: `literacy-${Date.now()}`,
    Policy: JSON.stringify(policy),
    DurationSeconds: 1800
  });
  return data;
}

async function completeLiteracyCharacter(childId, literacyCharacterId, hasCharacterAudioPointRead) {
  if (typeof literacyCharacterId !== 'string' || !literacyCharacterId.trim()) {
    throw new HttpError(400, 'literacyCharacterId 必填');
  }
  if (hasCharacterAudioPointRead !== undefined && typeof hasCharacterAudioPointRead !== 'boolean') {
    throw new HttpError(400, 'hasCharacterAudioPointRead 必须是布尔值');
  }
  // 旧版客户端没有该字段，为避免它们在函数先发布时改变既有行为，仍按“点读过”处理。
  const shouldRecognize = hasCharacterAudioPointRead !== false;
  // 主表更新及 pending 资产的迁移/清理都由一个数据库 RPC 提交，网络失败时不会
  // 出现“已认识记录已创建但音素仍留在待认识任务”的中间状态。
  const completed = await supabase('rpc/complete_literacy_character_with_phonetic_assets', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      p_child_id: childId,
      p_literacy_character_id: literacyCharacterId,
      p_has_character_audio_point_read: shouldRecognize
    })
  });
  return completed || { recognized: shouldRecognize };
}

/**
 * “存库”是一次服务端受控的转移。只有教学音频全部删除后才提交；若删除失败，
 * 立即撤销本次新增的字库记录并恢复复习数据，等待孩子再次主动点击“存库”。
 */
async function archiveRecognizedCharacter(childId, recognizedCharacterId) {
  if (typeof recognizedCharacterId !== 'string' || !recognizedCharacterId.trim()) {
    throw new HttpError(400, 'recognizedCharacterId 必填');
  }
  const target = await loadRecognizedArchiveTarget(childId, recognizedCharacterId);
  const result = await supabase('rpc/archive_recognized_character_with_phonetic_assets', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      p_child_id: childId,
      p_recognized_character_id: recognizedCharacterId
    })
  });
  // 存库提示只能在教学音频也同步删除完成后返回。失败不进入后台清理队列，
  // 而是撤销这一次转移；孩子下次点击“存库”才会重新开始。
  if (target) {
    try {
      await deleteArchivedTeachingAudio(target);
    } catch (cleanupError) {
      try {
        await rollbackRecognizedArchive(target);
      } catch (rollbackError) {
        console.error('存库音频清理失败后回滚失败', {
          recognizedCharacterId: target.id,
          cleanupError: audioCleanupError(cleanupError),
          rollbackError: audioCleanupError(rollbackError)
        });
        throw new HttpError(500, '教学音频清理失败，且存库回滚失败，请联系管理员');
      }
      throw cleanupError;
    }
  }
  return result || { archived: true };
}

/**
 * 将复习字重新排到首页的“新字”队列：只允许孩子本人更新自己的收录时间。
 * 复习内容、音频和学习记录都不变，首页会据此从当天开始重新计算三天学习期。
 */
async function topRecognizedCharacter(childId, recognizedCharacterId) {
  if (typeof recognizedCharacterId !== 'string' || !recognizedCharacterId.trim()) {
    throw new HttpError(400, 'recognizedCharacterId 必填');
  }
  const recognizedAt = new Date().toISOString();
  const updated = await supabase(
    `recognized_characters?id=eq.${encodeURIComponent(recognizedCharacterId)}&child_id=eq.${encodeURIComponent(childId)}`,
    {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', Prefer: 'return=representation' },
      body: JSON.stringify({ recognized_at: recognizedAt })
    }
  );
  if (!Array.isArray(updated) || updated.length !== 1) {
    throw new HttpError(404, '未找到该已认识汉字');
  }
  return { recognizedAt: updated[0].recognized_at || recognizedAt };
}

async function loadRecognizedArchiveTarget(childId, recognizedCharacterId) {
  const recognizedFields = [
    'id', 'child_id', 'character', 'source_literacy_character_id',
    'character_audio_url', 'character_audio_version', 'character_audio_hash', 'words', 'sentences'
  ].join(',');
  const rows = await supabase(
    `recognized_characters?select=${recognizedFields}&id=eq.${encodeURIComponent(recognizedCharacterId)}&child_id=eq.${encodeURIComponent(childId)}&limit=1`
  );
  const recognized = Array.isArray(rows) ? rows[0] || null : null;
  if (!recognized) return null;

  const [knownRows, sourceTaskRows, assets] = await Promise.all([
    supabase(
      `known_characters?select=character&user_id=eq.${encodeURIComponent(childId)}&character=eq.${encodeURIComponent(recognized.character)}&limit=1`
    ),
    recognized.source_literacy_character_id
      ? supabase(
        `child_literacy_characters?select=id,character_audio_url,character_audio_version,character_audio_hash,words,sentences&id=eq.${encodeURIComponent(recognized.source_literacy_character_id)}&child_id=eq.${encodeURIComponent(childId)}&limit=1`
      )
      : Promise.resolve([]),
    listArchivedTeachingAudio(recognized)
  ]);
  return {
    ...recognized,
    knownCharacterExisted: Array.isArray(knownRows) && knownRows.length > 0,
    sourceTask: Array.isArray(sourceTaskRows) ? sourceTaskRows[0] || null : null,
    assets
  };
}

function archivedAudioMetadata(record) {
  return {
    character_audio_url: record.character_audio_url,
    character_audio_version: record.character_audio_version,
    character_audio_hash: record.character_audio_hash,
    words: record.words,
    sentences: record.sentences
  };
}

async function rollbackRecognizedArchive(target, supabaseRequest = supabase) {
  const restoreOptions = {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', Prefer: 'return=minimal' }
  };
  const now = new Date().toISOString();
  const restoreCalls = [
    supabaseRequest(
      `recognized_characters?id=eq.${encodeURIComponent(target.id)}&child_id=eq.${encodeURIComponent(target.child_id)}`,
      { ...restoreOptions, body: JSON.stringify(archivedAudioMetadata(target)) }
    )
  ];
  if (target.sourceTask) {
    restoreCalls.push(supabaseRequest(
      `child_literacy_characters?id=eq.${encodeURIComponent(target.sourceTask.id)}&child_id=eq.${encodeURIComponent(target.child_id)}`,
      { ...restoreOptions, body: JSON.stringify(archivedAudioMetadata(target.sourceTask)) }
    ));
  }
  for (const asset of (target.assets || []).filter((item) => item.status !== 'deleted')) {
    restoreCalls.push(supabaseRequest(
      `literacy_tts_assets?id=eq.${encodeURIComponent(asset.id)}&status=neq.deleted`,
      {
        ...restoreOptions,
        body: JSON.stringify({ status: asset.status, last_error: null, updated_at: now })
      }
    ));
  }
  await Promise.all(restoreCalls);

  // 同字可能在本次操作前已存在于字库；这种历史记录绝不能随回滚删除。
  if (!target.knownCharacterExisted) {
    await supabaseRequest(
      `known_characters?user_id=eq.${encodeURIComponent(target.child_id)}&character=eq.${encodeURIComponent(target.character)}`,
      { method: 'DELETE', headers: { Prefer: 'return=minimal' } }
    );
  }
}

function teachingAudioPath(asset) {
  if (asset.object_path) return asset.object_path;
  const filename = asset.item_type === 'character'
    ? 'character.mp3'
    : `${asset.item_type}-${asset.item_order}.mp3`;
  if (asset.root_literacy_character_id) {
    return `${asset.voice_version}/task/${asset.root_literacy_character_id}/${filename}`;
  }
  if (asset.recognized_character_id) {
    return `${asset.voice_version}/recognized/${asset.recognized_character_id}/${filename}`;
  }
  throw new Error(`教学音频资产 ${asset.id} 缺少归属记录`);
}

function encodeStoragePath(path) {
  return path.split('/').map(encodeURIComponent).join('/');
}

function audioCleanupError(error) {
  const message = String(error?.message || error || '未知错误')
    .replace(/Bearer\s+[^\s,;]+/ig, 'Bearer [已隐藏]')
    .replace(/(secret(?:id|key)?|token|apikey)\s*[=:]\s*[^\s,;]+/ig, '$1=[已隐藏]');
  return message.slice(0, 500);
}

function audioDeleteRetryable(error) {
  const status = Number(error?.status || error?.statusCode || 0);
  return status === 429 || status >= 500 || /^(ECONNRESET|ECONNREFUSED|ETIMEDOUT|EAI_AGAIN|ENOTFOUND)$/i.test(String(error?.code || ''));
}

function wait(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

async function deleteTeachingAudioObject(asset, fetchImpl = fetch) {
  const objectPath = teachingAudioPath(asset);
  const result = await fetchImpl(
    `${SUPABASE_URL}/storage/v1/object/${AUDIO_BUCKET}/${encodeStoragePath(objectPath)}`,
    {
      method: 'DELETE',
      headers: {
        apikey: SUPABASE_SERVICE_ROLE_KEY,
        Authorization: `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`
      }
    }
  );
  const body = await result.text();
  const alreadyMissing = result.status === 404
    || /"statusCode"\s*:\s*"?404"?|"code"\s*:\s*"NoSuchKey"|Object not found/i.test(body);
  if (result.ok || alreadyMissing) return;
  const error = new Error(`教学音频删除失败，HTTP ${result.status}`);
  error.status = result.status;
  throw error;
}

async function deleteTeachingAudioWithRetry(asset, fetchImpl = fetch, waitFn = wait) {
  let lastError;
  for (let attempt = 1; attempt <= AUDIO_DELETE_MAX_ATTEMPTS; attempt++) {
    try {
      await deleteTeachingAudioObject(asset, fetchImpl);
      return;
    } catch (error) {
      lastError = error;
      if (!audioDeleteRetryable(error) || attempt === AUDIO_DELETE_MAX_ATTEMPTS) break;
      await waitFn(250 * (2 ** (attempt - 1)));
    }
  }
  throw lastError;
}

async function listArchivedTeachingAudio(target, supabaseRequest = supabase) {
  const fields = 'id,object_path,voice_version,item_type,item_order,root_literacy_character_id,recognized_character_id,status';
  const queries = [
    `literacy_tts_assets?select=${fields}&status=neq.deleted&recognized_character_id=eq.${encodeURIComponent(target.id)}`
  ];
  if (target.source_literacy_character_id) {
    queries.push(
      `literacy_tts_assets?select=${fields}&status=neq.deleted&root_literacy_character_id=eq.${encodeURIComponent(target.source_literacy_character_id)}`
    );
  }
  const pages = await Promise.all(queries.map((query) => supabaseRequest(query)));
  return [...new Map(pages.flat().filter(Boolean).map((asset) => [asset.id, asset])).values()];
}

async function deleteArchivedTeachingAudio(target, {
  supabaseRequest = supabase,
  fetchImpl = fetch,
  waitFn = wait
} = {}) {
  const assets = await listArchivedTeachingAudio(target, supabaseRequest);
  for (const asset of assets) {
    try {
      await deleteTeachingAudioWithRetry(asset, fetchImpl, waitFn);
      await supabaseRequest(`literacy_tts_assets?id=eq.${encodeURIComponent(asset.id)}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', Prefer: 'return=minimal' },
        body: JSON.stringify({ status: 'deleted', deleted_at: new Date().toISOString(), last_error: null, updated_at: new Date().toISOString() })
      });
    } catch (error) {
      throw new HttpError(503, '教学音频清理失败，请稍后重试');
    }
  }

  const deleteOptions = { method: 'DELETE', headers: { Prefer: 'return=minimal' } };
  await supabaseRequest(
    `literacy_tts_assets?recognized_character_id=eq.${encodeURIComponent(target.id)}&status=eq.deleted`,
    deleteOptions
  );
  if (target.source_literacy_character_id) {
    await supabaseRequest(
      `literacy_tts_assets?root_literacy_character_id=eq.${encodeURIComponent(target.source_literacy_character_id)}&status=eq.deleted`,
      deleteOptions
    );
  }
  await supabaseRequest(
    `recognized_characters?id=eq.${encodeURIComponent(target.id)}`,
    deleteOptions
  );
}

async function handler(event) {
  const body = requestBody(event);
  // 后台回填只访问内部 service_role 队列，不读取或修改任何单个孩子的业务数据。
  // 必须先匹配独立密钥；不要将此分支放到普通 JWT 鉴权之后，否则定时任务仍无法调用。
  if (body.action === 'run_phonetic_backfill') {
    requirePhoneticBackfillKey(event);
    const generated = await generatePhoneticAssets(body.limit);
    console.info('音素资产后台回填完成一批', generated);
    return response(200, { generated });
  }
  const childId = await authenticatedChild(event);
  if (body.action === 'issue_credentials') {
    const sts = await issueStsCredentials();
    return response(200, {
      appId: TENCENT_APP_ID,
      credentials: {
        secretId: sts.Credentials.TmpSecretId,
        secretKey: sts.Credentials.TmpSecretKey,
        token: sts.Credentials.Token,
        expiredTime: sts.ExpiredTime
      }
    });
  }
  if (body.action === 'issue_session') {
    // 评测统一使用腾讯内置词典；拼音字段不再是发起评测的前置条件。
    const target = await loadTarget(childId, body.literacyCharacterId, body.targetType, body.sentenceText, body.wordText, body.contentSource);
    const sts = await issueStsCredentials();
    return response(200, {
      appId: TENCENT_APP_ID,
      credentials: {
        secretId: sts.Credentials.TmpSecretId,
        secretKey: sts.Credentials.TmpSecretKey,
        token: sts.Credentials.Token,
        expiredTime: sts.ExpiredTime
      },
      evaluation: {
        // 字、词、句均由腾讯词典按完整文本进行发音归一化，覆盖轻声、变调和常见多音字。
        refText: target.targetText,
        textMode: 0,
        // 客户端据此确认词组评测已被云端收敛为当前的一个词，避免旧服务静默回退成整组评测。
        targetText: target.targetText
      }
    });
  }
  if (body.action === 'prepare_evaluation') {
    return response(200, { evaluation: await prepareEvaluation(childId, body) });
  }
  if (body.action === 'get_phonetic_assets') {
    return response(200, await loadPhoneticAssets(childId, body.literacyCharacterId, body.contentSource));
  }
  if (body.action === 'save_phonetic_asset') {
    return response(200, await savePhoneticAsset(childId, body));
  }
  // 供受控的定时调用或故障恢复使用；函数仍要求有效的孩子登录凭证，避免暴露
  // service_role 的音素写入能力。正常新建会在保存后立即执行一次。
  if (body.action === 'generate_phonetic_assets') {
    return response(200, { generated: await generatePhoneticAssets(body.limit) });
  }
  if (body.action === 'record_help_request') {
    if (typeof body.character !== 'string' || !/^[\u4E00-\u9FFF]$/.test(body.character)) {
      throw new HttpError(400, 'character 必须是一个汉字');
    }
    if (!Number.isInteger(body.characterIndex) || body.characterIndex < 0) {
      throw new HttpError(400, 'characterIndex 必须是非负整数');
    }
    const target = await loadTarget(childId, body.literacyCharacterId, body.targetType, body.sentenceText, body.wordText, body.contentSource);
    if (target.targetText[body.characterIndex] !== body.character) {
      throw new HttpError(400, '请求帮助的汉字不属于当前朗读内容');
    }
    // 求助历史既保留完整词/句，也保留被长按的具体字和位置，供客户端回看时精确高亮。
    const contextText = resolveHelpContext(target, body.characterIndex);
    if (!await shouldRecordHelpRequest(childId, body.character)) {
      return response(200, { status: 'skipped', help: { character: body.character, contextText } });
    }
    await supabase('child_literacy_character_help_requests?on_conflict=child_id,target_type,target_text,requested_character,character_index', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Prefer: 'resolution=ignore-duplicates,return=minimal' },
      body: JSON.stringify({
        child_id: target.character.child_id,
        target_type: target.targetType,
        target_text: contextText,
        requested_character: body.character,
        character_index: body.characterIndex,
        ...(target.contentSource === 'recognized'
          ? { recognized_character_id: target.character.id }
          : { literacy_character_id: target.character.id })
      })
    });
    return response(201, { status: 'recorded', help: { character: body.character, contextText } });
  }
  if (body.action === 'complete_literacy_character') {
    const completed = await completeLiteracyCharacter(childId, body.literacyCharacterId, body.hasCharacterAudioPointRead);
    return response(201, { status: 'completed', completed });
  }
  if (body.action === 'archive_recognized_character') {
    const archived = await archiveRecognizedCharacter(childId, body.recognizedCharacterId);
    return response(201, { status: 'archived', archived });
  }
  if (body.action === 'top_recognized_character') {
    const topped = await topRecognizedCharacter(childId, body.recognizedCharacterId);
    return response(200, { status: 'topped', topped });
  }
  if (body.action === 'preview_literacy_tasks') {
    console.info('识字任务预览开始');
    const preview = await previewGeneratedLiteracyTasks(childId, body.characters);
    console.info('识字任务预览完成', {
      taskCount: preview.tasks.length,
      quality: literacyTaskQualitySummary(preview.tasks)
    });
    return response(200, { status: 'previewed', preview });
  }
  if (body.action === 'save_literacy_tasks') {
    const generated = await saveGeneratedLiteracyTasks(childId, body.characters, body.items);
    const audioGeneration = await triggerNewLiteracyTaskAudio(generated.created);
    const phoneticGeneration = await generatePhoneticAssets(Math.max(1, generated.created.length * 4));
    return response(201, { status: 'created', generated, audioGeneration, phoneticGeneration });
  }
  throw new HttpError(400, '未知 action');
}

exports.main_handler = async (event) => {
  try { return await handler(event || {}); }
  catch (error) {
    console.error(error);
    return response(error.statusCode || 500, { error: error.message || '服务暂时不可用' });
  }
};

exports._private = {
  archiveRecognizedCharacter,
  topRecognizedCharacter,
  rollbackRecognizedArchive,
  deleteArchivedTeachingAudio,
  deleteTeachingAudioWithRetry,
  teachingAudioPath,
  triggerNewLiteracyTaskAudio,
  phonemesForText,
  normalizePhonemeTokens,
  wordListForPhonemeTokens,
  requirePhoneticBackfillKey
};
