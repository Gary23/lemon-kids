# `:family-video-app`：家庭动画

面向家庭儿童的私有动画媒体库 Android App。采用项目现有 Kotlin/Compose、Hilt 与 Supabase 技术栈；登录直接复用家长端相同的邮箱和密码，不存储 123 云盘密码。

## 当前范围

- 家长端 Supabase 邮箱密码会话复用；
- 首页按分类展示家庭媒体库，支持继续观看；
- 我的页提供云盘连接、选择同步目录、立即同步与分类入口；
- 剧集详情、媒体选集和 Media3 在线播放器基础界面；
- `CloudDriveProvider` 作为云盘适配边界，123 云盘 OAuth/临时播放 URL 只能由该层和受保护的服务端完成；
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
- 123 云盘 OAuth token 与临时播放 URL 不写入 Supabase；生产环境通过云函数交换和刷新。
- 本模块默认不提供 123 云盘 Mock 凭证。未配置授权服务时，同步页会明确提示配置状态。

## 验证

```bash
./gradlew :family-video-app:assembleDebug
```
