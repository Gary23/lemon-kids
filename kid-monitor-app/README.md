# `:kid-monitor-app`：孩子设备监控端

## 当前职责

采集设备使用情况、执行应用限时、显示使用详情和设备资料。它依赖 `:shared`，并使用 Android 的使用情况统计、前台服务、悬浮窗和无障碍能力。

## AI 定位入口

| 目标 | 入口文件 |
| --- | --- |
| 启动调度 | `KidMonitorApp.kt` |
| 路由 | `navigation/KidMonitorNavGraph.kt` |
| 监控执行 | `monitor/LimitEnforcementService.kt`、`AppLimitAccessibilityService.kt`、`AppLimitEvaluator.kt` |
| 数据采集/保活 | `monitor/UsageCollectWorker.kt`、`KeepAliveWorker.kt`、`DeviceStatusWorker.kt`、`BootReceiver.kt` |
| 悬浮与拦截 UI | `UsageFloatingService.kt`、`LimitBlockActivity.kt` |
| 使用详情 | `feature/usage/` |

## 高风险约束

1. `KidMonitorApp.onCreate` 会调度 Worker、上报启动状态并启动限制服务；改动启动逻辑需避免重复调度和 Android 后台启动限制。
2. Manifest 中的 `PACKAGE_USAGE_STATS`、悬浮窗、前台服务、无障碍服务及开机广播均为功能前提。删除/收紧权限前，须验证真机授权流程。
3. 绑定页使用 `type = "monitor"`；同一码通常只绑定一台设备，重绑经共享 `BindingCodeScreen` 的确认路径处理。
4. 不能以普通 UI 状态替代限时拦截；限制决策必须经过 `AppLimitEvaluator`，并评估服务、无障碍和拦截页的协作。

## 修改后验证

```bash
./gradlew :kid-monitor-app:assembleDebug
```

构建成功不等于监控可用。涉及权限、服务、Worker 或拦截行为时，必须在 API 26+ 真机验证授权、重启恢复和限时命中。

