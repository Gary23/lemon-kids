package com.lemonkids.kidmonitor.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmPresentationCoordinatorTest {
    @Test fun `锁屏解锁和 Activity 可见性会正确切换覆盖层`() {
        var locked = true
        val overlay = FakeOverlay()
        val coordinator = coordinator(overlay) { locked }
        val alarm = AlarmPresentation("a", 1)

        coordinator.onAlarmStarted(alarm)
        assertEquals(0, overlay.shown.size)
        locked = false
        coordinator.onUserPresent()
        assertEquals(listOf(alarm), overlay.shown)
        coordinator.onActivityVisible(alarm.session)
        assertEquals(1, overlay.removed)
        coordinator.onActivityHidden(alarm.session)
        assertEquals(2, overlay.shown.size)
    }

    @Test fun `停止竞态和旧 revision 不会移除新展示`() {
        val overlay = FakeOverlay()
        val coordinator = coordinator(overlay) { false }
        val old = AlarmPresentation("a", 1)
        val newer = AlarmPresentation("a", 2)
        coordinator.onAlarmStarted(old)
        coordinator.onAlarmStarted(newer)
        coordinator.onAlarmStopped(old.session)
        assertEquals(newer, overlay.shown.last())
    }

    @Test fun `加窗失败只上报一次且不影响后续停止`() {
        val overlay = FakeOverlay(AlarmOverlayResult.PermissionMissing)
        val unavailable = mutableListOf<String>()
        val coordinator = AlarmPresentationCoordinator({ false }, overlay, { _, reason -> unavailable += reason }, {})
        val alarm = AlarmPresentation("a", 1)
        coordinator.onAlarmStarted(alarm)
        coordinator.onScreenOn()
        coordinator.onAlarmStopped(alarm.session)
        assertEquals(listOf("overlay_permission_missing"), unavailable)
        assertTrue(overlay.removed >= 0)
    }

    private fun coordinator(overlay: FakeOverlay, locked: () -> Boolean) =
        AlarmPresentationCoordinator(locked, overlay, { _, _ -> }, {})

    private class FakeOverlay(private val result: AlarmOverlayResult = AlarmOverlayResult.Shown) : AlarmOverlayHost {
        val shown = mutableListOf<AlarmPresentation>()
        var removed = 0
        override fun show(presentation: AlarmPresentation, onDismiss: () -> Unit): AlarmOverlayResult {
            shown += presentation
            return result
        }
        override fun remove() { removed++ }
    }
}
