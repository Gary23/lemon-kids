package com.lemonkids.parent.feature.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRestartTargetTest {
    @Test fun onlySameUidMainProcessCanBeStopped() {
        assertTrue(isLocalRestartTarget(123, 10418, "app.local", 456, 10418, "app.local"))
        assertFalse(isLocalRestartTarget(123, 10419, "app.local", 456, 10418, "app.local"))
        assertFalse(isLocalRestartTarget(123, 10418, "app", 456, 10418, "app.local"))
        assertFalse(isLocalRestartTarget(123, 10418, "app.local:local_restart", 456, 10418, "app.local"))
    }

    @Test fun helperAndInvalidPidCannotBeStopped() {
        assertFalse(isLocalRestartTarget(456, 10418, "app.local", 456, 10418, "app.local"))
        assertFalse(isLocalRestartTarget(-1, 10418, "app.local", 456, 10418, "app.local"))
        assertFalse(isLocalRestartTarget(0, 10418, "app.local", 456, 10418, "app.local"))
    }
}
