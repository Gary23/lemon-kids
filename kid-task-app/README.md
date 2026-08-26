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
- 今日任务按上午、下午、晚上展示；无时间任务归入上午，避免遗漏。
- 任务端提供标准 Android 桌面小部件“今日任务卡片”，当前以演示任务验证荣耀平板桌面的识别与尺寸调整。
- 桌面卡片宽度小于 `320dp` 时展示单列任务，达到该宽度时展示双列；卡片由 `onAppWidgetOptionsChanged` 在用户拖动尺寸后刷新。

## 修改后验证

```bash
./gradlew :kid-task-app:assembleDebug
```

涉及模型、认证、积分或数据库时，继续阅读 [`../shared/README.md`](../shared/README.md) 与 [`../supabase/README.md`](../supabase/README.md)。视觉历史参考见 [`../shared/docs/UI-SPEC.md`](../shared/docs/UI-SPEC.md)。
