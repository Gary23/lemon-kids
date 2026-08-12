# `:parent-app`：家长端

## 当前职责

家长登录/注册并创建家庭，管理孩子、任务、分类、回收站、奖励与设备使用信息，并生成孩子任务端或监控端绑定码。

## AI 定位入口

| 目标 | 入口文件 |
| --- | --- |
| 应用与启动页 | `LemonParentsApp.kt`、`MainActivity.kt` |
| 认证/家庭创建 | `feature/auth/` |
| 路由 | `navigation/ParentNavGraph.kt` |
| 任务和日历 | `feature/tasks/TasksScreen.kt`、`TaskEditScreen.kt`、`CalendarView.kt` |
| 使用监管 | `feature/monitor/MonitorScreen.kt`、`MonitorViewModel.kt` |
| 家庭、分类、回收站、日志 | `feature/profile/` |

## 路由与跨端约束

- 未登录依次使用 `login`、`register`、`create_family` 路由；已登录进入 `main`。
- 主 Tab 为 `tasks`、`monitor`、`profile`。编辑、家庭管理、分类、回收站和设备日志都是子路由。
- 家庭页生成 `task` 或 `monitor` 绑定码，并在进入页面时查询、展示已有的活跃绑定码；协议和重绑规则归 `:shared` 与 Supabase RPC，家长端不得自行生成或保存孩子凭据。
- 任务、积分、分类和监控数据均按 `family_id` 隔离。改查询或写入前检查 RLS 与共享 Repository。

## 修改后验证

```bash
./gradlew :parent-app:assembleDebug
```

涉及绑定码、家庭权限或数据模型时，同时验证孩子任务端/监控端，并阅读 [`../shared/README.md`](../shared/README.md)。
