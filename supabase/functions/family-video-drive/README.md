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

## 运行边界

- 使用 123 OpenAPI 的 `/api/v1/access_token`、`/api/v2/file/list`、`/api/v1/file/download_info`；视频流始终由手机直连 123 云盘。
- 首层目录同步为剧集/电影，子目录递归读取常见视频格式；采用稳定 file ID upsert 与自然排序。
- 云盘目录消失时保留 App 分类和播放记录，仅将剧集标记为 `unavailable`。
