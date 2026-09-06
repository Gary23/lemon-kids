# 家庭动画 App 开发与运维说明

本文档是 `:family-video-app` 的后续开发入口。它描述当前已实现的边界与部署流程；不记录任何密码、123 应用密钥、Supabase service-role key、用户 access token 或临时播放 URL。

## 1. 当前产品边界

- 使用与家长端相同的 Supabase 邮箱密码登录；登录用户必须已关联 `users.family_id`。
- 家庭内登录成员不区分家长、孩子权限，访问同一个家庭媒体库。
- 用户在 App 里选择一个 123 云盘同步根目录；根目录的首层子目录会成为剧集/电影，目录下的视频文件会被递归同步为选集。
- 同步仅保存目录与视频元数据。媒体文件始终保留在 123 云盘，播放时手机直连临时播放地址。
- 云盘中已不存在的首层目录只会标记为 `unavailable`，不会删除 App 分类和播放记录。

首版已具备登录、首页、剧集详情、目录选择、手动同步和基础 Media3 播放。分类的完整增删改排序、可靠的播放进度上报、自动下一集及播放链接失效后的续播仍是后续功能。

## 2. 架构与代码地图

请求路径为：

`Compose 界面 → ViewModel → Repository / CloudDriveProvider → Supabase（PostgREST 或 Edge Function）→ 123 OpenAPI`

| 责任 | 位置 | 说明 |
| --- | --- | --- |
| App 初始化与入口 Activity | `FamilyVideoApp.kt`、`MainActivity.kt` | Hilt 与 Compose 根节点。 |
| 登录与路由 | `feature/auth/VideoLoginScreen.kt`、`navigation/FamilyVideoNavGraph.kt` | 复用 `shared` 中的 Supabase 会话。 |
| 媒体库读取、播放记录 | `data/FamilyVideoRepository.kt` 中的 `SupabaseFamilyVideoRepository` | 直接通过受 RLS 保护的 PostgREST 表访问。 |
| 123 云盘调用 | `SupabaseEdgeCloudDriveProvider` | 只发送 Supabase access token 和业务参数，绝不接触 123 凭据。 |
| 首页、详情、播放器、我的 | `feature/home/`、`feature/library/`、`feature/player/`、`feature/profile/` | ViewModel 保持 UI 状态与错误提示。 |
| 依赖注入 | `di/FamilyVideoModule.kt` | `CloudDriveProvider` 的实现绑定点。 |
| 服务端云盘边界 | `supabase/functions/family-video-drive/index.ts` | 唯一允许使用 123 应用密钥的代码。 |

当前 Android 端 Edge Function 地址在 `FamilyVideoRepository.kt` 中固定为现有 Supabase 项目。迁移 Supabase 项目时，必须同时更新此地址、匿名发布密钥来源及对应环境的数据库迁移；不得把 service-role key 放入 APK。

## 3. 数据库

按以下顺序在目标 Supabase 项目的 SQL Editor 审查、执行：

1. `supabase/sql/20260906_family_video_library.sql`：创建媒体库表、索引与初版 RLS。
2. `supabase/sql/20260906_family_video_all_family_access.sql`：将初版“仅家长”策略替换为“同家庭全部登录成员均可访问”。本 App 当前必须执行此脚本。

| 表 | 用途 | 关键关系 |
| --- | --- | --- |
| `video_drive_connections` | 每个家庭的一条 123 云盘连接和同步根目录 | `family_id` 唯一。 |
| `video_categories` | 家庭自定义/内置分类 | 归属 `family_id`。 |
| `video_collections` | 根目录下的剧集或电影 | `(family_id, drive_folder_id)` 唯一。 |
| `video_media` | 剧集下的视频文件 | `(collection_id, drive_file_id)` 唯一。 |
| `video_playback_records` | 每个视频的播放进度 | `media_id` 为主键。 |
| `video_sync_logs` | 每次同步的计数与错误摘要 | 归属 `family_id`。 |

新增字段、表、RPC 或 RLS 时，要新增带日期的 SQL 文件、写明前置条件及验证查询，并同步更新 `supabase/README.md` 的脚本地图。不要修改已用于生产环境的历史 SQL 来代替新增迁移。

## 4. Edge Function 与 123 云盘

函数名称为 `family-video-drive`，部署说明见 [函数 README](../../supabase/functions/family-video-drive/README.md)。函数接受 JSON `action`：

| action | 作用 |
| --- | --- |
| `connection_status` | 获取当前家庭的连接和同步根目录状态。 |
| `connect` | 使用服务端应用凭据获取短期 123 token，并记录已连接状态。 |
| `browse` | 浏览指定目录。 |
| `select_root` | 保存同步根目录。 |
| `sync` | 扫描根目录、upsert 剧集和视频元数据、标记失效剧集。 |
| `playback_url` | 按文件 ID 获取短期播放 URL，响应不持久化。 |

函数要求请求带 `Authorization: Bearer <Supabase access token>`。虽然部署时使用 `--no-verify-jwt`，`authenticatedFamily()` 仍会验证登录用户，并从 `users` 表确认该用户有家庭归属。不要把该函数改成接受云盘账号密码、客户端上传 access token 或返回应用密钥。

### Secret 与部署

仅在 Supabase Dashboard → Edge Functions → Secrets 中配置：

```text
PAN123_CLIENT_ID=<123 开放平台 Client ID>
PAN123_CLIENT_SECRET=<123 开放平台 Client Secret>
```

部署命令如下；将项目 Reference ID 换成目标项目：

```bash
supabase functions deploy family-video-drive --project-ref <project-ref> --no-verify-jwt
```

`SUPABASE_URL` 与 `SUPABASE_SERVICE_ROLE_KEY` 由函数运行时使用。它们和以上两个 Secret 均不可写进 Git、APK、SQL、日志或问题单。

## 5. 本地开发与验证

构建 APK：

```bash
./gradlew --no-daemon :family-video-app:assembleDebug
```

建议的真机验收顺序：

1. 使用已加入家庭的家长端邮箱密码登录。
2. 打开“我的”，连接云盘并选择一个含剧集子目录的根目录。
3. 点击立即同步，确认首页出现剧集，详情页能列出视频。
4. 打开一个视频，确认 Media3 能获取临时地址并开始播放。
5. 在 Supabase Functions Logs 检查同步和播放请求没有 5xx；在数据库中检查同步记录和媒体元数据是否符合预期。
6. 在同一家庭的另一账号登录，确认能看见同一媒体库；这验证第二个 RLS 脚本已生效。

常见定位方式：401“请先登录”通常是客户端会话过期；403“此账号尚未加入家庭”表示 `users.family_id` 缺失；502“123 云盘授权失败”优先检查 Dashboard 中的 Secret 与 OpenAPI 权限；同步结果异常则在函数日志中检查 123 接口响应，而不是在客户端打印 token。

## 6. 提交与发布检查

- 代码变更至少运行 `:family-video-app:assembleDebug`。
- 变更云盘函数后重新部署，并用未登录请求确认返回 401，而非 500。
- 变更 RLS 后，分别以同家庭成员、其他家庭成员和未登录状态测试读写边界。
- 提交前运行 `git status`，不要提交 `.codex/`、`.qoder/`、本地 Supabase 临时目录、密钥文件或构建产物。
- 合并到 `product` 前，更新 `BRANCHES.md` 的状态和本文件的“当前产品边界/待办”。
