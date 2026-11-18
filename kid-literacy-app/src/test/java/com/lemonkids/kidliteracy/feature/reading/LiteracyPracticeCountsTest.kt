package com.lemonkids.kidliteracy.feature.reading

import android.app.Application
import com.lemonkids.kidliteracy.feature.home.LiteracyCharacterGroup
import com.lemonkids.kidliteracy.feature.home.LiteracyGroupType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LiteracyPracticeCountsTest {
    private val word = ReadingTarget("id", "word", "花朵", wordText = "花朵")
    private fun recognizedCharacter(group: Int): ReadingTarget {
        val required = LiteracyCharacterGroup(type = LiteracyGroupType.KNOWN, groupNumber = group,
            characters = listOf("花")).recognizedCharacterRequiredReadings
        return ReadingTarget("id", "character", "花", contentSource = ReadingContentSource.RECOGNIZED,
            characterRequiredReadings = required)
    }

    @Test fun requiredCountsFollowDisplayedGroupAndContentType() {
        assertEquals(listOf(2, 2, 1, 1), (1..4).map { recognizedCharacter(it).requiredCorrectReadings() })
        assertEquals(3, ReadingTarget("id", "character", "花").requiredCorrectReadings())
        assertEquals(1, ReadingTarget("id", "sentence", "花开了").requiredCorrectReadings())
        assertEquals(1, word.requiredCorrectReadings())
        assertEquals(1, word.copy(contentSource = ReadingContentSource.RECOGNIZED).requiredCorrectReadings())
    }

    @Test fun newPracticeStopsAtNewLimitsAndTracksWordsSeparately() {
        val store = LiteracyPracticeProgressStore(RuntimeEnvironment.getApplication(), "new")
        val other = word.copy(displayText = "花园", wordText = "花园", itemOrder = 1)
        val first = recognizedCharacter(1)
        assertTrue(store.snapshot().isEmpty())
        assertEquals(1, store.recordCorrectReadings(word))
        assertEquals(1, store.recordCorrectReadings(word))
        assertEquals(null, store.snapshot()[other.practiceProgressKey()])
        assertEquals(1, store.recordCorrectReadings(first))
        assertEquals(2, store.recordCorrectReadings(first, 2))
        assertEquals(2, store.recordCorrectReadings(first))
        assertEquals(store.snapshot(), store.pendingProgress())
    }

    @Test fun historicalCountsSurviveRemoteMergeLocalPracticeAndParentPass() {
        val store = LiteracyPracticeProgressStore(RuntimeEnvironment.getApplication(), "history")
        val first = recognizedCharacter(1)
        val targets = listOf(word, first)
        val original = mapOf(word.practiceProgressSyncKey() to 2, first.practiceProgressSyncKey() to 3)
        store.mergeRemoteProgress(original, targets)
        store.mergeRemoteProgress(original.mapValues { 1 }, targets)
        assertEquals(2, store.recordCorrectReadings(word))
        assertEquals(3, store.recordCorrectReadings(first))
        store.markAllCorrectReadings(targets)
        assertEquals(2, store.snapshot()[word.practiceProgressKey()])
        assertEquals(3, store.snapshot()[first.practiceProgressKey()])
        assertTrue(store.pendingProgress().isEmpty())
        store.queueExistingProgressForSync()
        assertEquals(store.snapshot(), store.pendingProgress())
    }

    @Test fun explicitUndoRestoresTheRecordedSnapshotNotTodaysThreshold() {
        val store = LiteracyPracticeProgressStore(RuntimeEnvironment.getApplication(), "undo")
        val first = recognizedCharacter(1)
        store.restoreReadings(listOf(word to 2, first to 3))
        assertEquals(2, store.snapshot()[word.practiceProgressKey()])
        assertEquals(3, store.snapshot()[first.practiceProgressKey()])
        store.applyRemoteProgressReset(mapOf(word.practiceProgressSyncKey() to 2, first.practiceProgressSyncKey() to 1), listOf(word, first))
        assertEquals(2, store.snapshot()[word.practiceProgressKey()])
        assertEquals(1, store.snapshot()[first.practiceProgressKey()])
        assertTrue(store.pendingProgress().isEmpty())
    }
}
