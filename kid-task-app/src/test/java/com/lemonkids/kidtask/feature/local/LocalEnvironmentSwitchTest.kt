package com.lemonkids.kidtask.feature.local

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalEnvironmentSwitchTest {
    @Test fun savesOnlyAfterEveryOldStateHasBeenCleared() = runBlocking {
        val calls = mutableListOf<String>()
        switchLocalEnvironment(
            clearSession = { calls.add("session") },
            clearBinding = { calls.add("binding"); true },
            clearCaches = { calls.add("caches"); true },
            saveAddress = { calls.add("save"); true }
        )
        assertEquals(listOf("session", "binding", "caches", "save"), calls)
    }

    @Test fun clearFailuresNeverSaveNewAddressAndCanBeRetried() = runBlocking {
        for (failure in listOf("session", "binding", "caches")) {
            var saved = false
            val result = runCatching {
                switchLocalEnvironment(
                    clearSession = { if (failure == "session") error("failed") },
                    clearBinding = { failure != "binding" },
                    clearCaches = { failure != "caches" },
                    saveAddress = { saved = true; true }
                )
            }
            assertTrue(result.isFailure)
            assertEquals(false, saved)
        }
    }

    @Test fun persistenceFailureIsReportedInsteadOfContinuing() = runBlocking {
        assertTrue(runCatching {
            switchLocalEnvironment({}, { true }, { true }, { false })
        }.isFailure)
    }
}
