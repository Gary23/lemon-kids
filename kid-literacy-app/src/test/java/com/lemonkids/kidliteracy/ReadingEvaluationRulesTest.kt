package com.lemonkids.kidliteracy

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingEvaluationRulesTest {
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
