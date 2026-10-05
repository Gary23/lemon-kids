package com.lemonkids.parent.feature.profile

import com.lemonkids.shared.repository.impl.BadgeProgressSaveUnconfirmed
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeSaveErrorTest {
    @Test fun rawNetworkExceptionCannotReachUserMessage() {
        val message = badgeSaveErrorMessage(IllegalStateException("Headers: Authorization=Bearer private-token; URL: https://example.test"))
        assertFalse(message.contains("Bearer"))
        assertFalse(message.contains("private-token"))
        assertFalse(message.contains("Headers"))
        assertTrue(message.contains("保存失败"))
    }

    @Test fun unconfirmedSaveRequestsRefresh() {
        assertTrue(badgeSaveErrorMessage(BadgeProgressSaveUnconfirmed()).contains("刷新"))
    }
}
