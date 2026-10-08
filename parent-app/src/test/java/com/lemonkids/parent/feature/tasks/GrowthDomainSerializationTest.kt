package com.lemonkids.parent.feature.tasks

import com.lemonkids.shared.model.GrowthDomain
import com.lemonkids.shared.model.Task
import com.lemonkids.shared.model.TaskTemplate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthDomainSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun oldRowsKeepUnsetDomainWhileNewSnapshotsRoundTrip() {
        val oldTemplate = json.decodeFromString<TaskTemplate>("""{"id":"template-1","title":"阅读"}""")
        val oldTask = json.decodeFromString<Task>("""{"id":"task-1","title":"阅读"}""")
        assertNull(oldTemplate.growthDomain)
        assertNull(oldTask.growthDomain)
        assertEquals("未设置（不计专项）", GrowthDomain.label(oldTask.growthDomain))

        val snapshot = oldTask.copy(growthDomain = "reading")
        assertEquals("reading", json.decodeFromString<Task>(json.encodeToString(snapshot)).growthDomain)
        assertTrue(GrowthDomain.choices.map { it.first }.containsAll(
            listOf("reading", "calculation", "dictation", "english", "writing", "math_thinking", "life", "other")
        ))
    }
}
