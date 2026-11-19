package com.lemonkids.kidliteracy

import com.lemonkids.kidliteracy.feature.reading.ReadingContentSource
import com.lemonkids.kidliteracy.feature.reading.ReadingTarget
import com.lemonkids.kidliteracy.feature.reading.practiceProgressKey
import com.lemonkids.kidliteracy.feature.reading.requiredCorrectReadings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingEvaluationRulesTest {
    @Test fun practiceRequirementsUseOneCorrectReadingPerWordAndSentence() {
        assertEquals(3, ReadingTarget("task", "character", "花").requiredCorrectReadings())
        assertEquals(1, ReadingTarget("task", "word", "花朵").requiredCorrectReadings())
        assertEquals(1, ReadingTarget("task", "sentence", "花开了。").requiredCorrectReadings())
        assertEquals(1, ReadingTarget("known", "word", "花朵",
            contentSource = ReadingContentSource.RECOGNIZED).requiredCorrectReadings())
    }

    @Test fun taskWordUsesNewThresholdForCurrentAndSavedProgress() {
        val target = ReadingTarget("task", "word", "花朵")
        val key = target.practiceProgressKey()

        assertFalse(learningContent(target, emptyMap(), completed = false).learned)
        assertTrue(learningContent(target, mapOf(key to 1), completed = false).learned)
        val saved = learningContent(target, mapOf(key to 2), completed = false)
        assertTrue(saved.learned)
        assertEquals(1, saved.correctReadings)
        assertEquals(1, saved.requiredReadings)
    }

    @Test fun recognizedCharacterPreservesPartialProgressAndCapsOldStars() {
        val target = ReadingTarget("known", "character", "花",
            contentSource = ReadingContentSource.RECOGNIZED, characterRequiredReadings = 2)
        val key = target.practiceProgressKey()

        val partial = learningContent(target, mapOf(key to 1), completed = false)
        assertFalse(partial.learned)
        assertEquals(1, partial.correctReadings)
        assertEquals(1, partial.requiredReadings - partial.correctReadings)

        val saved = learningContent(target, mapOf(key to 3), completed = false)
        assertTrue(saved.learned)
        assertEquals(2, saved.correctReadings)
        assertEquals(2, saved.requiredReadings)
    }

    private fun word(score: String? = null, tag: String? = "0", text: String = "花"): JsonObject {
        val fields = mutableListOf("\"Word\":\"$text\"")
        if (score != null) fields += "\"PronAccuracy\":\"$score\""
        if (tag != null) fields += "\"MatchTag\":\"$tag\""
        return Json.parseToJsonElement("{${fields.joinToString(",")}}") as JsonObject
    }

    @Test fun thresholdsApplyAtBoundariesOnlyAfterMatching() {
        for ((type, threshold) in listOf("character" to 60, "word" to 50, "sentence" to 30)) {
            assertTrue(word("${threshold - 1}").isPronunciationWrong(type))
            assertFalse(word("$threshold").isPronunciationWrong(type))
            assertFalse(word("${threshold / 100.0}").isPronunciationWrong(type))
            assertTrue(word("99", "3").isPronunciationWrong(type))
        }
    }

    @Test fun missingOrInvalidMatchTagFailsAndMissingScoreUsesMatchFallback() {
        assertTrue(word("99", null).isPronunciationWrong("word"))
        assertTrue(word("99", "unknown").isPronunciationWrong("word"))
        assertFalse(word(null, "0").isPronunciationWrong("word"))
        assertFalse(word("2", "3", "*").isPronunciationWrong("word"))
    }

    @Test fun unreturnedReferenceCharactersRemainWrong() {
        assertEquals(setOf(1), listOf(word("60")).toWrongCharacterIndexes("花朵", listOf(0, 1), "word"))
        assertEquals(setOf(0, 1), emptyList<JsonObject>().toWrongCharacterIndexes("桂桂", listOf(0, 1), "character"))
    }

    @Test fun diagnosticsUseSameThresholdAndExplicitTagReasons() {
        val response = """{"Words":[{"Word":"花","PronAccuracy":"49","MatchTag":"3"}]}"""
        val (_, reasons) = extractTencentDiagnostic(response, "花", "word")
        assertTrue("low_pron_accuracy" in reasons)
        assertTrue("nonzero_match_tag" in reasons)
        val (_, sentenceReasons) = extractTencentDiagnostic(response, "花", "sentence")
        assertFalse("low_pron_accuracy" in sentenceReasons)
        val (_, missingReasons) = extractTencentDiagnostic("""{"Words":[{"Word":"花","PronAccuracy":"99"}]}""", "花", "word")
        assertTrue("missing_match_tag" in missingReasons)
    }
}
