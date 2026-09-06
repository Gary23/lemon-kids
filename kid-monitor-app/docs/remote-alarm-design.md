# 柠檬闹钟管家：远程闹钟设计

## 目标与边界

家长在家长端为**已绑定的一台监控 Pad**建立、修改或取消闹钟；Pad 收到并确认后，即使应用进程被系统回收、设备处于 Doze 或长期未打开 App，也要在目标时刻发出声音、振动并在锁屏上展示全屏提醒。

本设计只面向 Android Pad。iPadOS 不能用同等方式获得后台精确到点和锁屏全屏响铃能力，需单独设计 iOS 降级路径。

监控端继续承担设备受控与状态上报职责，不把 `LimitEnforcementService` 误用为闹钟计时器。相关既有约束见 [监控端 README](../README.md)：它已有开机广播、前台服务、全屏提醒权限和一设备一 `monitor` 绑定语义，适合作为闹钟执行端。

## 本次架构决策

```text
家长端保存闹钟 ──> Supabase alarms / alarm_deliveries
                           │ 启动、解锁、网络恢复与 15 分钟对账
                           ▼
             监控 Pad：RemoteAlarmApplier
                    │ 先持久化，再登记
        Room device_alarms + 设备保护存储最小恢复记录
                    │
       AlarmManager.setAlarmClock（不依赖常驻进程）
                    │
     静态 AlarmReceiver 冷启动 -> AlarmRingService（媒体播放 FGS）
                    │                          │
     声音 / 振动 / CPU 唤醒锁              高优先级全屏通知
                                               │
                                        AlarmActivity 锁屏页
                                               │
                              alarm_events 回执：响铃 / 关闭 / 异常
```

本方案使用 Android 的 `AlarmManager.setAlarmClock`，即把“触发”交由系统的闹钟通道执行，而非借助系统时钟 App 的 UI 或数据库。它会显示系统下一次闹钟、按时唤醒设备，并比应用常驻后台或 `WorkManager` 更适合准点提醒。

不采用系统时钟 App 作为执行主体：它无法保证接收本产品的远程修改、条件、关闭确认和回执。也不采用纯常驻服务轮询：厂商回收进程或 Doze 会使轮询不可靠。

## 已在本分支落地的监控端基础

- App 名改为 **柠檬闹钟管家**，并替换为柠檬闹钟图标。
- `device_alarms` Room 表保存已下发快照与版本号；`RemoteAlarmApplier` 是所有云端下发的唯一入口，保证“先落库、再登记”及版本幂等。
- `AlarmScheduler` 使用 `setAlarmClock` 登记精确闹钟；Android 12+ 未获“闹钟与提醒”特殊权限时明确返回失败，不以不精确闹钟伪装成功。
- 静态 `AlarmReceiver` 能在被系统回收后冷启动。闹钟响铃服务使用 `mediaPlayback` 前台服务、`USAGE_ALARM` 音频焦点、系统闹铃音、振动和最多十分钟的 CPU 唤醒锁。
- 全屏通知打开 `AlarmActivity`，该页在锁屏时点亮屏幕；若系统/用户收回全屏通知资格，通知仍可点击进入，不会静默失败。
- 已登记闹钟的最小元数据写入 device-protected storage。`BootReceiver` 在开机、解锁或包替换时重新登记，避免普通 App 重启后 AlarmManager 项丢失。

## 云端与家长端待接入合同

### 数据表

| 表 | 关键字段 | 用途 |
| --- | --- | --- |
| `alarms` | `id`, `family_id`, `child_id`, `target_device_id`, `revision`, `trigger_at`, `timezone`, `title`, `message`, `enabled`, `requires_confirmation`, `updated_at` | 家长端的逻辑闹钟；取消为 `enabled=false` 的新版本。 |
| `alarm_deliveries` | `alarm_id`, `device_id`, `revision`, `status`, `ack_at`, `error_code`, `updated_at` | Pad 对“已部署 / 权限缺失 / 已响铃 / 已关闭”的当前确认。 |
| `alarm_events` | `id`, `alarm_id`, `device_id`, `revision`, `event_type`, `occurred_at`, `detail` | 不可变审计：下发、触发、全屏失败、关闭、错过和恢复。 |

`target_device_id` 必须是 `monitor` 绑定产生的设备标识，不能使用可多端复用的 `task` 绑定码。RLS 规则应确保家长仅操作自己家庭中孩子的闹钟；监控 Pad 仅能读写自身 `device_id` 的投递与事件。

### 下发与一致性

1. 家长端写入闹钟并递增 `revision`；数据库触发器原子创建或重置对应 `alarm_deliveries` 为 `pending`。
2. Pad 在应用启动、正常开机/用户解锁后立即对账，且由有网络约束的 `AlarmSyncWorker` 每 15 分钟兜底调用 `RemoteAlarmApplier.apply(snapshot)`。项目现有数据层以轮询为正式路径，Realtime 即使在控制台开启也只能作为将来的加速通道，不能作为唯一投递手段。
3. `apply` 先将快照写入 Room，再登记 OS 闹钟；相同/更旧版本直接忽略。取消会先持久化禁用状态，再取消 PendingIntent。
4. 成功登记后写 `deployed` 回执；缺少精确闹钟、通知或全屏资格则写准确错误码，家长端展示“需在 Pad 授权”。
5. 到点、展示全屏、用户关闭和自动超时分别追加 `alarm_events`，并更新投递状态。

未来若接入推送，只承担“尽快同步”和网络恢复唤醒；到点执行始终以 Pad 已落地的 `AlarmManager` 项为准。这样网络在响铃时中断不影响已确认的闹钟。

## 权限与可靠性策略

| 情况 | 处理 | 家长端状态 |
| --- | --- | --- |
| 系统杀掉 App 后台进程 | 静态 Receiver 由 AlarmManager 冷启动，闹钟不依赖常驻进程。 | 不降级。 |
| Doze / 待机 | `setAlarmClock` 走系统闹钟通道。 | 不降级。 |
| 开机或 App 更新 | device-protected storage + BootReceiver 立即重建。 | 恢复中 / 已部署。 |
| Android 12+ 未授予精确闹钟 | 不登记，提示 Pad 到系统“闹钟与提醒”授权。 | `exact_alarm_denied`。 |
| Android 13+ 通知未允许 | 服务仍尝试响铃，但锁屏展示不可承诺。 | `notification_denied`。 |
| Android 14+ 全屏资格关闭 | 声音与高优先级通知可用，改为点击打开。 | `full_screen_denied`。 |
| 关机、没电、飞行模式 | 无法在目标时刻执行；开机后应记录 `missed`，按产品策略补提醒或不补。 | `missed_offline`。 |
| 用户强行停止、卸载、禁用 | Android 会冻结/取消 App 任务，任何 App 方案都不能绕过。产品假设不允许此行为。 | 离线 / 未确认。 |

厂商电池优化可能影响同步和开机后的即时对账，但不会取消已经由 AlarmManager 登记的闹钟。监控端已有 `KeepAliveWorker`、开机广播和设备状态心跳；闹钟模块只借用它们做同步/诊断，不依赖它们准点触发。

## 闹钟动作和关闭策略

一期默认：响铃、振动、锁屏全屏页、手动确认关闭，最长响十分钟。家长端配置的 `requires_confirmation` 已预留，二期可扩展为答题、朗读、拍照或家长远程确认；扩展必须由 `AlarmActivity` 的完成态驱动，不能让通知划掉即视为完成。

如需“响铃后限制娱乐 App”，可在 `AlarmActivity` 确认前调用现有 `AppLimitEvaluator`/无障碍拦截能力，但这是独立产品规则，不能影响音频播放和基础关闭通路。

## 发布前真机验收

至少在 API 26、31、33、34+ 各一台真实 Pad 验证：精确权限未授予/授予、熄屏、锁屏、Doze、系统回收进程、重启后未解锁与解锁后、通知关闭、全屏资格关闭、耳机连接、静音模式、来电/其他音频、网络断开、修改/取消竞态及同一闹钟重放。构建成功不等于可靠闹钟可用。
