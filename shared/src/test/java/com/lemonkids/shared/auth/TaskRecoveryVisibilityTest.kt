package com.lemonkids.shared.auth

import com.lemonkids.shared.ui.auth.taskRecoveryMustRemainVisible
import org.junit.Assert.*
import org.junit.Test

class TaskRecoveryVisibilityTest {
    @Test fun sdkDiscardingSessionAfterFailedRefreshDoesNotHideRecovery() {
        assertTrue(taskRecoveryMustRemainVisible(false, TaskSessionUnavailableException()))
    }

    @Test fun existingRecoveryIsPreservedEvenForLaterNetworkErrors() {
        assertTrue(taskRecoveryMustRemainVisible(true, IllegalStateException("offline")))
    }

    @Test fun firstTimeUnboundFailureDoesNotInventRecovery() {
        assertFalse(taskRecoveryMustRemainVisible(false, IllegalStateException("unbound")))
    }
}
