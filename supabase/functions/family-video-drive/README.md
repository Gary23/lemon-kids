# 家庭动画云盘服务

这个 Supabase Edge Function 用应用凭证换取 123 云盘的短期 access token，并代为浏览目录、同步元数据、签发临时播放链接。它不会接收或保存 123 云盘手机号、密码、access token 或播放链接。

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

1. 在 Dashboard 的 **Edge Functions** 中确认 `family-video-drive` 状态为 `ACTIVE`，并确认 `Verify JWT` 为关闭状态；这是本函数由代码自行校验并返回中文错误的预期配置。
2. 不带 `Authorization` 请求函数应收到 HTTP 401 和“请先登录”，而不是 500。该结果表示函数已启动，且没有绕过登录校验。
3. 在真机使用家庭成员的邮箱密码登录，在“设置”页连接云盘，再到“整理媒体库”创建条目、选择目录、刷新和播放；在 Functions Logs 中确认没有 123 OpenAPI 或数据库错误。

不要使用 `supabase secrets list` 的输出写入文档、日志或工单；日常只需确认 Secret 名称存在即可。

## 运行边界

- 使用 123 OpenAPI 的 `/api/v1/access_token`、`/api/v2/file/list`、`/api/v1/file/download_info`；视频流始终由手机直连 123 云盘。
- 仅同步 App 已创建条目的绑定目录中的直接视频；不递归云盘子目录，也不会自动创建剧集或子剧集。子剧集由 App 明确创建并独立绑定目录。
- 同步采用稳定 file ID upsert，并移除已不在绑定目录中的媒体元数据；不会移动、创建或删除任何云盘文件。
