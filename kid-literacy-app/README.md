# `:kid-literacy-app`：柠檬认字

## 模块概览

Compose 平板端孩子应用。模块依赖 `:shared`、Hilt 和 Supabase，通过共享绑定码恢复孩子登录态；评测与智能生成均经云函数调用，APK 不保存腾讯或 DeepSeek 长期密钥。

当前业务行为的唯一正式来源是 [学习与数据规则](docs/LEARNING-RULES.md)。页面布局、状态和交互验收见 [UI 规范](docs/UI-SPEC.md)。

## AI 定位入口

- 启动：`MainActivity.kt` → `LemonLiteracyApp()`。
- 首页分组与学习页页面切换：`LemonLiteracyApp.kt`。
- 首页任务与已认识字加载：`feature/home/LiteracyHomeViewModel.kt`。
- 待认识字全量展示：`feature/pending/PendingCharactersViewModel.kt`。
- 字库状态与加载逻辑：`feature/library/LibraryViewModel.kt`。
- 字库查询接口：`shared` 模块的 `KnownCharacterRepository` / `SupabaseKnownCharacterRepository`。
- 主题与颜色：`ui/theme/LiteracyTheme.kt`。
- 当前学习、点读和数据流转规则：[LEARNING-RULES.md](docs/LEARNING-RULES.md)。
- 详细页面/验收状态：[UI-SPEC.md](docs/UI-SPEC.md)。
- 评测接口、凭证与部署：[认字口语评测云函数](../cloud-functions/evaluate-reading/README.md)。
- 预生成点读音频的运行与部署说明：[认字教学音频生成云函数](../cloud-functions/generate-literacy-audio/README.md)。

## 技术边界

字库已通过 `LibraryViewModel` 接入 Repository；认字首页通过 `LiteracyHomeViewModel` 读取真实任务。评测请求由 `ReadingEvaluationViewModel` 调用腾讯 SCF，APK 不保存腾讯长期密钥。`RECORD_AUDIO`、拒绝授权态与取消录音逻辑已接入；逐字评测结果解析以腾讯真机返回结构为准。

## 登录会话恢复

启动或业务请求发现 Supabase 登录凭证不可用时，`SessionRecoveryCoordinator` 通知 `LemonLiteracyApp` 根层显示不可关闭的恢复弹层，覆盖朗读、智能添加识字等所有业务弹层，避免继续提交受保护请求。孩子可先“重试刷新”；仍失败时，可用本机已保存且已验证的 `task` 绑定码静默换取新会话。恢复成功后自动关闭弹层；没有可用绑定码时保留失败提示，需重新进入应用按绑定流程处理。

## 字库数据约定

字库读取当前已登录孩子的 `uid`，查询 `public.known_characters`：

| 字段 | 用途 |
|---|---|
| `user_id` | 孩子的 Supabase Auth UUID |
| `character` | 单个已认识汉字；与 `user_id` 组成唯一键，避免同一孩子重复入库 |
| `learned_at` | 收录时间，字库按 `learned_at`、`character` 升序稳定分页展示；客户端刷新及缓存加载时会按 `character` 去重 |

页面具备加载中、空数据、错误重试、汉字搜索和无分类分页展示。首次加载 100 个字，点击“加载更多”继续获取下一页。

## 验证

```bash
./gradlew :kid-literacy-app:assembleDebug
```
