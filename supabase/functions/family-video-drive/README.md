# 家庭动画云盘服务

这个 Supabase Edge Function 用应用凭证换取 123 云盘的短期 access token，并代为浏览目录、同步元数据、签发临时播放链接和读取目录约定封面。它不会接收或保存 123 云盘手机号、密码或播放链接。为优先复用授权，短期应用级 access token 会保存到受 RLS 保护、无客户端策略的服务端缓存表；不会返回给客户端或写入日志。

## 部署前配置

在 Supabase Dashboard 的 Edge Functions Secrets 中配置以下两个值（不要写进仓库）：

```text
PAN123_CLIENT_ID=<123 开放平台 Client ID>
PAN123_CLIENT_SECRET=<123 开放平台 Client Secret>
```

项目内置的 `SUPABASE_URL`、`SUPABASE_SERVICE_ROLE_KEY` 由 Edge Functions 运行时提供。部署命令：

```bash
supabase functions deploy family-video-drive --no-verify-jwt
```

函数自身会验证客户端传来的 Supabase JWT，并且只允许已加入家庭的用户操作自己家庭的媒体库。`--no-verify-jwt` 是为了由函数显式返回中文鉴权错误；并不意味着匿名访问。

## 发布后的核验

1. 先在 SQL Editor 审查并执行 `supabase/sql/20260921_family_video_drive_token_cache.sql`，运行其末尾查询，确认 `video_drive_token_cache` 启用 RLS、没有策略，且查询结果不展示 `access_token`。
2. 在 Dashboard 的 **Edge Functions** 中确认 `family-video-drive` 状态为 `ACTIVE`，并确认 `Verify JWT` 为关闭状态；这是本函数由代码自行校验并返回中文错误的预期配置。
3. 不带 `Authorization` 请求函数应收到 HTTP 401 和“请先登录”，而不是 500。该结果表示函数已启动，且没有绕过登录校验。
4. 在真机使用家庭成员的邮箱密码登录，在“我的”页连接云盘，再到“整理媒体库”创建条目、选择目录、确认 `folder.png`、`folder.jpg` 或 `folder.jpeg` 封面预览、刷新和播放；在 Functions Logs 中确认没有 123 OpenAPI 或数据库错误。

不要使用 `supabase secrets list` 的输出写入文档、日志或工单；日常只需确认 Secret 名称存在即可。

## 运行边界

- 使用 123 OpenAPI 的 `/api/v1/access_token`、`/api/v2/file/list`、`/api/v1/file/download_info`；视频流始终由手机直连 123 云盘。
- 授权按“Edge 内存 → `video_drive_token_cache` → 重新授权”三级复用；token 会用到 123 声明的实际到期时刻，或在 123 认证拒绝时才重新授权，认证拒绝仅重试当前读操作一次。`browse` 每次只读取一个列表页，Edge 按“目录 ID + 游标”缓存该页五分钟并合并相同并发读取；Android 按登录用户缓存成功目录页 24 小时，并能以 `forceRefresh` 绕过两级页缓存。`folder_cover` 缓存按家庭与目录隔离：首次名称搜索必须验证结果父目录，无法验证时至多降级读取一页；成功后函数签发目录绑定的短期定位凭据，供 Android 再次选择同一目录时复用。`sync_collection` 仍读取完整目录并始终获取最新结果。
- 新建条目读取所选目录直接子项中精确命名为 `folder.png`、`folder.jpg` 或 `folder.jpeg` 的封面；函数只签发短期下载链接，Android 必须立即上传到 `video-covers`，不得持久化链接。Android 定位缓存仅含目录 ID、封面文件 ID、名称、服务端归属凭据和时间戳，不含 token、下载链接、图片字节、请求头或原始云盘响应；没有有效归属凭据的裸文件 ID绝不作为封面快速路径。
- 仅同步 App 已创建条目的绑定目录中的直接视频；不递归云盘子目录，也不会自动创建剧集或子剧集。子剧集由 App 明确创建并独立绑定目录。
- 同步采用稳定 file ID upsert，并移除已不在绑定目录中的媒体元数据；不会移动、创建或删除任何云盘文件。
- `connect`、`browse`、`folder_cover`、`playback_url` 各有 85 秒总预算；`sync_collection` 为 55 秒。目录选择和封面定位的单页 123 列表请求最长 25 秒，其余上游读请求最长 20 秒；目录分页未完整读取时同步不会开始替换媒体，保留上一次成功数据。所有 123 只读接口对网络异常、超时、HTTP 429/5xx 最多重试一次，间隔 500ms；失败诊断只记录操作标签、HTTP 状态、123 数值 code（如有）、尝试次数、页数和耗时。
