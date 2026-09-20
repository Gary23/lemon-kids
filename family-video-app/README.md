# `:family-video-app`：柠檬视频

面向家庭儿童的私有动画媒体库 Android App。采用项目现有 Kotlin/Compose、Hilt 与 Supabase 技术栈；登录直接复用家长端相同的邮箱和密码，不存储 123 云盘密码。

供后续开发与运维使用的完整说明见 [开发维护文档](docs/DEVELOPMENT.md)。其中包含数据模型、服务端接口、部署顺序、调试方法和发布检查项。

## 当前范围

- 家长端 Supabase 邮箱密码会话复用；
- 首页提供仅搜索顶层剧集/电影名称的全库搜索与继续观看；
- 在 App 内手工创建剧集或电影（名称、类型、封面、云盘目录），支持无限层级子剧集；
- 每个条目独立刷新其绑定目录的直接视频，绝不递归自动发现或创建云盘目录；
- 剧集详情、显式子剧集、选集和 Media3 在线播放器；
- `CloudDriveProvider` 通过受保护的 Supabase Edge Function 调用 123 OpenAPI；应用凭证、短期 access token 与临时播放 URL 均不持久化；
- `supabase/sql/20260919_family_video_explicit_library.sql` 定义显式父子媒体库迁移与封面存储策略。

## 开发入口

| 目标 | 入口 |
| --- | --- |
| 应用与认证路由 | `FamilyVideoApp.kt`、`navigation/FamilyVideoNavGraph.kt` |
| 云盘与 Supabase 数据边界 | `data/FamilyVideoRepository.kt` |
| 首页/详情/播放器 | `feature/home/`、`feature/library/`、`feature/player/` |
| 媒体库配置入口 | `feature/profile/MediaLibraryManageScreen.kt` |
| 长期开发/运维说明 | `docs/DEVELOPMENT.md` |

## 约束

- 首版同步仅写入目录和媒体元数据，绝不下载或移动云盘媒体文件。
- 123 云盘 token、账号密码与临时播放 URL 不写入 Supabase；视频流由手机直连 123 云盘。
- 部署 `supabase/functions/family-video-drive/` 并在 Edge Function Secrets 配置 `PAN123_CLIENT_ID`、`PAN123_CLIENT_SECRET` 后，才能使用连接、目录选择、同步与播放。具体步骤见该目录的 README。
- 初版 SQL 的 RLS 仅允许家长；因本 App 不区分角色，已执行初版 SQL 的项目还须执行 `supabase/sql/20260906_family_video_all_family_access.sql`。

## 验证

```bash
./gradlew :family-video-app:assembleDebug
```
