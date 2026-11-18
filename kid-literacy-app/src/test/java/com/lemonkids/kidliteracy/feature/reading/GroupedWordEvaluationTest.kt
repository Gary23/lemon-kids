package com.lemonkids.kidliteracy.feature.reading

import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupedWordEvaluationTest {
    private fun targets(vararg texts: String) = texts.mapIndexed { i, s -> ReadingTarget("task", "word", s, itemOrder = i, wordText = s) }
    private val flowers = targets("花朵", "花园", "花生")
    private val pass = GroupedWordResult(true, false, emptySet())
    private val unclear = GroupedWordResult(false, true, emptySet())
    private fun wrong(vararg offsets: Int) = GroupedWordResult(false, false, offsets.toSet())
    private fun item(text: String, score: Int = 90) = buildJsonObject {
        put("Word", text); put("MatchTag", 0); put("PronAccuracy", score)
    }
    private fun reply(vararg words: JsonObject) = buildJsonObject {
        put("data", buildJsonObject { put("Words", JsonArray(words.toList())) }.toString())
    }.toString()
    private fun result(vararg words: JsonObject) = parseGroupedWordEvaluation(reply(*words), flowers)

    @Test fun allPassIncludingNestedStringAndNonTargetSound() {
        assertEquals(List(3) { pass }, result(item("*", 0), item("花朵"), item("花园"), item("花生")))
    }
    @Test fun onlySecondWordFailsAndIndexesAreLocal() {
        assertEquals(listOf(pass, wrong(1), pass), result(item("花朵"), item("花"), item("园", 49), item("花生")))
        assertEquals(listOf(pass, wrong(1), pass), result(item("花朵"), item("花圆"), item("花生")))
    }
    @Test fun skippedMiddleDoesNotShiftLastResult() {
        assertEquals(listOf(pass, wrong(0, 1), pass), result(item("花"), item("朵"), item("花"), item("生")))
    }
    @Test fun insertedSyllableDoesNotReplaceFollowingWord() {
        assertEquals(List(3) { pass }, result(item("花朵"), item("啊", 20), item("花园"), item("花生")))
    }
    @Test fun competingAssignmentsDoNotAccuseEitherRepeatedWord() {
        assertEquals(listOf(unclear, unclear, pass), parseGroupedWordEvaluation(reply(item("花"), item("树")), targets("花", "花", "树")))
    }
    @Test fun indivisibleFailingItemSpanningWordsIsUnclearForBoth() {
        assertEquals(listOf(unclear, unclear, pass), result(item("花"), item("朵花", 40), item("园"), item("花生")))
    }
    @Test fun noUsableResponseGivesNoStarsAndNoAccusations() {
        for (raw in listOf("", "not json", "{}", reply(), reply(item("*")))) {
            assertEquals(List(3) { unclear }, parseGroupedWordEvaluation(raw, flowers))
        }
    }
    private fun ref(vararg entries: String) = PreparedEvaluation("{\"wordList\":[${entries.joinToString(",")}]}", 1)
    @Test fun mergesAuthorizedPhonemesWithoutFabricatingNeutralTone() {
        val a = """{"word":"花","pron":[["hua1"]]}"""
        val b = """{"word":"朵"}"""
        val c = """{"word":"园","pron":[["yuan2"]]}"""
        val out = mergeWordEvaluations(flowers.take(2), listOf(ref(a,b), ref(a,c)))
        assertEquals(1, out.textMode)
        assertEquals(Json.parseToJsonElement(ref(a,b,a,c).refText), Json.parseToJsonElement(out.refText))
    }
    @Test fun rejectsModeMismatchEmptyAssetsOrWrongText() {
        val good = ref("""{"word":"花"}""", """{"word":"朵"}""")
        for (refs in listOf(emptyList(), listOf(good.copy(textMode = 0)), listOf(ref()), listOf(ref("""{"word":"树"}""")), listOf(ref("""{"word":"花"}""", """{"word":"生"}""")))) {
            assertTrue(runCatching { mergeWordEvaluations(flowers.take(1), refs) }.isFailure)
        }
    }
}
