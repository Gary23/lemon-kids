# `:family-video-app`：家庭动画

面向家庭儿童的私有动画媒体库 Android App。采用项目现有 Kotlin/Compose、Hilt 与 Supabase 技术栈；登录直接复用家长端相同的邮箱和密码，不存储 123 云盘密码。

## 当前范围

- 家长端 Supabase 邮箱密码会话复用；
- 首页按分类展示家庭媒体库，支持继续观看；
- 我的页可连接 123 云盘、浏览并选择同步目录、递归同步媒体元数据；
- 剧集详情、媒体选集和 Media3 在线播放器基础界面；
- `CloudDriveProvider` 通过受保护的 Supabase Edge Function 调用 123 OpenAPI；应用凭证、短期 access token 与临时播放 URL 均不持久化；
- `supabase/sql/20260906_family_video_library.sql` 定义数据表和 RLS。

## 开发入口

| 目标 | 入口 |
| --- | --- |
| 应用与认证路由 | `FamilyVideoApp.kt`、`navigation/FamilyVideoNavGraph.kt` |
| 云盘与 Supabase 数据边界 | `data/FamilyVideoRepository.kt` |
| 首页/详情/播放器 | `feature/home/`、`feature/library/`、`feature/player/` |
| 同步和分类管理入口 | `feature/profile/VideoProfileScreen.kt` |

## 约束

- 首版同步仅写入目录和媒体元数据，绝不下载或移动云盘媒体文件。
- 123 云盘 token、账号密码与临时播放 URL 不写入 Supabase；视频流由手机直连 123 云盘。
- 部署 `supabase/functions/family-video-drive/` 并在 Edge Function Secrets 配置 `PAN123_CLIENT_ID`、`PAN123_CLIENT_SECRET` 后，才能使用连接、目录选择、同步与播放。具体步骤见该目录的 README。
- 初版 SQL 的 RLS 仅允许家长；因本 App 不区分角色，已执行初版 SQL 的项目还须执行 `supabase/sql/20260906_family_video_all_family_access.sql`。

## 验证

```bash
./gradlew :family-video-app:assembleDebug
```
