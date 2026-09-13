package com.lemonkids.kidmonitor.alarm

import java.lang.ref.WeakReference

/** 由不同窗口实现的覆盖层宿主；Coordinator 保持为纯状态机以便单元测试。 */
interface AlarmOverlayHost {
    fun show(presentation: AlarmPresentation, onDismiss: () -> Unit): AlarmOverlayResult
    fun remove()
}

sealed interface AlarmOverlayResult {
    data object Shown : AlarmOverlayResult
    data object PermissionMissing : AlarmOverlayResult
    data class Failed(val cause: Throwable) : AlarmOverlayResult
}

/**
 * 单次响铃生命周期的展示状态机。所有调用均须来自主线程；服务负责把广播和
 * 协程回调切换到主线程，确保 add/removeView 不会竞争。
 */
class AlarmPresentationCoordinator(
    private val isKeyguardLocked: () -> Boolean,
    private val overlay: AlarmOverlayHost,
    private val onOverlayUnavailable: (AlarmPresentation, String) -> Unit,
    private val onDismissRequested: (AlarmPresentation) -> Unit
) {
    private val sessions = LinkedHashMap<AlarmSession, AlarmPresentation>()
    private val reportedUnavailable = mutableSetOf<AlarmSession>()
    private var screenOn = true
    private var visibleActivity: AlarmSession? = null
    private var shownOverlay: AlarmSession? = null

    fun onAlarmStarted(presentation: AlarmPresentation) {
        // 新 revision 到达后，旧会话绝不能继续遮挡新内容。
        sessions.keys.filter { it.alarmId == presentation.alarmId && it.revision < presentation.revision }
            .toList().forEach(sessions::remove)
        sessions[presentation.session] = presentation
        reconcile()
    }

    fun onAlarmUpdated(presentation: AlarmPresentation) {
        if (sessions.containsKey(presentation.session)) {
            sessions[presentation.session] = presentation
            if (shownOverlay == presentation.session) {
                overlay.remove()
                shownOverlay = null
            }
            reconcile()
        }
    }

    fun onAlarmStopped(session: AlarmSession) {
        sessions.remove(session)
        reportedUnavailable.remove(session)
        if (visibleActivity == session) visibleActivity = null
        reconcile()
    }

    fun onUserPresent() {
        screenOn = true
        reconcile()
    }

    fun onScreenOn() {
        screenOn = true
        reconcile()
    }

    fun onScreenOff() {
        screenOn = false
        reconcile()
    }

    fun onActivityVisible(session: AlarmSession) {
        if (sessions.containsKey(session)) visibleActivity = session
        reconcile()
    }

    fun onActivityHidden(session: AlarmSession) {
        if (visibleActivity == session) visibleActivity = null
        reconcile()
    }

    fun dispose() {
        sessions.clear()
        visibleActivity = null
        shownOverlay = null
        overlay.remove()
    }

    private fun reconcile() {
        val target = sessions.values.lastOrNull()
        val canOverlay = target != null && screenOn && !isKeyguardLocked() && visibleActivity == null
        if (!canOverlay) {
            if (shownOverlay != null) overlay.remove()
            shownOverlay = null
            return
        }
        if (shownOverlay == target.session) return
        if (shownOverlay != null) overlay.remove()
        shownOverlay = null
        when (val result = overlay.show(target) { onDismissRequested(target) }) {
            AlarmOverlayResult.Shown -> shownOverlay = target.session
            AlarmOverlayResult.PermissionMissing -> unavailable(target, "overlay_permission_missing")
            is AlarmOverlayResult.Failed -> unavailable(target, "overlay_window_failed")
        }
    }

    private fun unavailable(presentation: AlarmPresentation, reason: String) {
        if (reportedUnavailable.add(presentation.session)) onOverlayUnavailable(presentation, reason)
    }
}

/** AlarmActivity 不持有 Service；仅把自身可见性送给当前响铃服务。 */
object AlarmPresentationRegistry {
    private var coordinator: WeakReference<AlarmPresentationCoordinator>? = null

    fun attach(value: AlarmPresentationCoordinator) {
        coordinator = WeakReference(value)
    }

    fun detach(value: AlarmPresentationCoordinator) {
        if (coordinator?.get() === value) coordinator = null
    }

    fun activityVisible(session: AlarmSession) = coordinator?.get()?.onActivityVisible(session)

    fun activityHidden(session: AlarmSession) = coordinator?.get()?.onActivityHidden(session)
}
