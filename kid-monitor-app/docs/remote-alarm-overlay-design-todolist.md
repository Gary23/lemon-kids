# 监控端远程闹钟全局弹出层设计与 ToDo List

## 目标

当远程闹钟到点时，无论 Pad 处于锁屏、息屏还是正在使用其他应用，孩子都应能看到同一套“闹钟响铃”内容（标题、说明、关闭操作），并保持声音和振动。设计目标是提高可见性，不试图绕过 Android 锁屏、安全页或系统级界面的安全限制。

本方案只调整监控端的**展示层**；远程下发、Room 持久化、`AlarmManager.setAlarmClock` 准点触发及铃声服务保持现有架构。

## 结论

不能用一种系统窗口类型可靠覆盖所有状态；可以使用一套闹钟内容和关闭逻辑，并按设备状态选择展示通道：

| 设备状态 | 主展示通道 | 目的 | 兜底 |
| --- | --- | --- | --- |
| 锁屏或息屏 | 全屏通知（Full-screen intent）打开 `AlarmActivity` | 点亮屏幕并允许在锁屏界面查看、关闭闹钟 | 高优先级闹钟通知；铃声和振动持续 |
| 已解锁，`AlarmActivity` 已在前台 | 保留 `AlarmActivity` | 不产生重复页面或闪烁 | 无 |
| 已解锁，Activity 未被系统拉起或离开前台 | 全局覆盖层 | 覆盖普通第三方应用，提供关闭入口 | 高优先级通知及铃声/振动 |
| 重新锁屏 | 移除全局覆盖层，回归全屏通知 / `AlarmActivity` | 不依赖悬浮窗盖住锁屏 | 高优先级闹钟通知 |

`AlarmActivity` 是锁屏场景的正式方案。`TYPE_APPLICATION_OVERLAY` 仅作为解锁态增强：它不能可靠覆盖锁屏、来电、系统授权、安全页或所有厂商系统 UI。

## 现有基础与约束

- `AlarmReceiver` 会由已登记的精确闹钟冷启动 `AlarmRingService`。
- `AlarmRingService` 已是媒体播放前台服务，负责铃声、振动、唤醒锁和全屏闹钟通知。
- `AlarmActivity` 已配置 `showWhenLocked` 与 `turnScreenOn`，可展示锁屏闹钟页。
- Manifest 已声明 `SYSTEM_ALERT_WINDOW`、`USE_FULL_SCREEN_INTENT`、通知、精确闹钟和无障碍服务权限。
- 现有 `AppLimitAccessibilityService` 已能创建 `TYPE_ACCESSIBILITY_OVERLAY` 的全屏 View；现有普通浮窗使用 `TYPE_APPLICATION_OVERLAY`。
- Android 10+ 限制后台直接启动 Activity；Android 14+ 还可能收回全屏通知资格。因此不能用 `startActivity()` 取代全屏通知，也不能承诺 Activity 在解锁态必然弹出。

## 展示架构

```text
AlarmManager 到点
  -> AlarmReceiver
  -> AlarmRingService（前台服务：声音、振动、通知、状态机）
       |
       +-> 始终发布全屏闹钟通知
       |      -> 系统允许时展示 AlarmActivity
       |
       +-> AlarmPresentationCoordinator
              |
              +-> 锁屏：不创建覆盖层
              |
              +-> 已解锁且 AlarmActivity 不可见：创建 AlarmOverlayController
              |      -> TYPE_APPLICATION_OVERLAY 全屏、可点击、不透传
              |
              +-> 已开启无障碍时：可选 TYPE_ACCESSIBILITY_OVERLAY 实现
                     （仅作为已获用户授权的增强通道，不能借此规避锁屏限制）
```

### 统一内容与操作

提取 `AlarmPresentation` 数据模型，至少包含 `alarmId`、`revision`、`title`、`message`、`requiresConfirmation` 和关闭回调。`AlarmActivity` 与覆盖层使用同一份文案、视觉规范和关闭规则：

- 点击“关闭闹钟”调用 `AlarmRingService.stopIntent()`；由服务统一停止媒体、振动、覆盖层并上报关闭回执。
- 闹钟自动超时、服务销毁、收到更高 `revision` 的取消、或用户重新锁屏时，必须移除覆盖层。
- 覆盖层设置为全屏、可获得自身触摸事件且不向下透传；不提供悬浮拖动、最小化或绕过关闭流程的入口。

UI 可以先复用 `AlarmActivity` 的视觉设计；覆盖层由传统 Android View 加到 `WindowManager`，避免在 Service 内直接管理 Compose 生命周期。后续若需 Compose，应封装具备 `ViewTreeLifecycleOwner`、`ViewTreeViewModelStoreOwner` 与 `SavedStateRegistryOwner` 的专用宿主。

## 状态检测与切换

`AlarmRingService` 在本次响铃生命周期内动态监听状态，并由 `AlarmPresentationCoordinator` 串行处理，避免重复添加或遗漏移除窗口。

| 事件 / 判断 | 处理 |
| --- | --- |
| 闹钟开始 | 发布全屏通知；查询 `KeyguardManager.isKeyguardLocked`。锁屏则只等待 `AlarmActivity`，解锁则评估覆盖层。 |
| `ACTION_USER_PRESENT` | 表示用户完成解锁。若闹钟仍在响且 `AlarmActivity` 不在前台，申请展示覆盖层；若 Activity 已前台可见，不切换。 |
| `ACTION_SCREEN_OFF` 或再次锁屏 | 立即移除普通覆盖层，保留全屏通知和响铃服务。 |
| `AlarmActivity.onResume/onPause` | 向协调器报告可见性。Activity 可见时移除覆盖层；离开前台且设备已解锁、闹钟仍响时可补显示覆盖层。 |
| 停止、超时、取消、服务销毁 | 注销动态广播、移除覆盖层、清除对 Activity 的可见性记录。 |

判断锁屏须使用 `KeyguardManager.isKeyguardLocked`；`ACTION_USER_PRESENT` 只用于“已完成解锁”的事件通知，不能单独推断屏幕是否点亮。广播只在响铃前台服务存活期间动态注册，Android 13+ 按非导出接收器要求注册。

## 覆盖层权限与降级

### 普通悬浮窗（一期必做）

在创建前检查 `Settings.canDrawOverlays(context)`：

- 已授权：使用 `TYPE_APPLICATION_OVERLAY` 创建全屏覆盖层。
- 未授权：不跳转设置、不打断响铃，仅记录日志/回执，并继续使用全屏通知、Activity、铃声和振动。
- 家长端根据 Pad 回执显示“未开启悬浮窗增强展示”，但不把该项误报为闹钟下发失败。

覆盖层依赖 `AlarmRingService` 这个已启动的前台服务，减少厂商 ROM 对后台加窗的限制；仍需在小米、华为、OPPO、vivo 等真机验证。

### 无障碍覆盖层（二期可选）

仅当 `AppLimitAccessibilityService.isEnabled()` 为真时，可委托它创建 `TYPE_ACCESSIBILITY_OVERLAY`。它对已解锁的第三方应用覆盖更稳定，也不需要单独悬浮窗授权；但该服务可能被用户关闭，且不应被用于穿透锁屏或规避系统安全限制。

一期不将无障碍作为闹钟可见性的前置条件，以免将闹钟基础能力耦合到应用限时功能。

### 全屏通知（始终保留）

`USE_FULL_SCREEN_INTENT` 与系统“全屏通知”特殊权限仍是锁屏展示的关键。即使覆盖层创建成功也必须保留：它负责锁屏点亮、系统闹钟语义和无权限场景的点击入口。通知、全屏资格、精确闹钟缺失时的既有回执保持不变。

## 生命周期与并发规则

1. 以 `(alarmId, revision)` 标识当前展示会话；旧版本的停止/回调不能移除新版本的覆盖层。
2. 同时响铃时采用显式策略：一期仅展示最新触发闹钟的页面，其他闹钟继续响铃并以通知呈现；关闭当前项后协调器再展示仍在响铃的下一项。该策略必须与通知 ID 和服务实例策略一并梳理，避免现有单例服务互相覆盖。
3. 覆盖层的 `addView`、`removeView` 与可见性切换必须在主线程执行，并捕获 `BadTokenException` / `SecurityException`；失败不影响响铃。
4. 覆盖层不可标记为 `FLAG_NOT_TOUCHABLE`，也不可设置允许触摸下传的行为；仅暴露关闭闹钟所需操作。
5. `AlarmActivity` 与覆盖层均不直接写 Room 或上报回执，所有结束路径仍经过 `AlarmRingService.stopAlarm()`。

## ToDo List

### 一期：普通悬浮覆盖层

- [x] 新建 `alarm/AlarmPresentation` 与 `AlarmPresentationCoordinator`，集中维护响铃会话、锁屏状态、Activity 可见性和覆盖层决策。
- [x] 新建 `alarm/AlarmOverlayController`，以全屏 `TYPE_APPLICATION_OVERLAY` 展示和移除原生 View；实现权限检查、主线程调用和异常降级。
- [x] 抽取 `AlarmActivity` 的标题、说明、按钮文案和关闭规则，供覆盖层复用；保持一期视觉一致。
- [x] 在 `AlarmRingService` 中注册/注销 `ACTION_USER_PRESENT`、`ACTION_SCREEN_OFF`、`ACTION_SCREEN_ON` 动态广播，并把生命周期事件交给协调器。
- [x] 在 `AlarmActivity` 的 `onResume` / `onPause` / `onDestroy` 上报页面可见性；Activity 可见时隐藏覆盖层。
- [x] 在 `AlarmRingService` 的停止、超时、取消和 `onDestroy` 路径中保证移除覆盖层和注销接收器。
- [x] 为“悬浮窗未授权 / 加窗失败”增加监控端日志与家长端可区分的增强展示状态，不能影响既有 `deployed` 回执含义。
- [x] 核对覆盖层窗口 flags：全屏、可点按、禁止下层触摸；窗口未使用 `FLAG_NOT_TOUCHABLE` 或 `FLAG_NOT_FOCUSABLE`，关闭按钮采用统一二次确认规则；系统返回键/状态栏/导航栏仍由系统保留。

### 二期：无障碍增强与多闹钟

- [x] 为 `AppLimitAccessibilityService` 提供独立的闹钟覆盖层 API，避免复用或污染应用限时的提示/阻挡状态。
- [x] 只有无障碍已启用时才选用该通道；服务断连时自动退回普通悬浮窗或通知。
- [x] 明确并实现多闹钟并发的队列、通知 ID、音频与关闭语义：最新触发项显示为主项，各响铃会话独立通知；关闭主项后自动展示下一项，声音/振动在仍有会话时持续。
- [x] 评估 `requiresConfirmation` 为真时的完成交互，覆盖层与 Activity 均采用“首次点击切换为确认文案、第二次点击关闭”的一致规则。

### 验收与发布

- [x] 为协调器的状态转换编写单元测试：锁屏→解锁、Activity 可见/不可见、停止竞态、旧 revision 回调和加窗失败。
- [ ] 真实设备验证：锁屏、息屏、解锁后正在视频/游戏中、横竖屏、分屏、画中画、再次锁屏、通知权限关闭、全屏资格关闭和悬浮窗权限关闭。
- [ ] 在 Android 26、31、33、34+ 以及至少一台小米、华为、OPPO/vivo 设备上验证；记录厂商差异与所需自启动/后台运行授权。
- [ ] 验证精确闹钟、网络中断、进程被回收、重启后恢复等既有闹钟保障没有回归。
- [ ] 完成后执行 `./gradlew :kid-monitor-app:assembleDebug`，并安装真机进行至少一次锁屏和一次解锁态端到端测试。

## 实施与验证记录（2026-09-13）

- 已完成 `:kid-monitor-app:testDebugUnitTest :kid-monitor-app:assembleDebug`；`AlarmPresentationCoordinatorTest` 的 3 个状态转换测试通过。
- 已以 `adb install -r` 覆盖安装到已连接的 HUAWEI BZT3-AL00（Android 10 / API 29）。APK 包信息：`versionName=1.0.0`、`versionCode=1`。
- Debug APK 提供 `DebugAlarmReceiver` 本地触发器，可通过 ADB 在数秒内验证展示链路；它只创建内存调试会话，不访问 Room、AlarmManager 或 Supabase，命令见监控端 README。
- 本地触发器已在该 HUAWEI BZT3-AL00 上实测：先启动监控端解除厂商后台限制后，ADB 广播可启动 `AlarmRingService` 前台服务并发布闹钟通知；同一设备的后台冷启动广播会被限制。
- 设备端完整响铃验证仍待使用真实已下发闹钟执行：`AlarmActivity` 为非导出组件，ADB 不能越过应用内部启动边界直接伪造该端到端场景。锁屏、解锁、横竖屏、分屏/画中画、通知/全屏/悬浮窗权限关闭、进程回收与重启恢复，以及 Android 26、31、33、34+ 和小米/OPPO/vivo 真机矩阵均未验证，保持未勾选。

## 非目标

- 不尝试覆盖来电、系统授权对话框、锁屏安全认证、支付页面或其他受 Android 保护的系统界面。
- 不用悬浮窗强制阻止用户回桌面、下拉系统通知栏或使用系统导航；闹钟基础功能必须保留正常关闭路径。
- 不把远程配置同步改为依赖悬浮窗、无障碍或实时推送；到点触发仍以已落地的 `AlarmManager` 为准。
