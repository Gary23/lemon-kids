package com.lemonkids.kidtask.reminder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalReminderTest {
    @Test fun anAddressRoundTripCannotReviveAnOldReminder() {
        assertTrue(isCurrentLocalReminder("new-epoch", "new-epoch"))
        assertFalse(isCurrentLocalReminder("new-epoch", "old-epoch"))
        assertFalse(isCurrentLocalReminder(null, "old-epoch"))
        assertFalse(isCurrentLocalReminder(null, null))
        assertFalse(isCurrentLocalReminder("new-epoch", null))
    }
}
