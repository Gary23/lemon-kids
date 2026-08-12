# `:kid-task-app`：孩子任务端

## 当前职责

孩子通过任务绑定码进入应用，查看/完成任务、查看日历、兑换奖励和管理个人资料。模块依赖 `:shared`，不应直接实现 Supabase 数据访问。

## AI 定位入口

| 目标 | 入口文件 |
| --- | --- |
| 应用与 Hilt | `KidTaskApp.kt`、`MainActivity.kt` |
| 顶层路由与 Tab | `navigation/KidTaskNavGraph.kt` |
| 任务首页 | `feature/home/HomeScreen.kt`、`HomeViewModel.kt` |
| 日历 | `feature/calendar/CalendarScreen.kt`、`CalendarViewModel.kt` |
| 奖励 | `feature/reward/RewardScreen.kt`、`RewardViewModel.kt` |
| 计划/个人资料 | `feature/plan/PlanScreen.kt`、`feature/profile/` |
| 语音朗读 | `util/KidTtsManager.kt`、`di/KidTtsEntryPoint.kt` |

## 路由与约束

- 首次会话检查由共享 `AuthViewModel` 完成；未登录进入 `task_binding_code`，成功后进入 `task_main`。
- 任务端固定 4 个 Tab：`home`、`calendar`、`reward`、`profile`；`plan` 是子页面而非 Tab。
- 绑定页必须传递 `type = "task"` 和 Android `ANDROID_ID`。不得将其改为监控端的单设备重绑语义。
- 任务完成、撤销和奖励兑换依赖 `:shared` Repository/RPC；页面只负责展示状态与触发 ViewModel。

## 修改后验证

```bash
./gradlew :kid-task-app:assembleDebug
```

涉及模型、认证、积分或数据库时，继续阅读 [`../shared/README.md`](../shared/README.md) 与 [`../supabase/README.md`](../supabase/README.md)。视觉历史参考见 [`../shared/docs/UI-SPEC.md`](../shared/docs/UI-SPEC.md)。

