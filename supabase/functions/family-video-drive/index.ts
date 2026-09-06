// 123 云盘家庭动画库的唯一服务端边界。
// 仅保存同步元数据；123 应用密钥和 access token 永远不返回客户端，也不写入 Supabase 表。

const SUPABASE_URL = requiredEnv("SUPABASE_URL").replace(/\/$/, "");
const SERVICE_ROLE_KEY = requiredEnv("SUPABASE_SERVICE_ROLE_KEY");
const PAN_CLIENT_ID = requiredEnv("PAN123_CLIENT_ID");
const PAN_CLIENT_SECRET = requiredEnv("PAN123_CLIENT_SECRET");
const PAN_API = "https://open-api.123pan.com";
const VIDEO_EXTENSION = /\.(mp4|mkv|mov|m4v|webm|avi|ts)$/i;
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
};

let cachedToken: { value: string; expiresAt: number } | undefined;

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
      case "select_root": data = await selectRoot(context.familyId, payload); break;
      case "sync": data = await syncLibrary(context.familyId, payload); break;
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
  if (!result.ok) throw new HttpError(`数据库操作失败：${await result.text()}`, 500);
  if (result.status === 204) return undefined as T;
  return await result.json() as T;
}

async function driveToken(): Promise<string> {
  if (cachedToken && Date.now() < cachedToken.expiresAt - 60_000) return cachedToken.value;
  const result = await fetch(`${PAN_API}/api/v1/access_token`, {
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
  const result = await fetch(url, {
    headers: { authorization: `Bearer ${await driveToken()}`, platform: "open_platform" },
  });
  const body = await result.json() as Json;
  if (!result.ok || Number(body.code) !== 0) {
    if (Number(body.code) === 401) cachedToken = undefined;
    throw new HttpError(`123 云盘请求失败：${text(body.message) ?? "请稍后重试"}`, 502);
  }
  return body;
}

async function listFolder(parentFileId: string): Promise<DriveFile[]> {
  const files: DriveFile[] = [];
  let lastFileId = "0";
  do {
    const body = await pan("/api/v2/file/list", { parentFileId, limit: "100", lastFileId, trashed: "false", searchMode: "", searchData: "" });
    const data = object(body.data);
    const page = Array.isArray(data.fileList) ? data.fileList as DriveFile[] : [];
    files.push(...page.filter((file) => Number(file.trashed ?? 0) === 0));
    lastFileId = text(data.lastFileId) ?? "-1";
  } while (lastFileId !== "-1");
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

async function selectRoot(familyId: string, payload: Json): Promise<Json> {
  const folderId = text(payload.folderId);
  const folderPath = text(payload.folderPath);
  if (!folderId || !folderPath) throw new HttpError("请选择一个云盘目录", 400);
  const rows = await supabase<Json[]>("video_drive_connections?on_conflict=family_id", {
    method: "POST",
    headers: { Prefer: "resolution=merge-duplicates,return=representation" },
    body: JSON.stringify({ family_id: familyId, provider: "123pan", sync_root_folder_id: folderId, sync_root_path: folderPath, authorization_status: "connected", updated_at: new Date().toISOString() }),
  });
  return rows[0] ?? { authorization_status: "connected", sync_root_folder_id: folderId, sync_root_path: folderPath };
}

async function syncLibrary(familyId: string, payload: Json): Promise<Json> {
  const connection = await connectionStatus(familyId);
  const rootFolderId = text(connection.sync_root_folder_id);
  if (!rootFolderId || rootFolderId !== text(payload.rootFolderId)) throw new HttpError("请先选择同步目录", 400);
  const rootPath = text(connection.sync_root_path) ?? "123 云盘";
  const roots = (await listFolder(rootFolderId)).filter((item) => Number(item.type) === 1);
  const current = await supabase<Json[]>(`video_collections?family_id=eq.${encodeURIComponent(familyId)}&select=id,drive_folder_id`);
  const currentByDriveId = new Map(current.map((item) => [text(item.drive_folder_id), item]));
  let added = 0;
  let updated = 0;

  for (const root of roots) {
    const folderId = String(root.fileId);
    if (currentByDriveId.has(folderId)) updated += 1; else added += 1;
    const collections = await supabase<Json[]>("video_collections?on_conflict=family_id,drive_folder_id", {
      method: "POST",
      headers: { Prefer: "resolution=merge-duplicates,return=representation" },
      body: JSON.stringify({ family_id: familyId, drive_folder_id: folderId, name: root.filename, sync_status: "ready", last_synced_at: new Date().toISOString(), updated_at: new Date().toISOString() }),
    });
    const collectionId = text(collections[0]?.id);
    if (!collectionId) throw new HttpError("无法保存同步目录", 500);
    const videos = await walkVideos(folderId, `${rootPath} / ${root.filename}`);
    const ordered = videos.sort((a, b) => naturalCompare(a.name, b.name));
    for (const [index, video] of ordered.entries()) {
      await supabase("video_media?on_conflict=collection_id,drive_file_id", {
        method: "POST",
        headers: { Prefer: "resolution=merge-duplicates" },
        body: JSON.stringify({ collection_id: collectionId, drive_file_id: video.id, name: video.name, path: video.path, size_bytes: video.size, sort_order: index, updated_at: new Date().toISOString() }),
      });
    }
  }

  const rootIds = new Set(roots.map((root) => String(root.fileId)));
  const unavailable = current.filter((item) => !rootIds.has(text(item.drive_folder_id) ?? ""));
  await Promise.all(unavailable.map((item) => supabase(`video_collections?id=eq.${encodeURIComponent(text(item.id) ?? "")}`, {
    method: "PATCH", body: JSON.stringify({ sync_status: "unavailable", updated_at: new Date().toISOString() }),
  })));
  const summary = { family_id: familyId, added_count: added, updated_count: updated, unavailable_count: unavailable.length };
  await supabase("video_sync_logs", { method: "POST", body: JSON.stringify(summary) });
  await supabase(`video_drive_connections?family_id=eq.${encodeURIComponent(familyId)}`, { method: "PATCH", body: JSON.stringify({ last_synced_at: new Date().toISOString(), authorization_status: "connected", updated_at: new Date().toISOString() }) });
  return summary;
}

async function walkVideos(folderId: string, path: string): Promise<Array<{ id: string; name: string; path: string; size: number | null }>> {
  const entries = await listFolder(folderId);
  const output: Array<{ id: string; name: string; path: string; size: number | null }> = [];
  for (const entry of entries) {
    if (Number(entry.type) === 1) output.push(...await walkVideos(String(entry.fileId), `${path} / ${entry.filename}`));
    else if (VIDEO_EXTENSION.test(entry.filename)) output.push({ id: String(entry.fileId), name: entry.filename, path: `${path} / ${entry.filename}`, size: typeof entry.size === "number" ? entry.size : null });
  }
  return output;
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
