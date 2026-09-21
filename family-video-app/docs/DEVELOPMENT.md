# 柠檬视频 App 开发与运维说明

本文档是 `:family-video-app` 的后续开发入口。它描述当前已实现的边界与部署流程；不记录任何密码、123 应用密钥、Supabase service-role key、用户 access token 或临时播放 URL。

## 1. 当前产品边界

- 使用与家长端相同的 Supabase 邮箱密码登录；登录用户必须已关联 `users.family_id`。
- 家庭内登录成员不区分家长、孩子权限，访问同一个家庭媒体库。
- 用户在 App 内手工创建剧集或电影。新建时先选择目录，App 读取该目录直接子项中约定的 `folder.png`、`folder.jpg` 或 `folder.jpeg` 作为封面并上传至 `video-covers`；子剧集由 `parent_id` 明确关联，可继续嵌套；App 不会在 123 云盘创建目录。
- 每个条目独立刷新，仅读取其绑定目录中的直接视频，不递归扫描子目录；父剧集可有特别篇，子剧集必须独立绑定目录，因此视频不会在父子条目间重复。
- 同步仅保存目录与视频元数据。媒体文件始终保留在 123 云盘，播放时手机直连临时播放地址。
- 封面上传至 `video-covers` bucket，路径按家庭隔离；删除 App 条目不会移动或删除云盘文件。

当前已具备登录、顶层媒体库搜索、剧集详情、媒体条目及目录配置、手动同步和 Media3 播放。播放首次报错时会自动重新签发一次临时链接；可靠的播放进度上报和自动下一集仍是后续功能。

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
2. `supabase/sql/20260906_family_video_all_family_access.sql`：将初版“仅家长”策略替换为“同家庭全部登录成员均可访问”。
3. `supabase/sql/20260919_family_video_explicit_library.sql`：增加 `parent_id`、`media_type`、`drive_folder_path`，并创建 `video-covers` bucket 与策略。
4. `supabase/sql/20260921_family_video_drive_token_cache.sql`：创建只供 `family-video-drive` service-role 使用的短期 123 access token 缓存；执行脚本末尾核验，确认 RLS 已开启且没有客户端策略。

| 表 | 用途 | 关键关系 |
| --- | --- | --- |
| `video_drive_connections` | 每个家庭的一条 123 云盘连接 | `family_id` 唯一；旧同步根目录字段不再由 App 使用。 |
| `video_categories` | 家庭自定义/内置分类 | 归属 `family_id`。 |
| `video_collections` | 手工配置的剧集/电影或子剧集 | `parent_id` 表示层级；一个条目绑定一个云盘目录。 |
| `video_media` | 剧集下的视频文件 | `(collection_id, drive_file_id)` 唯一。 |
| `video_playback_records` | 每个视频的播放进度 | `media_id` 为主键。 |
| `video_sync_logs` | 每次同步的计数与错误摘要 | 归属 `family_id`。 |
| `video_drive_token_cache` | 跨 Edge 冷启动复用的短期 123 应用级 access token | 固定 `provider='123pan'`；RLS 已开启，**没有** `anon` 或 `authenticated` 策略。 |

新增字段、表、RPC 或 RLS 时，要新增带日期的 SQL 文件、写明前置条件及验证查询，并同步更新 `supabase/README.md` 的脚本地图。不要修改已用于生产环境的历史 SQL 来代替新增迁移。

## 4. Edge Function 与 123 云盘

函数名称为 `family-video-drive`，部署说明见 [函数 README](../../supabase/functions/family-video-drive/README.md)。函数接受 JSON `action`：

| action | 作用 |
| --- | --- |
| `connection_status` | 获取当前家庭的连接状态。 |
| `connect` | 使用服务端应用凭据获取短期 123 token，并记录已连接状态。 |
| `browse` | 分页浏览指定目录中的子目录；返回当前页、继续游标和是否还有下一页。 |
| `sync_collection` | 校验当前家庭的指定条目，并同步绑定目录中的直接视频。 |
| `playback_url` | 按文件 ID 获取短期播放 URL，响应不持久化。 |
| `folder_cover` | 按所选目录读取约定封面文件的短期下载链接；客户端须立即上传为长期封面，不能持久化该链接。 |

函数要求请求带 `Authorization: Bearer <Supabase access token>`。虽然部署时使用 `--no-verify-jwt`，`authenticatedFamily()` 仍会验证登录用户，并从 `users` 表确认该用户有家庭归属。不要把该函数改成接受云盘账号密码、客户端上传 access token 或返回应用密钥。

函数先复用本 Edge 实例的有效 token，其次读取服务端缓存表；只有 token 实际到期或收到 123 的认证拒绝时才重新调用授权接口。认证拒绝仅刷新并重试当前读操作一次。目录选择的 `browse` 每次只读取一个 123 列表页，Edge 按“目录 ID + 游标”缓存页面五分钟并合并相同并发读取；Pad 按登录用户缓存成功目录页 24 小时，再次打开优先本地显示，可点“刷新目录”绕过两级目录页缓存。`folder_cover` 的名称搜索必须验证返回文件属于所选目录，不能验证时最多降级读取一页，绝不完整扫描目录；成功定位后函数签发绑定家庭、目录、文件和有效期的归属凭据，Pad 仅复用该凭据，旧版裸文件 ID 缓存不再可信。新建页的自动封面加载会在换目录、关闭编辑或手动上传时取消，并在每个异步阶段核对当前目录，避免慢请求串图。手动同步仍扫描完整绑定目录并始终读取 123 最新结果，且不写入部分目录。网络异常、超时、HTTP 429/5xx 对所有 123 只读接口最多重试一次，间隔 500ms；文件列表单页每次最多等待 25 秒，其余上游读请求最多 20 秒。`connect`、目录浏览、封面和播放地址各有 85 秒服务端总预算，Android 最多等待 95 秒；完整同步为服务端 55 秒、Android 65 秒，到期时保留上次成功视频。短期 token 会以明文存在服务端表中，但不进入 APK、函数响应、日志或客户端可读 RLS 范围。

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
2. 打开“我的”，连接云盘；进入“整理媒体库”，新建剧集并选择云盘目录，确认首批目录可显示、“加载更多目录”会追加后续页、“刷新目录”可重新读取 123；再次进入已读取路径应立即显示本地目录页缓存。随后分别选择两个含 `folder.png`、`folder.jpg` 或 `folder.jpeg` 的目录，确认每次自动预览均来自当前目录；在读取中换目录、关闭重开或手动上传时，旧请求不得覆盖当前封面。
3. 保存并刷新，确认首页出现剧集；在剧集详情中创建子剧集并为其单独绑定目录，确认二者视频不重复。
4. 打开一个视频，确认 Media3 能获取临时地址并开始播放；首次播放错误时确认会自动重新签发一次链接，连续错误显示手动重试。
5. 在 Supabase Functions Logs 检查同步和播放请求没有 5xx；在数据库中检查同步记录和媒体元数据是否符合预期。
6. 在同一家庭的另一账号登录，确认能看见同一媒体库；这验证第二个 RLS 脚本已生效。

常见定位方式：401“请先登录”通常是客户端会话过期；403“此账号尚未加入家庭”表示 `users.family_id` 缺失；“123 云盘授权失败”优先检查 Dashboard 中的 Secret 与 OpenAPI 权限；“目录/封面/播放地址读取超时”表示该动作已在总预算内重试后仍未完成，可稍后重试；目录页没有子目录但显示“加载更多目录”时，继续加载下一页即可；“目录整体同步超时”表示已保留上次成功视频，可稍后手动刷新。Functions Logs 的 123 失败诊断只应包含操作标签、HTTP 状态、123 数值 code（如有）、尝试次数、页数和耗时，绝不能打印 token、请求头、下载链接、文件名或原始响应正文。

“我的”页会将当前登录用户最近一次取得的连接状态缓存 24 小时，避免频繁重复读取状态。缓存仅含连接状态、账号提示与目录元数据，不含云盘密码、123 token、Supabase token 或临时下载 URL；点击“重新连接”会直接刷新服务端状态和本地缓存。

## 6. 提交与发布检查

- 代码变更至少运行 `:family-video-app:assembleDebug`。
- 变更云盘函数后重新部署，并用未登录请求确认返回 401，而非 500。
- 变更 RLS 后，分别以同家庭成员、其他家庭成员和未登录状态测试读写边界。
- 提交前运行 `git status`，不要提交 `.codex/`、`.qoder/`、本地 Supabase 临时目录、密钥文件或构建产物。
- 合并到 `product` 前，更新 `BRANCHES.md` 的状态和本文件的“当前产品边界/待办”。
