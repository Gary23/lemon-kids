package com.lemonkids.shared.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChildBindingStoreTest {
    @Test fun taskRecoveryIncludesBothExactApplicationIds() {
        assertTrue(ChildBindingStore.isTaskApplication("com.lemonkids.kidtask"))
        assertTrue(ChildBindingStore.isTaskApplication("com.lemonkids.kidtask.local"))
        listOf("com.lemonkids.parent.local", "com.lemonkids.kidmonitor", "com.lemonkids.kidtask.other",
            "com.lemonkids.kidtask.local.other", "com.lemonkids.kidtask:local_restart", "")
            .forEach { assertFalse(it, ChildBindingStore.isTaskApplication(it)) }
    }
}
