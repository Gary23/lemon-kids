package com.lemonkids.shared.repository.impl

import com.lemonkids.shared.model.BadgeProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeWriteResolutionTest {
    private fun record(value: Int, version: Int) = BadgeProgress(
        childId = "child", familyId = "family", badgeKey = "recognition",
        value = value, version = version
    )

    @Test fun committedWriteIsSuccessEvenWhenRpcResponseWasLost() {
        val latest = record(value = 1071, version = 1)
        assertEquals(BadgeWriteResolution.Saved(latest), resolveBadgeWrite(latest, expectedVersion = 0, requestedValue = 1071))
    }

    @Test fun differentNewerValueIsConflict() {
        val latest = record(value = 1200, version = 2)
        assertEquals(BadgeWriteResolution.Conflict(latest), resolveBadgeWrite(latest, expectedVersion = 1, requestedValue = 1071))
    }

    @Test fun unchangedValueAndVersionIsRejected() {
        assertTrue(resolveBadgeWrite(record(value = 1000, version = 1), expectedVersion = 1, requestedValue = 1071) is BadgeWriteResolution.Rejected)
    }
}
