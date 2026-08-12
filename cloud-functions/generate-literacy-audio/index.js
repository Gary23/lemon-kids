'use strict';

/**
 * 腾讯云 SCF（事件函数）：认字端预生成 MP3。
 *
 * 不创建 Web/API Gateway 入口。仅允许拥有 SCF InvokeFunction 权限的管理端、
 * 云控制台和定时触发器调用；孩子端没有该权限，也不会持有任何腾讯云密钥。
 */
const tencentcloud = require('tencentcloud-sdk-nodejs-tts');
const TtsClient = tencentcloud.tts.v20190823.Client;
const {
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
} = require('./lib');

const MAX_RETRIES_PER_REQUEST = 3;

function configuration() {
  const codec = requiredEnv('TENCENT_TTS_CODEC').toLowerCase();
  if (codec !== 'mp3') throw new Error('TENCENT_TTS_CODEC 必须为 mp3，以匹配 literacy-audio 的 audio/mpeg 限制');
  return {
    supabaseUrl: requiredEnv('SUPABASE_URL').replace(/\/$/, ''),
    supabaseServiceRoleKey: requiredEnv('SUPABASE_SERVICE_ROLE_KEY'),
    secretId: requiredEnv('TENCENT_TTS_SECRET_ID'),
    secretKey: requiredEnv('TENCENT_TTS_SECRET_KEY'),
    region: requiredEnv('TENCENT_TTS_REGION'),
    voiceType: positiveInteger(requiredEnv('TENCENT_TTS_VOICE_TYPE'), 'TENCENT_TTS_VOICE_TYPE'),
    modelType: positiveInteger(requiredEnv('TENCENT_TTS_MODEL_TYPE'), 'TENCENT_TTS_MODEL_TYPE'),
    codec,
    sampleRate: positiveInteger(requiredEnv('TENCENT_TTS_SAMPLE_RATE'), 'TENCENT_TTS_SAMPLE_RATE'),
    voiceVersion: requiredEnv('TENCENT_TTS_VOICE_VERSION'),
    dailyCharacterLimit: positiveInteger(
      requiredEnv('TENCENT_TTS_DAILY_CHARACTER_LIMIT'),
      'TENCENT_TTS_DAILY_CHARACTER_LIMIT'
    ),
    maxAttempts: positiveInteger(process.env.TENCENT_TTS_MAX_ATTEMPTS || '3', 'TENCENT_TTS_MAX_ATTEMPTS', 10)
  };
}

async function supabase(config, path, options = {}) {
  const response = await fetch(`${config.supabaseUrl}${path}`, {
    ...options,
    headers: {
      apikey: config.supabaseServiceRoleKey,
      Authorization: `Bearer ${config.supabaseServiceRoleKey}`,
      ...(options.headers || {})
    }
  });
  const text = await response.text();
  if (!response.ok) {
    const error = new Error(`Supabase ${response.status}: ${text.slice(0, 500)}`);
    error.status = response.status;
    throw error;
  }
  if (!text) return null;
  try { return JSON.parse(text); } catch (_) { return text; }
}

function rpc(config, name, body) {
  return supabase(config, `/rest/v1/rpc/${name}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body)
  });
}

function ttsClient(config) {
  return new TtsClient({
    credential: { secretId: config.secretId, secretKey: config.secretKey },
    region: config.region,
    profile: { httpProfile: { endpoint: 'tts.tencentcloudapi.com' } }
  });
}

function sleep(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

async function retry(operation, sleepFn = sleep) {
  let lastError;
  for (let attempt = 1; attempt <= MAX_RETRIES_PER_REQUEST; attempt += 1) {
    try { return await operation(); }
    catch (error) {
      lastError = error;
      if (!isRetryable(error) || attempt === MAX_RETRIES_PER_REQUEST) break;
      await sleepFn(250 * (2 ** (attempt - 1)));
    }
  }
  throw lastError;
}

async function enqueueAssets(config, options) {
  return rpc(config, 'enqueue_literacy_tts_assets', {
    p_source: options.source,
    p_record_id: options.recordId,
    p_voice_version: config.voiceVersion,
    p_speed: -1
  });
}

async function listCandidates(config, options) {
  const fields = 'id,source_text,item_type,status,root_literacy_character_id,recognized_character_id';
  const sourceFilter = options.source === 'task'
    ? 'root_literacy_character_id=not.is.null'
    : 'root_literacy_character_id=is.null&recognized_character_id=not.is.null';
  const idFilter = options.recordId
    ? options.source === 'task'
      ? `&root_literacy_character_id=eq.${encodeURIComponent(options.recordId)}`
      : `&recognized_character_id=eq.${encodeURIComponent(options.recordId)}`
    : '';
  const statusFilter = options.retryFailed ? 'in.(pending,failed)' : 'eq.pending';
  const rows = [];
  for (let offset = 0; ; offset += 1000) {
    const page = await supabase(
      config,
      `/rest/v1/literacy_tts_assets?select=${fields}&${sourceFilter}${idFilter}&status=${statusFilter}&order=created_at.asc&offset=${offset}&limit=1000`,
      { headers: { Range: `${offset}-${offset + 999}` } }
    );
    rows.push(...(Array.isArray(page) ? page : []));
    if (!Array.isArray(page) || page.length < 1000) break;
  }
  return rows;
}

async function uploadAndValidate(config, objectPath, audio) {
  const encodedPath = objectPath.split('/').map(encodeURIComponent).join('/');
  const upload = await fetch(`${config.supabaseUrl}/storage/v1/object/${AUDIO_BUCKET}/${encodedPath}`, {
    method: 'POST',
    headers: {
      apikey: config.supabaseServiceRoleKey,
      Authorization: `Bearer ${config.supabaseServiceRoleKey}`,
      'content-type': 'audio/mpeg',
      'x-upsert': 'true'
    },
    body: audio
  });
  const uploadText = await upload.text();
  if (![200, 201].includes(upload.status)) {
    const error = new Error(`Storage 上传失败，HTTP ${upload.status}: ${uploadText.slice(0, 300)}`);
    error.status = upload.status;
    throw error;
  }

  const head = await fetch(`${config.supabaseUrl}/storage/v1/object/${AUDIO_BUCKET}/${encodedPath}`, {
    method: 'HEAD',
    headers: {
      apikey: config.supabaseServiceRoleKey,
      Authorization: `Bearer ${config.supabaseServiceRoleKey}`
    }
  });
  const mimeType = (head.headers.get('content-type') || '').toLowerCase();
  const size = Number(head.headers.get('content-length') || 0);
  if (!head.ok || !mimeType.startsWith('audio/mpeg') || !Number.isFinite(size) || size <= 0) {
    const error = new Error(
      `Storage 上传后校验失败：HTTP ${head.status}，MIME=${mimeType || '缺失'}，大小=${size}`
    );
    error.status = head.status;
    throw error;
  }
  return { mimeType, size };
}

async function markFailed(config, asset, error) {
  await supabase(config, `/rest/v1/literacy_tts_assets?id=eq.${encodeURIComponent(asset.id)}&status=eq.processing`, {
    method: 'PATCH',
    headers: { 'content-type': 'application/json', Prefer: 'return=minimal' },
    body: JSON.stringify({ status: 'failed', last_error: sanitizeError(error), updated_at: new Date().toISOString() })
  });
}

/** 删除教学对象；Storage 返回 404 说明对象已经不存在，按幂等成功处理。 */
async function deleteObjectIfPresent(config, objectPath) {
  const response = await fetch(
    `${config.supabaseUrl}/storage/v1/object/${AUDIO_BUCKET}/${encodeObjectPath(objectPath)}`,
    {
      method: 'DELETE',
      headers: {
        apikey: config.supabaseServiceRoleKey,
        Authorization: `Bearer ${config.supabaseServiceRoleKey}`
      }
    }
  );
  const text = await response.text();
  // Supabase Storage 网关有两种“对象不存在”响应：标准 HTTP 404，或外层 HTTP
  // 400、响应体内含 NoSuchKey/statusCode=404。两者都表示此前已删除，必须幂等成功。
  const alreadyMissing = response.status === 404
    || /"statusCode"\s*:\s*"?404"?|"code"\s*:\s*"NoSuchKey"|Object not found/i.test(text);
  if (response.ok || alreadyMissing) return { alreadyMissing };
  const error = new Error(`Storage 删除失败，HTTP ${response.status}: ${text.slice(0, 300)}`);
  error.status = response.status;
  throw error;
}

async function processDeletion(config, asset) {
  // object_path 在上传/回写之间可能为空；路径由受约束的资产字段可确定性重建，
  // 因而仍能清理由并发归档打断的“已上传但尚未 ready”的对象。
  const objectPath = asset.object_path || objectPathForAsset(asset);
  try {
    const deleted = await retry(() => deleteObjectIfPresent(config, objectPath));
    const completed = await rpc(config, 'mark_literacy_tts_asset_deleted', { p_asset_id: asset.id });
    console.info(JSON.stringify({
      event: 'literacy_tts_deleted', assetId: asset.id, objectPath,
      alreadyMissing: deleted.alreadyMissing,
      recognizedDeleted: completed?.recognized_deleted === true
    }));
    return { status: 'deleted', assetId: asset.id };
  } catch (error) {
    try {
      await rpc(config, 'defer_literacy_tts_asset_deletion', {
        p_asset_id: asset.id,
        p_reason: sanitizeError(error)
      });
    } catch (deferError) {
      console.error(JSON.stringify({
        event: 'literacy_tts_deletion_defer_error', assetId: asset.id,
        error: sanitizeError(deferError)
      }));
    }
    console.error(JSON.stringify({
      event: 'literacy_tts_deletion_failed', assetId: asset.id, objectPath,
      error: sanitizeError(error)
    }));
    return { status: 'failed', assetId: asset.id, error: sanitizeError(error) };
  }
}

async function cleanup(config, options) {
  const claimed = await rpc(config, 'claim_literacy_tts_assets_for_deletion', { p_limit: options.limit });
  const results = await runWithConcurrency(
    Array.isArray(claimed) ? claimed : [],
    options.concurrency,
    (asset) => processDeletion(config, asset)
  );
  const summary = results.reduce((counts, item) => {
    counts[item.status] = (counts[item.status] || 0) + 1;
    return counts;
  }, { deleted: 0, failed: 0 });
  console.info(JSON.stringify({ event: 'literacy_tts_cleanup_complete', claimed: results.length, ...summary }));
  return { action: 'cleanup', claimed: results.length, ...summary, results };
}

async function listAllLiveAssetPaths(config) {
  const fields = 'id,object_path,voice_version,item_type,item_order,root_literacy_character_id,recognized_character_id';
  const paths = new Set();
  for (let offset = 0; ; offset += 1000) {
    const rows = await supabase(
      config,
      `/rest/v1/literacy_tts_assets?select=${fields}&status=neq.deleted&order=id.asc&offset=${offset}&limit=1000`,
      { headers: { Range: `${offset}-${offset + 999}` } }
    );
    const page = Array.isArray(rows) ? rows : [];
    for (const asset of page) paths.add(asset.object_path || objectPathForAsset(asset));
    if (page.length < 1000) break;
  }
  return paths;
}

async function listStorageFolder(config, prefix, limit, offset) {
  const response = await fetch(`${config.supabaseUrl}/storage/v1/object/list/${AUDIO_BUCKET}`, {
    method: 'POST',
    headers: {
      apikey: config.supabaseServiceRoleKey,
      Authorization: `Bearer ${config.supabaseServiceRoleKey}`,
      'content-type': 'application/json'
    },
    body: JSON.stringify({
      prefix,
      limit,
      offset,
      sortBy: { column: 'name', order: 'asc' }
    })
  });
  const text = await response.text();
  if (!response.ok) throw new Error(`Storage 列表读取失败，HTTP ${response.status}: ${text.slice(0, 300)}`);
  const items = text ? JSON.parse(text) : [];
  return Array.isArray(items) ? items : [];
}

/** 专用 bucket 的树形扫描；文件项带 id，目录项没有 id。 */
async function listAllStoragePaths(config, limit) {
  const paths = new Set();
  const visitedPrefixes = new Set();
  async function visit(prefix) {
    if (visitedPrefixes.has(prefix)) return;
    visitedPrefixes.add(prefix);
    for (let offset = 0; ; offset += limit) {
      const entries = await listStorageFolder(config, prefix, limit, offset);
      for (const entry of entries) {
        const name = String(entry?.name || '');
        if (!name) continue;
        if (entry.id) {
          paths.add(`${prefix}${name}`);
        } else {
          await visit(`${prefix}${name.replace(/\/$/, '')}/`);
        }
      }
      if (entries.length < limit) break;
    }
  }
  await visit('');
  return paths;
}

/**
 * 每日对账：bucket 是教学音频专用 bucket，任何没有非 deleted 资产记录的对象
 * 都是可安全回收的孤儿（也覆盖“数据库已标 deleted 但对象仍存在”的情况）。
 */
async function reconcile(config, options) {
  const [livePaths, storagePaths] = await Promise.all([
    listAllLiveAssetPaths(config),
    listAllStoragePaths(config, options.limit)
  ]);
  const orphanPaths = [...storagePaths].filter((path) => !livePaths.has(path));
  const results = await runWithConcurrency(orphanPaths, options.concurrency, async (objectPath) => {
    try {
      await retry(() => deleteObjectIfPresent(config, objectPath));
      return { status: 'deleted', objectPath };
    } catch (error) {
      console.error(JSON.stringify({ event: 'literacy_tts_reconcile_delete_failed', objectPath, error: sanitizeError(error) }));
      return { status: 'failed', objectPath, error: sanitizeError(error) };
    }
  });
  const failed = results.filter((item) => item.status === 'failed').length;
  console.info(JSON.stringify({
    event: 'literacy_tts_reconcile_complete', liveAssets: livePaths.size,
    storageObjects: storagePaths.size, orphanObjects: orphanPaths.length,
    deleted: orphanPaths.length - failed, failed
  }));
  if (orphanPaths.length || failed) {
    console.warn(JSON.stringify({ event: 'literacy_tts_reconcile_alert', orphanObjects: orphanPaths.length, failed }));
  }
  return {
    action: 'reconcile', live_assets: livePaths.size, storage_objects: storagePaths.size,
    orphan_objects: orphanPaths.length, deleted: orphanPaths.length - failed, failed, results
  };
}

/**
 * 供定时触发器和日志告警读取的轻量快照。不调用腾讯 TTS，也不会改动任何资产；
 * 以 JSON 日志输出，避免把 service_role 或任何对象 URL 写入监控系统。
 */
async function monitor(config) {
  const rows = [];
  for (let offset = 0; ; offset += 1000) {
    const page = await supabase(
      config,
      `/rest/v1/literacy_tts_assets?select=status,created_at,updated_at&status=neq.deleted&order=created_at.asc&offset=${offset}&limit=1000`,
      { headers: { Range: `${offset}-${offset + 999}` } }
    );
    const assets = Array.isArray(page) ? page : [];
    rows.push(...assets);
    if (assets.length < 1000) break;
  }
  const usage = await supabase(
    config,
    '/rest/v1/literacy_tts_daily_usage?select=usage_date,character_count&order=usage_date.desc&limit=1'
  );
  const statuses = rows.reduce((counts, asset) => {
    counts[asset.status] = (counts[asset.status] || 0) + 1;
    return counts;
  }, {});
  const now = Date.now();
  const oldestDeletionUpdatedAt = rows
    .filter((asset) => ['delete_pending', 'deleting'].includes(asset.status))
    .map((asset) => Date.parse(asset.updated_at || asset.created_at || ''))
    .filter(Number.isFinite)
    .reduce((oldest, timestamp) => Math.min(oldest, timestamp), now);
  const latestUsage = Array.isArray(usage) ? usage[0] : null;
  const snapshot = {
    action: 'monitor',
    active_assets: rows.length,
    pending_assets: statuses.pending || 0,
    failed_assets: statuses.failed || 0,
    cleanup_pending_assets: (statuses.delete_pending || 0) + (statuses.deleting || 0),
    oldest_cleanup_pending_seconds: Math.max(0, Math.floor((now - oldestDeletionUpdatedAt) / 1000)),
    daily_tts_usage_date: latestUsage?.usage_date || null,
    daily_tts_character_count: Number(latestUsage?.character_count || 0)
  };
  console.info(JSON.stringify({ event: 'literacy_tts_monitor_snapshot', ...snapshot }));
  return snapshot;
}

async function processAsset(config, client, asset) {
  try {
    if (asset.speed !== -1) throw new Error(`资产 ${asset.id} 的 speed 必须为 -1`);
    const output = await retry(async () => {
      const allowed = await rpc(config, 'reserve_literacy_tts_characters', {
        p_character_count: characterCount(asset.source_text),
        p_daily_limit: config.dailyCharacterLimit
      });
      if (allowed !== true) throw new DailyLimitError();
      return client.TextToVoice({
        Text: asset.source_text,
        SessionId: asset.id,
        VoiceType: config.voiceType,
        ModelType: config.modelType,
        Codec: config.codec,
        SampleRate: config.sampleRate,
        Speed: -1,
        EmotionCategory: 'neutral',
        EmotionIntensity: 100
      });
    });
    const audio = Buffer.from(output.Audio || '', 'base64');
    if (!isMp3(audio)) throw new Error('腾讯 TTS 返回内容不是有效的 MP3 数据');

    const objectPath = objectPathForAsset(asset);
    const verified = await retry(() => uploadAndValidate(config, objectPath, audio));
    await rpc(config, 'mark_literacy_tts_asset_ready', {
      p_asset_id: asset.id,
      p_object_path: objectPath,
      p_audio_url: publicAudioUrl(config.supabaseUrl, objectPath),
      p_provider_request_id: output.RequestId || ''
    });
    console.info(JSON.stringify({
      event: 'literacy_tts_ready', assetId: asset.id, itemType: asset.item_type,
      rootLiteracyCharacterId: asset.root_literacy_character_id || null,
      recognizedCharacterId: asset.recognized_character_id || null,
      voiceVersion: asset.voice_version, bytes: verified.size, requestId: output.RequestId || null
    }));
    return { status: 'ready', assetId: asset.id, characters: characterCount(asset.source_text) };
  } catch (error) {
    if (error instanceof DailyLimitError) {
      await rpc(config, 'defer_literacy_tts_asset', { p_asset_id: asset.id, p_reason: error.message });
      return { status: 'deferred', assetId: asset.id, characters: 0 };
    }
    try { await markFailed(config, asset, error); }
    catch (markError) {
      console.error(JSON.stringify({ event: 'literacy_tts_mark_failed_error', assetId: asset.id, errorCode: markError.code || markError.Code || null, error: sanitizeError(markError) }));
    }
    console.error(JSON.stringify({
      event: 'literacy_tts_failed', assetId: asset.id, itemType: asset.item_type,
      rootLiteracyCharacterId: asset.root_literacy_character_id || null,
      recognizedCharacterId: asset.recognized_character_id || null,
      voiceVersion: asset.voice_version, errorCode: error.code || error.Code || null,
      error: sanitizeError(error)
    }));
    return { status: 'failed', assetId: asset.id, characters: 0, error: sanitizeError(error) };
  }
}

async function runWithConcurrency(items, concurrency, worker) {
  const results = [];
  let nextIndex = 0;
  async function run() {
    while (nextIndex < items.length) {
      const item = items[nextIndex];
      nextIndex += 1;
      results.push(await worker(item));
    }
  }
  await Promise.all(Array.from({ length: Math.min(concurrency, items.length) }, run));
  return results;
}

async function handler(event) {
  const config = configuration();
  const options = inputOptions(event);
  if (options.action === 'cleanup') return cleanup(config, options);
  if (options.action === 'reconcile') return reconcile(config, options);
  if (options.action === 'monitor') return monitor(config);
  const enqueued = await enqueueAssets(config, options);
  if (options.dryRun) {
    const candidates = await listCandidates(config, options);
    const estimatedCharacters = candidates.reduce((sum, asset) => sum + characterCount(asset.source_text), 0);
    const summary = {
      dry_run: true,
      source: options.source,
      record_id: options.recordId,
      // 系统转入的已认识字可能把既有根任务资产关联到自身，因此此数表示
      // 新建或补齐关联的队列行，不等同于必然新增的数据库行数。
      queue_rows_ensured: Number(enqueued || 0),
      candidate_count: candidates.length,
      estimated_tts_characters: estimatedCharacters,
      daily_character_limit: config.dailyCharacterLimit
    };
    // 事件函数控制台有时不会展示返回体；把不含密钥的汇总写入日志，方便审核 dry run。
    console.info(JSON.stringify({ event: 'literacy_tts_dry_run_complete', ...summary }));
    return summary;
  }

  const claimed = await rpc(config, 'claim_literacy_tts_assets', {
    p_source: options.source,
    p_record_id: options.recordId,
    p_limit: options.limit,
    p_retry_failed: options.retryFailed,
    p_max_attempts: config.maxAttempts
  });
  const client = ttsClient(config);
  const results = await runWithConcurrency(Array.isArray(claimed) ? claimed : [], options.concurrency,
    (asset) => processAsset(config, client, asset));
  const summary = results.reduce((counts, item) => {
    counts[item.status] = (counts[item.status] || 0) + 1;
    counts.characters += item.characters || 0;
    return counts;
  }, { ready: 0, failed: 0, deferred: 0, characters: 0 });
  console.info(JSON.stringify({ event: 'literacy_tts_batch_complete', source: options.source, claimed: results.length, ...summary }));
  return { dry_run: false, source: options.source, record_id: options.recordId, claimed: results.length, ...summary, results };
}

exports.main_handler = async (event) => {
  try { return await handler(event || {}); }
  catch (error) {
    const statusCode = error instanceof InputError ? error.statusCode : 500;
    console.error(JSON.stringify({ event: 'literacy_tts_batch_error', statusCode, error: sanitizeError(error) }));
    return { statusCode, error: sanitizeError(error) };
  }
};

exports._private = {
  configuration, uploadAndValidate, deleteObjectIfPresent, processDeletion,
  listCandidates, listAllLiveAssetPaths, listAllStoragePaths, processAsset, retry, monitor, handler
};
