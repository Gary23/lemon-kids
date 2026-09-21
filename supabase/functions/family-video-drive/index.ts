// 123 云盘家庭动画库的唯一服务端边界。
// 仅保存同步元数据；123 应用密钥和 access token 永远不返回客户端。
// access token 只允许保存在受 RLS 保护、无客户端策略的服务端缓存表中，以跨 Edge 冷启动复用。

const SUPABASE_URL = requiredEnv("SUPABASE_URL").replace(/\/$/, "");
const SERVICE_ROLE_KEY = requiredEnv("SUPABASE_SERVICE_ROLE_KEY");
const PAN_CLIENT_ID = requiredEnv("PAN123_CLIENT_ID");
const PAN_CLIENT_SECRET = requiredEnv("PAN123_CLIENT_SECRET");
const PAN_API = "https://open-api.123pan.com";
const PAN_REQUEST_TIMEOUT_MS = 20_000;
const FILE_LIST_REQUEST_TIMEOUT_MS = 25_000;
const PAN_REQUEST_RETRIES = 1;
const PAN_RETRY_DELAY_MS = 500;
const SYNC_COLLECTION_BUDGET_MS = 55_000;
const INTERACTIVE_REQUEST_BUDGET_MS = 85_000;
const FOLDER_LIST_CACHE_TTL_MS = 5 * 60_000;
const FOLDER_LIST_CACHE_LIMIT = 100;
const COVER_LOCATOR_PROOF_TTL_MS = 24 * 60 * 60_000;
const MEDIA_UPSERT_BATCH_SIZE = 100;
const VIDEO_EXTENSION = /\.(mp4|mkv|mov|m4v|webm|avi|ts)$/i;
const FOLDER_COVER_EXTENSION = /^folder\.(png|jpg|jpeg)$/i;
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
};

let cachedToken: { value: string; expiresAt: number } | undefined;
// 一个冷启动窗口内可能同时收到“播放”和“重试”请求。把授权请求合并，避免它们
// 分别占用 123 云盘连接并同时超时；token 只留在当前 Edge 实例内存中。
let pendingToken: Promise<string> | undefined;
let folderListCache = new Map<string, { files: DriveFile[]; expiresAt: number }>();
let pendingFolderLists = new Map<string, Promise<DriveFile[]>>();
let folderPageCache = new Map<string, { page: DriveFolderPage; expiresAt: number }>();
let pendingFolderPages = new Map<string, Promise<DriveFolderPage>>();
// 封面定位只缓存稳定的文件 ID 与名称；短期 downloadUrl 绝不缓存。
let folderCoverCache = new Map<string, { cover: DriveFile; expiresAt: number }>();
let pendingFolderCovers = new Map<string, Promise<DriveFile>>();
let coverLocatorSigningKey: Promise<CryptoKey> | undefined;

type Json = Record<string, unknown>;
type PanOperation = "access_token" | "file_list" | "download_info" | "user_info";
type PanDiagnostics = {
  operation: PanOperation;
  startedAt: number;
  attempts: number;
  pages: number;
  httpStatus?: number;
  panCode?: number;
};
type DriveFile = {
  fileId: number | string;
  filename: string;
  type: number;
  parentFileId?: number | string;
  size?: number;
  trashed?: number;
};
type CoverLocatorProof = {
  version: 1;
  scope: string;
  folderId: string;
  fileId: string;
  filename: string;
  expiresAt: number;
};
type DriveFolderPage = {
  files: DriveFile[];
  nextCursor?: string;
  hasMore: boolean;
};
type FamilyContext = { uid: string; familyId: string };
type DriveAction = "connect" | "browse" | "folder_cover" | "sync_collection" | "playback_url";
type DriveDeadline = { action: DriveAction; at: number };

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (request.method !== "POST") return response({ error: "只支持 POST 请求" }, 405);

  try {
    const context = await authenticatedFamily(request);
    const payload = await request.json() as Json;
    const action = text(payload.action);
    if (!action) throw new HttpError("缺少 action", 400);
    const deadline = driveDeadline(action);

    let data: Json;
    switch (action) {
      case "connection_status": data = await connectionStatus(context.familyId); break;
      case "connect": data = await connectDrive(context.familyId, deadline); break;
      case "browse": data = await browse(payload, deadline); break;
      case "folder_cover": data = await folderCover(context.familyId, payload, deadline); break;
      case "sync_collection": data = await syncCollection(context.familyId, payload, deadline); break;
      case "playback_url": data = await playbackUrl(context.familyId, payload, deadline); break;
      default: throw new HttpError("不支持的云盘操作", 400);
    }
    return response({ data });
  } catch (error) {
    const status = error instanceof HttpError ? error.status : 500;
    const message = error instanceof Error ? error.message : "云盘服务发生未知错误";
    // 123 请求失败已由 emitPanDiagnostic() 记录经过脱敏的诊断字段。这里不重复
    // 输出错误文本，避免将后续新增的上游错误内容意外带进 Functions Logs。
    return response({ error: message }, status);
  }
});

function requiredEnv(name: string): string {
  const value = Deno.env.get(name);
  if (!value) throw new Error(`缺少服务端环境变量 ${name}`);
  return value;
}

function response(body: Json, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: corsHeaders });
}

function driveDeadline(action: string): DriveDeadline | undefined {
  if (action === "sync_collection") return { action, at: Date.now() + SYNC_COLLECTION_BUDGET_MS };
  if (action === "connect" || action === "browse" || action === "folder_cover" || action === "playback_url") {
    return { action, at: Date.now() + INTERACTIVE_REQUEST_BUDGET_MS };
  }
  return undefined;
}

class HttpError extends Error {
  constructor(message: string, readonly status: number) { super(message); }
}

async function authenticatedFamily(request: Request): Promise<FamilyContext> {
  const authorization = request.headers.get("authorization");
  if (!authorization?.startsWith("Bearer ")) throw new HttpError("请先登录", 401);
  const userResult = await fetch(`${SUPABASE_URL}/auth/v1/user`, { headers: { Authorization: authorization, apikey: SERVICE_ROLE_KEY } });
  if (!userResult.ok) throw new HttpError("登录已过期，请重新登录", 401);
  const user = await userResult.json() as { id?: string };
  if (!user.id) throw new HttpError("无法识别登录用户", 401);
  const users = await supabase<Json[]>(`users?uid=eq.${encodeURIComponent(user.id)}&select=family_id&limit=1`);
  const familyId = text(users[0]?.family_id);
  if (!familyId) throw new HttpError("此账号尚未加入家庭", 403);
  return { uid: user.id, familyId };
}

async function supabase<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("apikey", SERVICE_ROLE_KEY);
  headers.set("Authorization", `Bearer ${SERVICE_ROLE_KEY}`);
  headers.set("Content-Type", "application/json");
  const result = await fetch(`${SUPABASE_URL}/rest/v1/${path}`, { ...init, headers });
  const body = await result.text();
  // 数据库响应可能含有本函数写入的敏感字段；不能回显到客户端或日志。
  if (!result.ok) throw new HttpError("数据库操作失败，请稍后重试", 500);
  // PostgREST 在未指定 return=representation 的新增/更新请求中通常返回 201 和空响应体，
  // 而不是 204。空响应是成功结果，不能继续调用 JSON.parse。
  if (!body.trim()) return undefined as T;
  try {
    return JSON.parse(body) as T;
  } catch {
    throw new HttpError("数据库返回格式错误", 500);
  }
}

async function driveToken(deadline?: DriveDeadline): Promise<string> {
  if (isTokenUsable(cachedToken)) return cachedToken.value;
  if (pendingToken) return waitForDeadline(pendingToken, deadline);

  const tokenRequest = loadOrRequestDriveToken(deadline);
  pendingToken = tokenRequest;
  // 调用方到达自身 deadline 时，底层授权请求仍可能在运行；只能在它真正结束后
  // 清理合并标记，否则随后到达的请求会重复授权。
  void tokenRequest.then(
    () => { if (pendingToken === tokenRequest) pendingToken = undefined; },
    () => { if (pendingToken === tokenRequest) pendingToken = undefined; },
  );
  return waitForDeadline(tokenRequest, deadline);
}

function isTokenUsable(token: { value: string; expiresAt: number } | undefined): token is { value: string; expiresAt: number } {
  return Boolean(token && Date.now() < token.expiresAt);
}

async function loadOrRequestDriveToken(deadline?: DriveDeadline): Promise<string> {
  const rows = await supabase<Array<{ access_token?: unknown; expires_at?: unknown }>>(
    "video_drive_token_cache?provider=eq.123pan&select=access_token,expires_at&limit=1",
  );
  const stored = rows[0];
  const value = text(stored?.access_token);
  const expiresAt = Date.parse(text(stored?.expires_at) ?? "");
  if (value && !Number.isNaN(expiresAt) && isTokenUsable({ value, expiresAt })) {
    cachedToken = { value, expiresAt };
    return value;
  }
  return requestDriveToken(deadline);
}

async function requestDriveToken(deadline?: DriveDeadline): Promise<string> {
  const diagnostics = newPanDiagnostics("access_token");
  const result = await panFetch(`${PAN_API}/api/v1/access_token`, {
    method: "POST",
    headers: { platform: "open_platform", "Content-Type": "application/json" },
    body: JSON.stringify({ clientID: PAN_CLIENT_ID, clientSecret: PAN_CLIENT_SECRET }),
  }, diagnostics, deadline);
  const body = await panBody(result, diagnostics);
  const data = object(body.data);
  const token = text(data.accessToken);
  const expiredAt = Date.parse(text(data.expiredAt) ?? "");
  if (!result.ok || Number(body.code) !== 0 || !token || Number.isNaN(expiredAt)) {
    throw driveFailure("access_token", diagnostics);
  }
  await supabase("video_drive_token_cache?on_conflict=provider", {
    method: "POST",
    headers: { Prefer: "resolution=merge-duplicates" },
    body: JSON.stringify({ provider: "123pan", access_token: token, expires_at: new Date(expiredAt).toISOString(), updated_at: new Date().toISOString() }),
  });
  cachedToken = { value: token, expiresAt: expiredAt };
  return token;
}

async function invalidateDriveToken(token: string): Promise<void> {
  if (cachedToken?.value === token) cachedToken = undefined;
  // 仅删除与本次被 123 拒绝的 token 一致的记录，避免并发刷新误删新 token。
  await supabase(`video_drive_token_cache?provider=eq.123pan&access_token=eq.${encodeURIComponent(token)}`, { method: "DELETE" });
}

async function waitForDeadline<T>(promise: Promise<T>, deadline?: DriveDeadline): Promise<T> {
  if (!deadline) return promise;
  const remaining = deadline.at - Date.now();
  if (remaining <= 0) throw driveBudgetExceeded(deadline);
  let timer: ReturnType<typeof setTimeout> | undefined;
  try {
    return await Promise.race([
      promise,
      new Promise<T>((_, reject) => { timer = setTimeout(() => reject(driveBudgetExceeded(deadline)), remaining); }),
    ]);
  } finally {
    if (timer !== undefined) clearTimeout(timer);
  }
}

async function pan(path: string, query: Record<string, string> = {}, operation: PanOperation = "file_list", diagnostics = newPanDiagnostics(operation), deadline?: DriveDeadline): Promise<Json> {
  const url = new URL(`${PAN_API}${path}`);
  Object.entries(query).forEach(([key, value]) => url.searchParams.set(key, value));
  for (let authorizationAttempt = 0; authorizationAttempt <= 1; authorizationAttempt += 1) {
    const token = await driveToken(deadline);
    const result = await panFetch(url, {
      headers: { authorization: `Bearer ${token}`, platform: "open_platform" },
    }, diagnostics, deadline);
    const body = await panBody(result, diagnostics);
    if (result.ok && Number(body.code) === 0) return body;
    if ((result.status === 401 || Number(body.code) === 401) && authorizationAttempt === 0) {
      await invalidateDriveToken(token);
      continue;
    }
    throw driveFailure(operation, diagnostics);
  }
  throw driveFailure(operation, diagnostics);
}

/** 123 云盘网络异常时必须返回错误，避免 Edge Function 和目录选择界面无限等待。 */
async function panFetch(input: RequestInfo | URL, init: RequestInit, diagnostics: PanDiagnostics, deadline?: DriveDeadline): Promise<Response> {
  for (let attempt = 0; attempt <= PAN_REQUEST_RETRIES; attempt += 1) {
    if (deadline && Date.now() >= deadline.at) throw driveBudgetExceeded(deadline, diagnostics);
    const controller = new AbortController();
    let timedOut = false;
    const requestTimeoutMs = diagnostics.operation === "file_list" ? FILE_LIST_REQUEST_TIMEOUT_MS : PAN_REQUEST_TIMEOUT_MS;
    const timeoutMs = deadline ? Math.min(requestTimeoutMs, Math.max(1, deadline.at - Date.now())) : requestTimeoutMs;
    const timer = setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, timeoutMs);
    try {
      diagnostics.attempts += 1;
      diagnostics.httpStatus = undefined;
      diagnostics.panCode = undefined;
      const result = await fetch(input, { ...init, signal: controller.signal });
      diagnostics.httpStatus = result.status;
      // 所有只读 123 OpenAPI 操作的 429 与服务端 5xx 都视为短暂性过载，最多重试一次。
      // 其他 HTTP 状态通常是凭据或请求错误，直接交给调用方处理，避免徒增等待。
      if ((result.status === 429 || result.status >= 500) && attempt < PAN_REQUEST_RETRIES) {
        await new Promise((resolve) => setTimeout(resolve, PAN_RETRY_DELAY_MS));
        continue;
      }
      return result;
    } catch {
      if (!timedOut || attempt === PAN_REQUEST_RETRIES) {
        if (timedOut) {
          if (deadline && Date.now() >= deadline.at) throw driveBudgetExceeded(deadline, diagnostics);
          throw driveTimeout(diagnostics);
        }
        emitPanDiagnostic(diagnostics);
        throw driveFailure(diagnostics.operation);
      }
      // 123 OpenAPI 偶发长连接超时；读操作重试一次，不会重复写入任何云盘数据。
      await new Promise((resolve) => setTimeout(resolve, PAN_RETRY_DELAY_MS));
    } finally {
      clearTimeout(timer);
    }
  }
  throw driveTimeout(diagnostics);
}

function newPanDiagnostics(operation: PanOperation): PanDiagnostics {
  return { operation, startedAt: Date.now(), attempts: 0, pages: 0 };
}

async function panBody(result: Response, diagnostics: PanDiagnostics): Promise<Json> {
  try {
    const body = await result.json() as Json;
    const panCode = Number(body.code);
    if (Number.isFinite(panCode)) diagnostics.panCode = panCode;
    return body;
  } catch {
    // 部分认证拒绝响应没有 JSON 正文，仍要走一次 token 失效/重新授权路径。
    if (result.status === 401) return {};
    throw driveFailure(diagnostics.operation, diagnostics);
  }
}

function emitPanDiagnostic(diagnostics: PanDiagnostics) {
  const fields = {
    operation: diagnostics.operation,
    ...(diagnostics.httpStatus === undefined ? {} : { http_status: diagnostics.httpStatus }),
    ...(diagnostics.panCode === undefined ? {} : { pan_code: diagnostics.panCode }),
    attempts: diagnostics.attempts,
    pages: diagnostics.pages,
    elapsed_ms: Date.now() - diagnostics.startedAt,
  };
  console.warn("family-video-drive pan diagnostic", fields);
}

function driveFailure(operation: PanOperation, diagnostics?: PanDiagnostics): HttpError {
  if (diagnostics) emitPanDiagnostic(diagnostics);
  const message = operation === "access_token" ? "123 云盘授权失败，请稍后重试"
    : operation === "file_list" ? "123 云盘目录读取失败，请稍后重试"
    : operation === "download_info" ? "123 云盘文件读取失败，请稍后重试"
    : "123 云盘连接失败，请稍后重试";
  return new HttpError(message, 502);
}

function driveTimeout(diagnostics: PanDiagnostics): HttpError {
  emitPanDiagnostic(diagnostics);
  const message = diagnostics.operation === "access_token" ? "123 云盘授权响应超时，请稍后重试"
    : diagnostics.operation === "file_list" ? "123 云盘目录读取超时，请稍后重试"
    : diagnostics.operation === "download_info" ? "123 云盘文件读取超时，请稍后重试"
    : "123 云盘连接响应超时，请稍后重试";
  return new HttpError(message, 504);
}

function driveBudgetExceeded(deadline: DriveDeadline, diagnostics?: PanDiagnostics): HttpError {
  if (diagnostics) emitPanDiagnostic(diagnostics);
  const message = deadline.action === "sync_collection" ? "123 云盘目录整体同步超时，已保留上次视频，可稍后刷新"
    : deadline.action === "browse" ? "123 云盘目录读取超时，请稍后重试"
    : deadline.action === "folder_cover" ? "123 云盘封面读取超时，请稍后重试"
    : deadline.action === "playback_url" ? "123 云盘播放地址读取超时，请稍后重试"
    : "123 云盘连接响应超时，请稍后重试";
  return new HttpError(message, 504);
}

async function listFolder(parentFileId: string, deadline?: DriveDeadline, diagnostics = newPanDiagnostics("file_list"), useCache = true): Promise<DriveFile[]> {
  if (useCache) {
    const cached = folderListCache.get(parentFileId);
    if (cached && Date.now() < cached.expiresAt) return cached.files;
    if (cached) folderListCache.delete(parentFileId);
    const pending = pendingFolderLists.get(parentFileId);
    if (pending) return waitForDeadline(pending, deadline);
  }

  const read = readFolder(parentFileId, deadline, diagnostics);
  if (!useCache) return read;
  pendingFolderLists.set(parentFileId, read);
  // 与授权合并相同：不能因某个 App 请求先超时就允许重复读同一目录。
  void read.then(
    (files) => {
      cacheFolderList(parentFileId, files);
      if (pendingFolderLists.get(parentFileId) === read) pendingFolderLists.delete(parentFileId);
    },
    () => { if (pendingFolderLists.get(parentFileId) === read) pendingFolderLists.delete(parentFileId); },
  );
  return waitForDeadline(read, deadline);
}

async function readFolder(parentFileId: string, deadline?: DriveDeadline, diagnostics = newPanDiagnostics("file_list")): Promise<DriveFile[]> {
  const files: DriveFile[] = [];
  let lastFileId = "0";
  const seenCursors = new Set<string>();
  while (true) {
    // 123 云盘在末页可能回传与请求相同的游标；若继续请求会无限循环，导致客户端始终显示处理中。
    if (seenCursors.has(lastFileId)) break;
    seenCursors.add(lastFileId);
    diagnostics.pages += 1;
    const body = await pan("/api/v2/file/list", { parentFileId, limit: "100", lastFileId, trashed: "false", searchMode: "", searchData: "" }, "file_list", diagnostics, deadline);
    const data = object(body.data);
    const page = Array.isArray(data.fileList) ? data.fileList as DriveFile[] : [];
    files.push(...page.filter((file) => Number(file.trashed ?? 0) === 0));
    const nextCursor = text(data.lastFileId);
    if (!nextCursor || nextCursor === "-1" || seenCursors.has(nextCursor)) break;
    lastFileId = nextCursor;
  }
  return files;
}

/**
 * 目录选择只需展示可进入的目录，不能为了过滤文件而串行读取完当前目录的所有分页。
 * 与完整扫描分开缓存，避免同步或封面查询把不完整页误当作完整目录。
 */
async function listFolderPage(parentFileId: string, cursor: string, deadline?: DriveDeadline, useCache = true): Promise<DriveFolderPage> {
  const key = folderPageCacheKey(parentFileId, cursor);
  if (useCache) {
    const cached = folderPageCache.get(key);
    if (cached && Date.now() < cached.expiresAt) return cached.page;
    if (cached) folderPageCache.delete(key);
    const pending = pendingFolderPages.get(key);
    if (pending) return waitForDeadline(pending, deadline);
  }

  const read = readFolderPage(parentFileId, cursor, deadline);
  if (!useCache) {
    const page = await read;
    cacheFolderPage(key, page);
    return page;
  }
  pendingFolderPages.set(key, read);
  void read.then(
    (page) => {
      cacheFolderPage(key, page);
      if (pendingFolderPages.get(key) === read) pendingFolderPages.delete(key);
    },
    () => { if (pendingFolderPages.get(key) === read) pendingFolderPages.delete(key); },
  );
  return waitForDeadline(read, deadline);
}

async function readFolderPage(parentFileId: string, cursor: string, deadline?: DriveDeadline): Promise<DriveFolderPage> {
  const diagnostics = newPanDiagnostics("file_list");
  diagnostics.pages = 1;
  const body = await pan("/api/v2/file/list", { parentFileId, limit: "100", lastFileId: cursor, trashed: "false", searchMode: "", searchData: "" }, "file_list", diagnostics, deadline);
  const data = object(body.data);
  const files = (Array.isArray(data.fileList) ? data.fileList as DriveFile[] : [])
    .filter((file) => Number(file.trashed ?? 0) === 0);
  const candidate = text(data.lastFileId);
  const hasMore = Boolean(candidate && candidate !== "-1" && candidate !== cursor);
  return { files, nextCursor: hasMore ? candidate : undefined, hasMore };
}

function cacheFolderList(parentFileId: string, files: DriveFile[]) {
  for (const [key, entry] of folderListCache) {
    if (entry.expiresAt <= Date.now()) folderListCache.delete(key);
  }
  if (!folderListCache.has(parentFileId) && folderListCache.size >= FOLDER_LIST_CACHE_LIMIT) {
    const oldestKey = folderListCache.keys().next().value;
    if (oldestKey) folderListCache.delete(oldestKey);
  }
  folderListCache.set(parentFileId, { files, expiresAt: Date.now() + FOLDER_LIST_CACHE_TTL_MS });
}

function folderPageCacheKey(parentFileId: string, cursor: string) { return `${parentFileId}:${cursor}`; }

function cacheFolderPage(key: string, page: DriveFolderPage) {
  for (const [cachedKey, entry] of folderPageCache) {
    if (entry.expiresAt <= Date.now()) folderPageCache.delete(cachedKey);
  }
  if (!folderPageCache.has(key) && folderPageCache.size >= FOLDER_LIST_CACHE_LIMIT) {
    const oldestKey = folderPageCache.keys().next().value;
    if (oldestKey) folderPageCache.delete(oldestKey);
  }
  folderPageCache.set(key, { page, expiresAt: Date.now() + FOLDER_LIST_CACHE_TTL_MS });
}

async function connectionStatus(familyId: string): Promise<Json> {
  const rows = await supabase<Json[]>(`video_drive_connections?family_id=eq.${encodeURIComponent(familyId)}&select=authorization_status,drive_account_hint,sync_root_folder_id,sync_root_path,last_synced_at&limit=1`);
  return rows[0] ?? { authorization_status: "disconnected" };
}

async function connectDrive(familyId: string, deadline?: DriveDeadline): Promise<Json> {
  const info = object((await pan("/api/v1/user/info", {}, "user_info", newPanDiagnostics("user_info"), deadline)).data);
  const uid = text(info.uid) ?? "";
  const hint = uid ? `123 云盘账号（UID ****${uid.slice(-4)}）` : "123 云盘已连接";
  const rows = await supabase<Json[]>("video_drive_connections?on_conflict=family_id", {
    method: "POST",
    headers: { Prefer: "resolution=merge-duplicates,return=representation" },
    body: JSON.stringify({ family_id: familyId, provider: "123pan", drive_account_hint: hint, authorization_status: "connected", updated_at: new Date().toISOString() }),
  });
  return rows[0] ?? { authorization_status: "connected", drive_account_hint: hint };
}

async function browse(payload: Json, deadline?: DriveDeadline): Promise<Json> {
  const parentFolderId = text(payload.parentFolderId) ?? "0";
  const breadcrumb = text(payload.breadcrumb) ?? "123 云盘";
  const cursor = text(payload.cursor) ?? "0";
  const page = await listFolderPage(parentFolderId, cursor, deadline, !boolean(payload.forceRefresh));
  const folders = page.files.filter((item) => Number(item.type) === 1).map((item) => ({
    id: String(item.fileId), name: item.filename, isFolder: true, breadcrumb: `${breadcrumb} / ${item.filename}`,
  }));
  return {
    folders: folders.sort((a, b) => naturalCompare(a.name, b.name)),
    next_cursor: page.nextCursor ?? null,
    has_more: page.hasMore,
  };
}

/**
 * 新建媒体条目时只读取所选目录直接子项中的约定封面。下载链接会过期，
 * 客户端收到后立即上传到自己的 video-covers bucket，不能把该链接写入数据库。
 */
async function folderCover(familyId: string, payload: Json, deadline?: DriveDeadline): Promise<Json> {
  const folderId = text(payload.folderId);
  if (!folderId || folderId === "0") throw new HttpError("请选择一个具体云盘目录", 400);
  const cacheKey = folderCoverCacheKey(familyId, folderId);
  const clientCandidate = await verifiedCoverLocator(familyId, folderId, text(payload.cachedLocatorProof));
  let cover = await locateFolderCover(cacheKey, folderId, clientCandidate, deadline);
  let data: Json;
  try {
    data = object((await pan("/api/v1/file/download_info", { fileId: String(cover.fileId) }, "download_info", newPanDiagnostics("download_info"), deadline)).data);
  } catch {
    // 文件被移动或删除时，定位缓存不能继续遮蔽重新查询。
    folderCoverCache.delete(cacheKey);
    cover = await locateFolderCover(cacheKey, folderId, undefined, deadline);
    data = object((await pan("/api/v1/file/download_info", { fileId: String(cover.fileId) }, "download_info", newPanDiagnostics("download_info"), deadline)).data);
  }
  const url = text(data.downloadUrl);
  if (!url) throw new HttpError("云盘未返回封面下载地址", 502);
  return {
    url,
    name: cover.filename,
    file_id: String(cover.fileId),
    locator_proof: await createCoverLocatorProof(familyId, folderId, cover),
  };
}

/**
 * 封面是约定文件名，不能再为它读完数百个视频。Android 只可复用由本函数签发的
 * “目录 + 文件”凭据；首次定位则利用列表接口的名称过滤，并校验返回项确属该目录。
 * 部分 123 节点对过滤参数兼容性不一，因此失败或无法核验归属时只降级读取首个普通
 * 列表页，绝不退回完整分页扫描。
 */
async function locateFolderCover(cacheKey: string, folderId: string, clientCandidate: DriveFile | undefined, deadline?: DriveDeadline): Promise<DriveFile> {
  if (clientCandidate) {
    cacheFolderCover(cacheKey, clientCandidate);
    return clientCandidate;
  }
  const cached = folderCoverCache.get(cacheKey);
  if (cached && Date.now() < cached.expiresAt) return cached.cover;
  if (cached) folderCoverCache.delete(cacheKey);
  const pending = pendingFolderCovers.get(cacheKey);
  if (pending) return waitForDeadline(pending, deadline);

  const locate = findFolderCover(folderId, deadline);
  pendingFolderCovers.set(cacheKey, locate);
  void locate.then(
    (cover) => {
      cacheFolderCover(cacheKey, cover);
      if (pendingFolderCovers.get(cacheKey) === locate) pendingFolderCovers.delete(cacheKey);
    },
    () => { if (pendingFolderCovers.get(cacheKey) === locate) pendingFolderCovers.delete(cacheKey); },
  );
  return waitForDeadline(locate, deadline);
}

async function findFolderCover(folderId: string, deadline?: DriveDeadline): Promise<DriveFile> {
  // 一次名称查询同时覆盖三种扩展名。名称匹配外还必须确认上游返回的父目录，
  // 不能假定 searchMode 与 parentFileId 在所有 123 节点上都会共同生效。
  try {
    const body = await pan("/api/v2/file/list", {
      parentFileId: folderId,
      limit: "100",
      lastFileId: "0",
      trashed: "false",
      searchMode: "filename",
      searchData: "folder",
    }, "file_list", newPanDiagnostics("file_list"), deadline);
    const candidate = folderCoverFromFiles(object(body.data), folderId);
    if (candidate) return candidate;
  } catch {
    // 过滤参数在个别上游节点不可用时，继续走一次受限普通列表降级路径。
  }
  const page = await readFolderPage(folderId, "0", deadline);
  const cover = folderCoverFromFiles({ fileList: page.files });
  if (cover) return cover;
  throw new HttpError("所选目录首批内容中没有 folder.png、folder.jpg 或 folder.jpeg，请确认文件名后重试", 404);
}

function folderCoverFromFiles(data: Json, expectedParentFileId?: string): DriveFile | undefined {
  const files = Array.isArray(data.fileList) ? data.fileList as DriveFile[] : [];
  return files
    .filter((item) => Number(item.trashed ?? 0) === 0
      && Number(item.type) !== 1
      && FOLDER_COVER_EXTENSION.test(item.filename)
      && (!expectedParentFileId || String(item.parentFileId ?? "") === expectedParentFileId))
    .sort((left, right) => folderCoverPriority(left.filename) - folderCoverPriority(right.filename))[0];
}

function folderCoverCacheKey(familyId: string, folderId: string): string {
  return JSON.stringify([familyId, folderId]);
}

function cacheFolderCover(cacheKey: string, cover: DriveFile) {
  for (const [key, entry] of folderCoverCache) {
    if (entry.expiresAt <= Date.now()) folderCoverCache.delete(key);
  }
  if (!folderCoverCache.has(cacheKey) && folderCoverCache.size >= FOLDER_LIST_CACHE_LIMIT) {
    const oldestKey = folderCoverCache.keys().next().value;
    if (oldestKey) folderCoverCache.delete(oldestKey);
  }
  folderCoverCache.set(cacheKey, { cover, expiresAt: Date.now() + FOLDER_LIST_CACHE_TTL_MS });
}

/**
 * APK 可以保存这个不含 token、下载链接的短期凭据，避免同一目录再次选择时重复列表查询。
 * 其内容绑定家庭、目录、文件与名称，并由服务端 HMAC 签发；客户端传入的裸文件 ID
 * 从此不再构成快速路径，旧版本缓存也就无法污染新选择。
 */
async function createCoverLocatorProof(familyId: string, folderId: string, cover: DriveFile): Promise<string> {
  const payload: CoverLocatorProof = {
    version: 1,
    scope: familyId,
    folderId,
    fileId: String(cover.fileId),
    filename: cover.filename,
    expiresAt: Date.now() + COVER_LOCATOR_PROOF_TTL_MS,
  };
  const encodedPayload = base64UrlEncode(new TextEncoder().encode(JSON.stringify(payload)));
  const signature = await crypto.subtle.sign("HMAC", await coverLocatorKey(), new TextEncoder().encode(encodedPayload));
  return `${encodedPayload}.${base64UrlEncode(new Uint8Array(signature))}`;
}

async function verifiedCoverLocator(familyId: string, folderId: string, proof: string | undefined): Promise<DriveFile | undefined> {
  if (!proof) return undefined;
  const pieces = proof.split(".");
  if (pieces.length !== 2 || !pieces[0] || !pieces[1]) return undefined;
  try {
    const encodedPayload = pieces[0];
    const signature = base64UrlDecode(pieces[1]);
    const valid = await crypto.subtle.verify("HMAC", await coverLocatorKey(), signature, new TextEncoder().encode(encodedPayload));
    if (!valid) return undefined;
    const payload = object(JSON.parse(new TextDecoder().decode(base64UrlDecode(encodedPayload))));
    const version = payload.version;
    const scope = text(payload.scope);
    const provenFolderId = text(payload.folderId);
    const fileId = text(payload.fileId);
    const filename = text(payload.filename);
    const expiresAt = Number(payload.expiresAt);
    if (version !== 1 || scope !== familyId || provenFolderId !== folderId || !fileId || !filename
      || !FOLDER_COVER_EXTENSION.test(filename) || !Number.isFinite(expiresAt) || Date.now() >= expiresAt) return undefined;
    return { fileId, filename, type: 0 };
  } catch {
    return undefined;
  }
}

function coverLocatorKey(): Promise<CryptoKey> {
  if (!coverLocatorSigningKey) {
    coverLocatorSigningKey = crypto.subtle.importKey(
      "raw",
      new TextEncoder().encode(SERVICE_ROLE_KEY),
      { name: "HMAC", hash: "SHA-256" },
      false,
      ["sign", "verify"],
    );
  }
  return coverLocatorSigningKey;
}

function base64UrlEncode(bytes: Uint8Array): string {
  let binary = "";
  bytes.forEach((byte) => { binary += String.fromCharCode(byte); });
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}

function base64UrlDecode(value: string): Uint8Array<ArrayBuffer> {
  const normalized = value.replaceAll("-", "+").replaceAll("_", "/");
  const padded = normalized + "=".repeat((4 - normalized.length % 4) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(new ArrayBuffer(binary.length));
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return bytes;
}

function folderCoverPriority(filename: string): number {
  const extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
  return extension === "png" ? 0 : extension === "jpg" ? 1 : 2;
}

/**
 * 手工创建的每个条目只读取其绑定目录的直接视频：不会递归，也不会自动生成子剧集。
 * 子剧集由 App 写入 parent_id 后分别绑定目录并调用本接口，因此父/子内容永不重复。
 */
async function syncCollection(familyId: string, payload: Json, deadline?: DriveDeadline): Promise<Json> {
  const collectionId = text(payload.collectionId);
  if (!collectionId) throw new HttpError("缺少媒体条目", 400);
  // 从进入同步操作开始计时；数据库校验也不能无限侵占目录同步预算。
  const syncDeadline = deadline ?? { action: "sync_collection" as const, at: Date.now() + SYNC_COLLECTION_BUDGET_MS };
  const diagnostics = newPanDiagnostics("file_list");
  const collections = await supabase<Json[]>(`video_collections?id=eq.${encodeURIComponent(collectionId)}&family_id=eq.${encodeURIComponent(familyId)}&select=id,drive_folder_id`);
  const collection = collections[0];
  const folderId = text(collection?.drive_folder_id);
  if (!folderId) throw new HttpError("该条目尚未绑定云盘目录", 400);

  try {
    // 手动同步必须读取 123 最新目录；成功后回填短期缓存，供紧随其后的浏览/封面读取复用。
    const files = await listFolder(folderId, syncDeadline, diagnostics, false);
    const videos = files
      .filter((entry) => Number(entry.type) !== 1 && VIDEO_EXTENSION.test(entry.filename))
      .map((entry) => ({ id: String(entry.fileId), name: entry.filename, path: entry.filename, size: typeof entry.size === "number" ? entry.size : null }))
      .sort((left, right) => naturalCompare(left.name, right.name));
    // 只有完整读取全部分页且预算仍充足时才开始替换，避免超时写入半份目录。
    if (Date.now() >= syncDeadline.at) throw driveBudgetExceeded(syncDeadline, diagnostics);
    cacheFolderList(folderId, files);
    await replaceMedia(collectionId, videos);
    const now = new Date().toISOString();
    await supabase(`video_collections?id=eq.${encodeURIComponent(collectionId)}`, {
      method: "PATCH", body: JSON.stringify({ sync_status: "ready", last_synced_at: now, updated_at: now }),
    });
    await supabase("video_sync_logs", {
      method: "POST", body: JSON.stringify({ family_id: familyId, added_count: 0, updated_count: 1, unavailable_count: 0 }),
    });
    return { added_count: 0, updated_count: 1, unavailable_count: 0, media_count: videos.length };
  } catch (error) {
    const now = new Date().toISOString();
    await supabase(`video_collections?id=eq.${encodeURIComponent(collectionId)}`, {
      method: "PATCH", body: JSON.stringify({ sync_status: "error", updated_at: now }),
    });
    throw error;
  }
}

async function upsertMedia(collectionId: string, videos: Array<{ id: string; name: string; path: string; size: number | null }>) {
  for (let start = 0; start < videos.length; start += MEDIA_UPSERT_BATCH_SIZE) {
    const updatedAt = new Date().toISOString();
    const batch = videos.slice(start, start + MEDIA_UPSERT_BATCH_SIZE).map((video, offset) => ({
      collection_id: collectionId,
      drive_file_id: video.id,
      name: video.name,
      path: video.path,
      size_bytes: video.size,
      sort_order: start + offset,
      updated_at: updatedAt,
    }));
    await supabase("video_media?on_conflict=collection_id,drive_file_id", {
      method: "POST",
      headers: { Prefer: "resolution=merge-duplicates" },
      body: JSON.stringify(batch),
    });
  }
}

async function replaceMedia(collectionId: string, videos: Array<{ id: string; name: string; path: string; size: number | null }>) {
  await upsertMedia(collectionId, videos);
  const previous = await supabase<Json[]>(`video_media?collection_id=eq.${encodeURIComponent(collectionId)}&select=id,drive_file_id`);
  const currentIds = new Set(videos.map((video) => video.id));
  // 删除云盘中已不存在的媒体元数据；删除 collection 时仍由外键级联处理。
  await Promise.all(previous.filter((row) => !currentIds.has(text(row.drive_file_id) ?? "")).map((row) =>
    supabase(`video_media?id=eq.${encodeURIComponent(text(row.id) ?? "")}`, { method: "DELETE" })
  ));
}

async function playbackUrl(familyId: string, payload: Json, deadline?: DriveDeadline): Promise<Json> {
  const fileId = text(payload.fileId);
  if (!fileId) throw new HttpError("缺少云盘文件 ID", 400);
  // 先验证该文件属于当前家庭，防止任何登录用户借接口读取任意文件的临时链接。
  const media = await supabase<Json[]>(`video_media?drive_file_id=eq.${encodeURIComponent(fileId)}&select=collection_id,video_collections!inner(family_id)&limit=1`);
  const ownerFamily = text(object(media[0]?.video_collections).family_id);
  if (ownerFamily !== familyId) throw new HttpError("该视频不属于当前家庭媒体库", 403);
  const data = object((await pan("/api/v1/file/download_info", { fileId }, "download_info", newPanDiagnostics("download_info"), deadline)).data);
  const url = text(data.downloadUrl);
  if (!url) throw new HttpError("云盘未返回可播放地址", 502);
  return { url };
}

function naturalCompare(left: string, right: string): number {
  return left.localeCompare(right, "zh-CN", { numeric: true, sensitivity: "base" });
}
function text(value: unknown): string | undefined { return typeof value === "string" || typeof value === "number" ? String(value) : undefined; }
function boolean(value: unknown): boolean { return value === true || value === "true"; }
function object(value: unknown): Json { return value && typeof value === "object" && !Array.isArray(value) ? value as Json : {}; }
