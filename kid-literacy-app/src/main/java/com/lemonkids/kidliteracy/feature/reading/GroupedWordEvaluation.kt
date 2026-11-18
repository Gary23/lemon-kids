package com.lemonkids.kidliteracy.feature.reading

import com.lemonkids.kidliteracy.isPronunciationWrong
import kotlinx.serialization.json.*

/** 下标属于原词 displayText，不是整组文本的偏移。 */
data class GroupedWordResult(val passed: Boolean, val uncertain: Boolean, val wrongCharacterIndexes: Set<Int>)

private fun Char.assetHan() = this in '\u4e00'..'\u9fff'

fun mergeWordEvaluations(targets: List<ReadingTarget>, references: List<PreparedEvaluation>): PreparedEvaluation {
    require(targets.isNotEmpty() && targets.size == references.size)
    val merged = buildJsonArray {
        targets.zip(references).forEach { (target, ref) ->
            require(target.targetType == "word" && ref.textMode == 1)
            val list = Json.parseToJsonElement(ref.refText).jsonObject["wordList"] as? JsonArray
            val chars = target.displayText.filter(Char::assetHan)
            require(list != null && list.isNotEmpty() && list.size == chars.length)
            list.forEachIndexed { i, item ->
                require((item as? JsonObject)?.get("word") == JsonPrimitive(chars[i].toString()))
                add(item) // pron is optional (neutral tone); retain the authorized asset verbatim.
            }
        }
    }
    return PreparedEvaluation(buildJsonObject { put("wordList", merged) }.toString(), 1)
}

private fun JsonElement.findGroupedWords(depth: Int = 0): JsonArray? {
    if (depth > 24) return null
    return when (this) {
        is JsonObject -> (this["Words"] as? JsonArray)?.takeIf { it.isNotEmpty() }
            ?: values.firstNotNullOfOrNull { it.findGroupedWords(depth + 1) }
        is JsonArray -> firstNotNullOfOrNull { it.findGroupedWords(depth + 1) }
        is JsonPrimitive -> content.takeIf { isString && it.trimStart().firstOrNull() in listOf('{', '[') }
            ?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull()?.findGroupedWords(depth + 1) }
    }
}

private data class ExpectedChar(val char: Char, val word: Int, val offset: Int)
private data class HeardChar(val char: Char, val item: Int, val failed: Boolean)

fun parseGroupedWordEvaluation(response: String, targets: List<ReadingTarget>): List<GroupedWordResult> {
    fun unknown() = targets.map { GroupedWordResult(false, true, emptySet()) }
    val items = runCatching { Json.parseToJsonElement(response).findGroupedWords() }.getOrNull() ?: return unknown()
    val expected = targets.flatMapIndexed { w, target -> target.displayText.mapIndexedNotNull { i, c ->
        if (c.assetHan()) ExpectedChar(c, w, i) else null
    } }
    val heard = mutableListOf<HeardChar>()
    for ((i, item) in items.withIndex()) {
        val obj = item as? JsonObject ?: return unknown()
        val text = ((obj["Word"] ?: obj["word"]) as? JsonPrimitive)?.contentOrNull ?: return unknown()
        val failed = runCatching { obj.isPronunciationWrong("word") }.getOrElse { return unknown() }
        text.filter(Char::assetHan).forEach { heard += HeardChar(it, i, failed) }
    }
    val n = expected.size
    val m = heard.size
    if (n == 0 || m == 0 || (n + 1L) * (m + 1L) > 250_000L) return unknown()

    // Acyclic edit graph. Minimize edits first; among ties favor intact contiguous reference
    // words (花朵/花生 is stronger evidence than splitting the second 花 from 生).
    // The full-word edge costs -1; there are at most targets.size such edges per path.
    val unit = targets.size + 1
    val span = IntArray(n)
    for (i in 0 until n) if (i == 0 || expected[i - 1].word != expected[i].word) {
        var end = i + 1
        while (end < n && expected[end].word == expected[i].word) end++
        span[i] = end - i
    }
    // Each edge describes how many expected and heard chars are consumed. Equal positive
    // widths denote pairwise alignment, including length-one substitutions.
    fun edges(i: Int, j: Int, visit: (Int, Int, Int) -> Unit) {
        if (i < n) visit(1, 0, unit)
        if (j < m) visit(0, 1, unit)
        if (i < n && j < m) {
            visit(1, 1, if (expected[i].char == heard[j].char) 0 else unit)
            val k = span[i]
            if (k > 0 && j + k <= m && (0 until k).all { expected[i + it].char == heard[j + it].char }) visit(k, k, -1)
        }
    }
    val best = Array(n + 1) { IntArray(m + 1) { Int.MAX_VALUE } }
    best[n][m] = 0
    for (i in n downTo 0) for (j in m downTo 0) edges(i, j) { a, b, cost ->
        best[i][j] = minOf(best[i][j], cost + best[i + a][j + b])
    }
    val reachable = Array(n + 1) { BooleanArray(m + 1) }
    reachable[0][0] = true
    val unmapped = -2
    val mapping = IntArray(n) { unmapped }
    val ambiguous = BooleanArray(targets.size)
    val failedOwners = mutableMapOf<Int, MutableSet<Int>>()
    for (i in 0..n) for (j in 0..m) if (reachable[i][j]) edges(i, j) { a, b, cost ->
        if (best[i][j] == cost + best[i + a][j + b]) {
            reachable[i + a][j + b] = true
            for (k in 0 until a) {
                val p = i + k
                val w = expected[p].word
                val h = if (b == 0) -1 else j + k
                if (mapping[p] == unmapped) mapping[p] = h else if (mapping[p] != h) ambiguous[w] = true
                if (h >= 0 && heard[h].failed) failedOwners.getOrPut(heard[h].item) { mutableSetOf() }.add(w)
            }
        }
    }
    failedOwners.values.filter { it.size > 1 }.forEach { owners -> owners.forEach { ambiguous[it] = true } }
    val wrong = List(targets.size) { mutableSetOf<Int>() }
    for ((i, ref) in expected.withIndex()) {
        val h = heard.getOrNull(mapping[i])
        if (h == null || h.failed || h.char != ref.char) wrong[ref.word].add(ref.offset)
    }
    return targets.indices.map { w ->
        if (ambiguous[w] || expected.none { it.word == w }) GroupedWordResult(false, true, emptySet())
        else GroupedWordResult(wrong[w].isEmpty(), false, wrong[w].toSet())
    }
}
