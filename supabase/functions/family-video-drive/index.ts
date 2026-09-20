// 123 云盘家庭动画库的唯一服务端边界。
// 仅保存同步元数据；123 应用密钥和 access token 永远不返回客户端，也不写入 Supabase 表。

const SUPABASE_URL = requiredEnv("SUPABASE_URL").replace(/\/$/, "");
const SERVICE_ROLE_KEY = requiredEnv("SUPABASE_SERVICE_ROLE_KEY");
const PAN_CLIENT_ID = requiredEnv("PAN123_CLIENT_ID");
const PAN_CLIENT_SECRET = requiredEnv("PAN123_CLIENT_SECRET");
const PAN_API = "https://open-api.123pan.com";
const PAN_REQUEST_TIMEOUT_MS = 20_000;
const PAN_REQUEST_RETRIES = 1;
const MEDIA_UPSERT_BATCH_SIZE = 100;
const VIDEO_EXTENSION = /\.(mp4|mkv|mov|m4v|webm|avi|ts)$/i;
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

type Json = Record<string, unknown>;
type DriveFile = {
  fileId: number | string;
  filename: string;
  type: number;
  size?: number;
  trashed?: number;
};
type FamilyContext = { uid: string; familyId: string };

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (request.method !== "POST") return response({ error: "只支持 POST 请求" }, 405);

  try {
    const context = await authenticatedFamily(request);
    const payload = await request.json() as Json;
    const action = text(payload.action);
    if (!action) throw new HttpError("缺少 action", 400);

    let data: Json;
    switch (action) {
      case "connection_status": data = await connectionStatus(context.familyId); break;
      case "connect": data = await connectDrive(context.familyId); break;
      case "browse": data = await browse(payload); break;
      case "sync_collection": data = await syncCollection(context.familyId, payload); break;
      case "playback_url": data = await playbackUrl(context.familyId, payload); break;
      default: throw new HttpError("不支持的云盘操作", 400);
    }
    return response({ data });
  } catch (error) {
    const status = error instanceof HttpError ? error.status : 500;
    const message = error instanceof Error ? error.message : "云盘服务发生未知错误";
    console.error("family-video-drive", { status, message });
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
  if (!result.ok) throw new HttpError(`数据库操作失败：${body}`, 500);
  // PostgREST 在未指定 return=representation 的新增/更新请求中通常返回 201 和空响应体，
  // 而不是 204。空响应是成功结果，不能继续调用 JSON.parse。
  if (!body.trim()) return undefined as T;
  try {
    return JSON.parse(body) as T;
  } catch {
    throw new HttpError("数据库返回格式错误", 500);
  }
}

async function driveToken(): Promise<string> {
  if (cachedToken && Date.now() < cachedToken.expiresAt - 60_000) return cachedToken.value;
  if (pendingToken) return pendingToken;

  const tokenRequest = requestDriveToken();
  pendingToken = tokenRequest;
  try {
    return await tokenRequest;
  } finally {
    // 只清理自己的请求，不能误清理后续已发起的刷新请求。
    if (pendingToken === tokenRequest) pendingToken = undefined;
  }
}

async function requestDriveToken(): Promise<string> {
  const result = await panFetch(`${PAN_API}/api/v1/access_token`, {
    method: "POST",
    headers: { platform: "open_platform", "Content-Type": "application/json" },
    body: JSON.stringify({ clientID: PAN_CLIENT_ID, clientSecret: PAN_CLIENT_SECRET }),
  });
  const body = await result.json() as Json;
  const data = object(body.data);
  const token = text(data.accessToken);
  const expiredAt = Date.parse(text(data.expiredAt) ?? "");
  if (!result.ok || Number(body.code) !== 0 || !token || Number.isNaN(expiredAt)) {
    throw new HttpError(`123 云盘授权失败：${text(body.message) ?? "请检查 OpenAPI 密钥"}`, 502);
  }
  cachedToken = { value: token, expiresAt: expiredAt };
  return token;
}

async function pan(path: string, query: Record<string, string> = {}): Promise<Json> {
  const url = new URL(`${PAN_API}${path}`);
  Object.entries(query).forEach(([key, value]) => url.searchParams.set(key, value));
  const result = await panFetch(url, {
    headers: { authorization: `Bearer ${await driveToken()}`, platform: "open_platform" },
  });
  const body = await result.json() as Json;
  if (!result.ok || Number(body.code) !== 0) {
    if (Number(body.code) === 401) cachedToken = undefined;
    throw new HttpError(`123 云盘请求失败：${text(body.message) ?? "请稍后重试"}`, 502);
  }
  return body;
}

/** 123 云盘网络异常时必须返回错误，避免 Edge Function 和目录选择界面无限等待。 */
async function panFetch(input: RequestInfo | URL, init: RequestInit): Promise<Response> {
  for (let attempt = 0; attempt <= PAN_REQUEST_RETRIES; attempt += 1) {
    const controller = new AbortController();
    let timedOut = false;
    const timer = setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, PAN_REQUEST_TIMEOUT_MS);
    try {
      return await fetch(input, { ...init, signal: controller.signal });
    } catch (error) {
      if (!timedOut || attempt === PAN_REQUEST_RETRIES) {
        if (timedOut) throw new HttpError("123 云盘响应超时，请稍后重试", 504);
        throw error;
      }
      // 123 OpenAPI 偶发长连接超时；读操作重试一次，不会重复写入任何云盘数据。
      await new Promise((resolve) => setTimeout(resolve, 500));
    } finally {
      clearTimeout(timer);
    }
  }
  throw new HttpError("123 云盘响应超时，请稍后重试", 504);
}

async function listFolder(parentFileId: string): Promise<DriveFile[]> {
  const files: DriveFile[] = [];
  let lastFileId = "0";
  const seenCursors = new Set<string>();
  while (true) {
    // 123 云盘在末页可能回传与请求相同的游标；若继续请求会无限循环，导致客户端始终显示处理中。
    if (seenCursors.has(lastFileId)) break;
    seenCursors.add(lastFileId);
    const body = await pan("/api/v2/file/list", { parentFileId, limit: "100", lastFileId, trashed: "false", searchMode: "", searchData: "" });
    const data = object(body.data);
    const page = Array.isArray(data.fileList) ? data.fileList as DriveFile[] : [];
    files.push(...page.filter((file) => Number(file.trashed ?? 0) === 0));
    const nextCursor = text(data.lastFileId);
    if (!nextCursor || nextCursor === "-1" || seenCursors.has(nextCursor)) break;
    lastFileId = nextCursor;
  }
  return files;
}

async function connectionStatus(familyId: string): Promise<Json> {
  const rows = await supabase<Json[]>(`video_drive_connections?family_id=eq.${encodeURIComponent(familyId)}&select=authorization_status,drive_account_hint,sync_root_folder_id,sync_root_path,last_synced_at&limit=1`);
  return rows[0] ?? { authorization_status: "disconnected" };
}

async function connectDrive(familyId: string): Promise<Json> {
  const info = object((await pan("/api/v1/user/info")).data);
  const uid = text(info.uid) ?? "";
  const hint = uid ? `123 云盘账号（UID ****${uid.slice(-4)}）` : "123 云盘已连接";
  const rows = await supabase<Json[]>("video_drive_connections?on_conflict=family_id", {
    method: "POST",
    headers: { Prefer: "resolution=merge-duplicates,return=representation" },
    body: JSON.stringify({ family_id: familyId, provider: "123pan", drive_account_hint: hint, authorization_status: "connected", updated_at: new Date().toISOString() }),
  });
  return rows[0] ?? { authorization_status: "connected", drive_account_hint: hint };
}

async function browse(payload: Json): Promise<Json> {
  const parentFolderId = text(payload.parentFolderId) ?? "0";
  const breadcrumb = text(payload.breadcrumb) ?? "123 云盘";
  const folders = (await listFolder(parentFolderId)).filter((item) => Number(item.type) === 1).map((item) => ({
    id: String(item.fileId), name: item.filename, isFolder: true, breadcrumb: `${breadcrumb} / ${item.filename}`,
  }));
  return { folders: folders.sort((a, b) => naturalCompare(a.name, b.name)) };
}

/**
 * 手工创建的每个条目只读取其绑定目录的直接视频：不会递归，也不会自动生成子剧集。
 * 子剧集由 App 写入 parent_id 后分别绑定目录并调用本接口，因此父/子内容永不重复。
 */
async function syncCollection(familyId: string, payload: Json): Promise<Json> {
  const collectionId = text(payload.collectionId);
  if (!collectionId) throw new HttpError("缺少媒体条目", 400);
  const collections = await supabase<Json[]>(`video_collections?id=eq.${encodeURIComponent(collectionId)}&family_id=eq.${encodeURIComponent(familyId)}&select=id,drive_folder_id`);
  const collection = collections[0];
  const folderId = text(collection?.drive_folder_id);
  if (!folderId) throw new HttpError("该条目尚未绑定云盘目录", 400);

  try {
    const videos = (await listFolder(folderId))
      .filter((entry) => Number(entry.type) !== 1 && VIDEO_EXTENSION.test(entry.filename))
      .map((entry) => ({ id: String(entry.fileId), name: entry.filename, path: entry.filename, size: typeof entry.size === "number" ? entry.size : null }))
      .sort((left, right) => naturalCompare(left.name, right.name));
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

async function playbackUrl(familyId: string, payload: Json): Promise<Json> {
  const fileId = text(payload.fileId);
  if (!fileId) throw new HttpError("缺少云盘文件 ID", 400);
  // 先验证该文件属于当前家庭，防止任何登录用户借接口读取任意文件的临时链接。
  const media = await supabase<Json[]>(`video_media?drive_file_id=eq.${encodeURIComponent(fileId)}&select=collection_id,video_collections!inner(family_id)&limit=1`);
  const ownerFamily = text(object(media[0]?.video_collections).family_id);
  if (ownerFamily !== familyId) throw new HttpError("该视频不属于当前家庭媒体库", 403);
  const data = object((await pan("/api/v1/file/download_info", { fileId })).data);
  const url = text(data.downloadUrl);
  if (!url) throw new HttpError("云盘未返回可播放地址", 502);
  return { url };
}

function naturalCompare(left: string, right: string): number {
  return left.localeCompare(right, "zh-CN", { numeric: true, sensitivity: "base" });
}
function text(value: unknown): string | undefined { return typeof value === "string" || typeof value === "number" ? String(value) : undefined; }
function object(value: unknown): Json { return value && typeof value === "object" && !Array.isArray(value) ? value as Json : {}; }
