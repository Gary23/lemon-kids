# `:kid-literacy-app`：柠檬认字

## 模块概览

Compose 平板端孩子应用。模块依赖 `:shared`、Hilt 和 Supabase，通过共享绑定码恢复孩子登录态；评测与智能生成均经云函数调用，APK 不保存腾讯或 DeepSeek 长期密钥。

当前业务行为的唯一正式来源是 [学习与数据规则](docs/LEARNING-RULES.md)。页面布局、状态和交互验收见 [UI 规范](docs/UI-SPEC.md)。
词句指定读音的协议、数据规则与运维验收见 [音素资产文档](docs/phonetic-assets/README.md)。

## AI 定位入口

- 启动：`MainActivity.kt` → `LemonLiteracyApp()`。
- 首页分组、单字学习弹层与朗读交互：`LemonLiteracyApp.kt`。
- 首页任务与已认识字加载：`feature/home/LiteracyHomeViewModel.kt`。
- 待认识字全量展示：`feature/pending/PendingCharactersViewModel.kt`。
- 字库状态与加载逻辑：`feature/library/LibraryViewModel.kt`。
- 字库查询接口：`shared` 模块的 `KnownCharacterRepository` / `SupabaseKnownCharacterRepository`。
- 主题与颜色：`ui/theme/LiteracyTheme.kt`。
- 当前学习、点读和数据流转规则：[LEARNING-RULES.md](docs/LEARNING-RULES.md)。
- 详细页面/验收状态：[UI-SPEC.md](docs/UI-SPEC.md)。
- 词句音素资产：[概览](docs/phonetic-assets/README.md)、[设计](docs/phonetic-assets/design.md)、[部署与验收](docs/phonetic-assets/operations.md)。
- 评测接口、凭证与部署：[认字口语评测云函数](../cloud-functions/evaluate-reading/README.md)。
- 预生成点读音频的运行与部署说明：[认字教学音频生成云函数](../cloud-functions/generate-literacy-audio/README.md)。

## 技术边界

字库已通过 `LibraryViewModel` 接入 Repository；认字首页通过 `LiteracyHomeViewModel` 读取真实任务，待认识字排在已认识字之前，点击单字打开统一学习弹层：待认识字练习字、词、句，已认识字复习只练字、词。评测请求由 `ReadingEvaluationViewModel` 调用腾讯 SCF，APK 不保存腾讯长期密钥：主字本地构造 `TEXT_MODE=0` 参考文本，待认识字的词、句和已认识字的词预取服务端音素参数并仅在当日内存中缓存。`RECORD_AUDIO`、拒绝授权态与取消录音逻辑已接入；逐字评测结果解析以腾讯真机返回结构为准。

## 横屏花园界面

应用支持两个横屏方向。首页、我的、待认识、已认识、字库、帮助记录和通过记录使用各自的固定场景背景、独立插画、奶油面板和胶囊按钮。页面按安全区可用宽度调整主要内容：1000dp 及以下保持标准尺寸，更宽时按宽度适度放大，倍率最高 1.35；这是内容与组件的尺寸适配，背景仍铺满窗口，纵向内容仍可滚动。首页和“我的”背景顶部对齐，竖向超出时只裁切底部；首页真实任务卡始终两列，卡内字格随可用宽度换行，标题与真实日期分行，四卡使用独立角饰。读完的字格保留浅绿色背景和绿色边框，不显示对号。首页与我的居中的“认字 / 我的”导航以 342dp 为标准最大宽度，宽屏随倍率增大，固定于屏幕安全区底部，内容可独立滚动；按住字格、按钮及导航项时不显示灰色遮罩。“我的”不显示独立页头，资料区和六个入口直接从顶部安全留白后开始；宽窗口入口两列三行，窄窗口或大字体时改为单列。入口为渐变底、白色外框和彩色内框，右侧是无白色环边的实心圆底箭头；资料卡为奶油渐变、白色外框与暖色内框，底部有花草，头像缩小并在卡内居中。累计识字数仅在当前孩子已有本地字库缓存时显示。

从“我的”进入的待认识、已认识、字库、帮助记录和通过记录页按各自效果图限制标准内容宽度，大屏时连同页头、主要卡片和字格适度放大，窄窗口自适应；五个子页均可正常打开和返回。待认识和已认识页的标题显示实时字数，已认识页提示置顶后重新学习 3 天；帮助记录页按钮靠右。智能添加识字弹窗加宽，允许用空格、换行、顿号和逗号分隔输入汉字，去重后最多 12 字；详细页面规则见 [UI 规范](docs/UI-SPEC.md)和[学习与数据规则](docs/LEARNING-RULES.md)。

页面标题、工具栏和列表随内容纵向滚动；学习及业务弹窗在大屏随可用宽高调整外框和主要内容，表单处理安全区与键盘，关闭按钮独立保持可达。首页单字学习弹层采用云朵、天空、柠檬与花草组合标题，动态展示当前字；字词句练习共用固定槽位，字词行加高且正文加粗放大，点亮星星略大且黄色更深，星位与文字框保留间距，录音、评测、错误及满星切换保持布局。五点仅在开始朗读后于当前行播放蓝色动画；读对不显示冗余提示，读错字逐字标红且不重复显示整句提醒，其他评测故障仍显示原因。字、词滚出视窗再返回保留朗读结果及“重试”状态，不自动重新录音；满星即时移除按钮交互与焦点并保留占位。底部“通过”显示在花草上层并继续进入家长确认。汉字字格、练习主字、词句与 UI 正文统一使用系统默认字体。

字、词、句的一次有效腾讯评测回调只要有标红汉字，认字端便异步调用 `evaluate-reading` 上报一条失败诊断；通过、取消及无有效最终回调不产生该事件。每个词独立上报，带随机 `attemptId`、目标、标红位置、客户端判定原因及腾讯逐项结果；新记录另带经敏感字段剔除和大小限制的腾讯评测 JSON，无法保存时明确记录状态。上报失败不阻塞朗读或改变星级，不上传录音、凭证或登录 Token。云函数核对当前孩子和教学内容后写入 `READING_EVAL_FAILED` 结构化日志；旧记录无法补齐原文，腾讯 `Word` 不是实际语音转写。部署与查询条件见 [`evaluate-reading` 说明](../cloud-functions/evaluate-reading/README.md)。

展示组件位于 `ui/components/`，主题位于 `ui/theme/`，运行时场景与插画位于 `res/drawable-nodpi/garden_*.png`。首页和学习弹层优化沿用真实数据、共享认证及既有学习规则，不新增数据库迁移或云函数部署前提。交付过程见 [花园 UI 重构](../docs/20261013-kid-literacy-app-garden-ui-redesign/执行.md)、[首页布局优化](../docs/20261014-kid-literacy-app-home-layout-scale/执行.md)、[学习弹层对齐](../docs/20261014-kid-literacy-app-study-dialog-ui/执行.md)与[“我的”子页尺寸调整](../docs/20261016-kid-literacy-app-profile-subpages-scale/执行.md)。

## 帮助过的内容

“我的 → 帮助过的内容”可将记录中标红的字加入“已认识的字”：已认识时提示无需重复添加；仅在字库中仍可编辑收录。弹层优先读取当前孩子该字的历史词句，没有完整内容时请求 DeepSeek；成功后该字置顶，只移除被点击的帮助记录。此流程依赖 `evaluate-reading` 的 `check_helped_character`、`preview_helped_character`、`save_helped_character` 接口及 `supabase/sql/20261009_helped_character_recognized_content.sql` 迁移。

## 登录会话恢复

启动或业务请求发现 Supabase 登录凭证不可用时，`SessionRecoveryCoordinator` 通知 `LemonLiteracyApp` 根层显示不可关闭的恢复弹层，覆盖朗读、智能添加识字等所有业务弹层，避免继续提交受保护请求。孩子可先“重试刷新”；仍失败时，可用本机已保存且已验证的 `task` 绑定码静默换取新会话。恢复成功后自动关闭弹层；没有可用绑定码时保留失败提示，需重新进入应用按绑定流程处理。

## 字库数据约定

字库读取当前已登录孩子的 `uid`，查询 `public.known_characters`：

| 字段 | 用途 |
|---|---|
| `user_id` | 孩子的 Supabase Auth UUID |
| `character` | 单个已认识汉字；与 `user_id` 组成唯一键，避免同一孩子重复入库 |
| `learned_at` | 收录时间，查询按 `learned_at`、`character` 升序；客户端更新及缓存加载时会按 `character` 去重 |

进入字库只读取当前孩子的本地缓存；没有缓存时不自动联网。点击“更新字库”才按每批 100 字查询全部数据，去重后覆盖本地缓存，失败保留旧数据并允许重试。搜索仅过滤当前缓存，没有“加载更多”；加载、未缓存、空数据、无搜索结果和更新失败均有真实状态提示。

“导出字库”按完整缓存顺序导出，不受搜索影响，不额外联网。序号从 1 开始且包含首尾，结束必须大于开始；开始越界报错，结束越界截断到最后一个字。汉字以顿号分隔，通过系统文件保存窗口写为 UTF-8 文本。

## 验证

```bash
./gradlew :kid-literacy-app:assembleDebug :kid-literacy-app:lintDebug :kid-literacy-app:testDebugUnitTest
```
