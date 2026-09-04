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
- 任务完成、撤销和奖励兑换依赖 `:shared` Repository/RPC；页面只负责展示状态与触发 ViewModel。
- 有截止时间的待完成任务会在孩子端以本地通知提醒；首次启动需取得通知权限。
- `MainActivity` 强制竖屏；首页仅展示当日任务，未完成与过期任务按家长端配置的任务分类及其创建顺序分组；已完成任务会脱离原分类，统一置于末尾的孩子端专用“已完成”分组。
- 任务完成与撤销先在首页即时反馈，仓库写入成功后会主动刷新任务观察流；失败时恢复原状态。
- 应用从后台恢复时会刷新 Supabase 会话并立即重拉任务；网络或会话暂不可用时最多八秒结束加载态，不会持续显示加载动画。
- 任务端提供标准 Android 桌面小部件“今日任务卡片”：作为首页当日任务的只读镜像，不展示问候头部，按相同分类展示全部当日任务及待完成、已完成、已过期状态；已完成任务同样固定归入末尾的“已完成”分组。设备解锁及日期、时间、时区变化时会刷新，并在后台进程重建时使用当天最近一次成功同步的快照兜底。点按小部件任意位置只会进入任务端首页，不会在桌面修改任务。
- 启动图标使用 `drawable-nodpi/ic_launcher_foreground_lemon.png` 的柠檬吉祥物前景，配合自适应图标浅绿色底色。

## 修改后验证

```bash
./gradlew :kid-task-app:assembleDebug
```

涉及模型、认证、积分或数据库时，继续阅读 [`../shared/README.md`](../shared/README.md) 与 [`../supabase/README.md`](../supabase/README.md)。视觉历史参考见 [`../shared/docs/UI-SPEC.md`](../shared/docs/UI-SPEC.md)。
