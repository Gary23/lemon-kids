# `:kid-task-app`：孩子任务端

## 当前职责

孩子通过任务绑定码进入应用，查看/完成任务、查看日历、兑换奖励和管理个人资料。模块依赖 `:shared`，不应直接实现 Supabase 数据访问。

## AI 定位入口

| 目标 | 入口文件 |
| --- | --- |
| 应用与 Hilt | `KidTaskApp.kt`、`MainActivity.kt` |
| 顶层路由与 Tab | `navigation/KidTaskNavGraph.kt` |
| 任务首页/时间轴 | `feature/home/HomeScreen.kt`、`HomeViewModel.kt` |
| 日历 | `feature/calendar/CalendarScreen.kt`、`CalendarViewModel.kt` |
| 奖励 | `feature/reward/RewardScreen.kt`、`RewardViewModel.kt` |
| 计划/个人资料 | `feature/plan/PlanScreen.kt`、`feature/profile/` |
| 语音朗读 | `util/KidTtsManager.kt`、`di/KidTtsEntryPoint.kt` |
| 到点提醒 | `reminder/TaskReminderScheduler.kt`、`TaskReminderReceiver.kt` |
| 桌面任务卡片 | `widget/TaskWidgetProvider.kt`、`res/xml/task_widget_info.xml` |

## 路由与约束

- 首次会话检查由共享 `AuthViewModel` 完成；未登录进入 `task_binding_code`，成功后进入 `task_main`。
- 任务端固定 4 个 Tab：`home`、`calendar`、`reward`、`profile`；`plan` 是子页面而非 Tab。
- 绑定页必须传递 `type = "task"` 和 Android `ANDROID_ID`。不得将其改为监控端的单设备重绑语义。
- 任务端会在本 App 私有存储中保存已验证的 `task` 绑定码。Supabase 会话无法刷新时，先尝试用该码静默恢复；仍失败则在根层显示不可关闭的恢复弹层，提供“重试刷新”和“使用绑定码登录”。主动切换账号或清除应用数据后必须重新绑定。
- 日历页采用横屏双栏：左侧月历以右上圆点表示状态、右下显示真实得星，右侧按选中日期展示摘要和平铺任务卡；首页任务卡维持独立布局。
- 任务完成和撤销依赖 `:shared` Repository/RPC。奖励页展示现有积分源的真实余额；心愿、兑换申请与记录目前为明确标注的本地演示，申请不会写入服务端、通知家长或扣减真实积分。
- 有截止时间的待完成任务会在孩子端以本地通知提醒；首次启动需取得通知权限。
- `MainActivity` 固定主横屏方向；公共壳层始终使用 184 dp 左侧四入口导航，底部保留儿童摘要。今日任务顶部直接显示进度，不展示时段问候及重复的天数、积分标签；待办/已完成按 1.25:0.75 双列布局，待办及已错过任务使用卡片右端仅显示“打卡”的黄色文字操作按钮，并沿用原确认流程。待办及已错过任务卡在分类标签后显示真实 `+n 积分`，不在卡片底部显示截止时间；已完成卡标题下显示“获得 N 颗星星”。不提供竖屏版或底部导航。首页仅展示当日任务，首次会等待任务和分类均返回后再按家长端配置的创建顺序分组，避免首屏重排；分类颜色与图标按分类稳定标识固定，已完成任务会脱离原分类，统一置于末尾的孩子端专用“已完成”分组。
- 今日任务的“神秘盲盒”在尚无解锁规则时仅展示不可点击的“即将开放”入口，不写入任务或奖励数据。
- 固定侧栏四入口名称为“今日任务”“任务日历”“奖励兑换”“我的成长”。奖励页按 Stitch 展示“我的星星银行”、大心愿、小心愿及演示兑换记录，不提供“投递新愿望”入口。成长页展示真实资料与演示等级、勋章、里程碑，并保留计划、头像、昵称和切换账号入口。
- 任务完成与撤销先在首页即时反馈，仓库写入成功后会主动刷新任务观察流；失败时恢复原状态。
- 应用从后台恢复时会刷新 Supabase 会话并立即重拉任务；网络暂不可用时最多八秒结束加载态，会话无法恢复时由根层登录恢复弹层接管，不会持续显示加载动画。
- 任务端提供标准 Android 桌面小部件“今日任务卡片”：作为首页当日任务的只读镜像，不展示问候头部，按相同分类展示全部当日任务及待完成、已完成、已过期状态；已完成任务同样固定归入末尾的“已完成”分组。设备解锁及日期、时间、时区变化时会刷新，并在后台进程重建时使用当天最近一次成功同步的快照兜底。点按小部件任意位置只会进入任务端首页，不会在桌面修改任务。
- 启动图标使用 `drawable-nodpi/ic_launcher_foreground_lemon.png` 的柠檬吉祥物前景，配合自适应图标浅绿色底色。

## UI 参照顺序

柠檬任务的 UI 修改必须先通过 Stitch MCP 获取目标屏幕的实际截图和 HTML，以 MCP 实际效果为主要依据；MCP 缺失或不明确的部分才参考本地下载的 `ui` 目录。此规则适用于本次及后续修改。具体屏幕和例外须记入对应的 `docs/20260922-kid-task-app-*` 需求与设计文档，不扩展到其他应用或全局规范。

## 修改后验证

```bash
./gradlew :kid-task-app:assembleDebug
```

涉及模型、认证、积分或数据库时，继续阅读 [`../shared/README.md`](../shared/README.md) 与 [`../supabase/README.md`](../supabase/README.md)。视觉历史参考见 [`../shared/docs/UI-SPEC.md`](../shared/docs/UI-SPEC.md)。
