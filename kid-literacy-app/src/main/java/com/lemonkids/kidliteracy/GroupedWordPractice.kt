package com.lemonkids.kidliteracy

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lemonkids.kidliteracy.feature.reading.*
import com.tencent.cloud.soe.TAIOralController
import com.tencent.cloud.soe.audio.data.TAIDataSource
import com.tencent.cloud.soe.entity.*
import com.tencent.cloud.soe.listener.OralEvaluationStateListener
import com.tencent.cloud.soe.listener.TAIListener
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

private data class GroupedWordDiagnostic(
    val words: JsonArray,
    val reasons: List<String>,
    val raw: JsonObject? = null,
    val rawStatus: String = "unavailable"
)

/**
 * A group response is not a word response. Project only whole Tencent items with an exact,
 * complete character alignment; omissions, substitutions and cross-word items are not guessed.
 * Group raw is deliberately absent: it cannot truthfully be attributed to a single word.
 */
private fun prepareGroupedWordDiagnostics(response: String, targets: List<ReadingTarget>): List<GroupedWordDiagnostic?> {
    val unavailable = List<GroupedWordDiagnostic?>(targets.size) { null }
    return runCatching {
        if (targets.any { it.targetType != "word" }) return unavailable
        if (targets.size == 1) {
            val diagnostic = extractTencentDiagnostic(response, targets.single().displayText, "word")
            if (diagnostic.second.isEmpty()) return unavailable
            val raw = prepareSingleWordDiagnosticRaw(response)
            return listOf(GroupedWordDiagnostic(diagnostic.first, diagnostic.second, raw.first, raw.second))
        }
        fun findWords(value: JsonElement, depth: Int = 0): JsonArray? {
            if (depth > 24) return null
            return when (value) {
                is JsonObject -> (value["Words"] as? JsonArray)?.takeIf { it.isNotEmpty() }
                    ?: value.values.firstNotNullOfOrNull { findWords(it, depth + 1) }
                is JsonArray -> value.firstNotNullOfOrNull { findWords(it, depth + 1) }
                is JsonPrimitive -> value.content.takeIf {
                    value.isString && it.trimStart().firstOrNull() in listOf('{', '[')
                }?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() }
                    ?.let { findWords(it, depth + 1) }
            }
        }
        val words = findWords(Json.parseToJsonElement(response)) ?: return unavailable
        val owners = targets.flatMapIndexed { index, target ->
            target.displayText.filter { it in '\u4e00'..'\u9fff' }.map { index }
        }
        val expected = targets.joinToString("") { target ->
            target.displayText.filter { it in '\u4e00'..'\u9fff' }
        }
        val texts = words.map { item ->
            val word = item as? JsonObject ?: return unavailable
            ((word["Word"] ?: word["word"]) as? JsonPrimitive)?.contentOrNull
                ?.filter { it in '\u4e00'..'\u9fff' } ?: return unavailable
        }
        if (texts.joinToString("") != expected) return unavailable
        val scoped = List(targets.size) { mutableListOf<JsonElement>() }
        val crossed = mutableSetOf<Int>()
        var cursor = 0
        words.zip(texts).forEach { (word, text) ->
            val itemOwners = owners.subList(cursor, cursor + text.length).toSet()
            cursor += text.length
            if (itemOwners.size == 1) scoped[itemOwners.single()].add(word)
            else crossed.addAll(itemOwners)
        }
        targets.mapIndexed { index, target ->
            if (index in crossed || scoped[index].isEmpty()) null
            else {
                // This envelope is only input to the summary extractor; it is never sent as raw.
                val projected = buildJsonObject { put("Words", JsonArray(scoped[index])) }
                val diagnostic = extractTencentDiagnostic(projected.toString(), target.displayText, "word")
                diagnostic.second.takeIf { it.isNotEmpty() }?.let {
                    GroupedWordDiagnostic(diagnostic.first, it)
                }
            }
        }
    }.getOrDefault(unavailable)
}

/** Same privacy/size boundary as the original single-target path; never used for group raw. */
private fun prepareSingleWordDiagnosticRaw(response: String): Pair<JsonObject?, String> {
    val root = runCatching { Json.parseToJsonElement(response) as? JsonObject }.getOrNull()
        ?: return null to "invalid_json"
    var redacted = false
    val forbidden = Regex("(?i)(secret|token|credential|authorization|password|audio|voice|base64|payload|private.?key|access.?key)")
    fun clean(value: JsonElement, depth: Int): JsonElement {
        if (depth > 24) return JsonPrimitive("[depth limit]").also { redacted = true }
        return when (value) {
            is JsonObject -> JsonObject(value.mapNotNull { (key, child) ->
                if (forbidden.containsMatchIn(key)) { redacted = true; null }
                else key to clean(child, depth + 1)
            }.toMap())
            is JsonArray -> JsonArray(value.map { clean(it, depth + 1) })
            else -> value
        }
    }
    val safe = clean(root, 0) as JsonObject
    if (safe.toString().toByteArray(Charsets.UTF_8).size > 64 * 1024) return null to "too_large"
    return safe to if (redacted) "redacted" else "complete"
}

/** One visual group, but never a synthetic progress/audio target. */
@Composable
internal fun GroupedWordPractice(
    items: List<Pair<ReadingTarget, Int>>,
    active: Boolean,
    otherRecordingActive: Boolean,
    onStart: () -> Unit,
    onReadingStateChanged: (RecordingState) -> Unit,
    onSpeak: (ReadingTarget, String) -> Unit,
    onStopPlayback: () -> Unit,
    onCorrectReadings: (ReadingTarget, Int) -> Int
) {
    if (items.isEmpty()) return
    val identity = items.map { it.first.practiceProgressKey() }
    key(identity) {
        val context = LocalContext.current
        val viewModel: ReadingEvaluationViewModel = hiltViewModel()
        val scope = rememberCoroutineScope()
        val main = remember { Handler(Looper.getMainLooper()) }
        val currentItems by rememberUpdatedState(items)
        val award by rememberUpdatedState(onCorrectReadings)
        val stateChanged by rememberUpdatedState(onReadingStateChanged)
        var state by remember { mutableStateOf(RecordingState.READY) }
        var message by remember { mutableStateOf("按顺序把这些词连起来读") }
        var attempted by remember { mutableStateOf(false) }
        var controller by remember { mutableStateOf<TAIOralController?>(null) }
        var attempt by remember { mutableIntStateOf(0) }
        var alive by remember { mutableStateOf(false) }
        val results = remember { mutableStateMapOf<String, GroupedWordResult>() }
        val highlighted = remember { mutableStateMapOf<String, Int>() }
        fun pending() = currentItems.filter { (target, count) ->
            count < target.requiredCorrectReadings()
        }.map { it.first }
        fun release(cancel: Boolean) {
            val old = controller
            controller = null
            try {
                if (cancel) runCatching { old?.cancelOralEvaluation() }
            } finally {
                runCatching { old?.release() }
            }
        }
        fun begin() {
            val remaining = pending()
            if (remaining.isEmpty()) return
            val targets = if (!attempted && remaining.size == currentItems.size) remaining else listOf(remaining.first())
            val id = ++attempt
            state = RecordingState.PREPARING
            message = "正在准备朗读…"
            onStopPlayback()
            highlighted.clear()
            scope.launch {
                try {
                    val credentials = viewModel.credentialsForEvaluation().getOrThrow()
                    val references = targets.map { viewModel.prepareCachedEvaluation(it).getOrThrow() }
                    val reference = if (targets.size == 1) references.single() else mergeWordEvaluations(targets, references)
                    if (!alive || id != attempt) return@launch
                    val params = java.util.TreeMap<String, Any>().apply {
                        put(HttpParameterKey.SERVER_ENGINE_TYPE, "16k_zh")
                        put(HttpParameterKey.EVAL_MODE, 1)
                        put(HttpParameterKey.TEXT_MODE, reference.textMode)
                        put(HttpParameterKey.SCORE_COEFF, 1.0)
                        put(HttpParameterKey.SENTENCE_INFO_ENABLED, 1)
                        put(HttpParameterKey.REF_TEXT, reference.refText)
                    }
                    val listener = object : TAIListener {
                        override fun onMessage(msg: String) = Unit
                        override fun onVad() = Unit
                        override fun onVolumeDb(volumeDb: Float) = Unit
                        override fun onFinish(msg: String) {
                            main.post {
                                if (!alive || id != attempt || (state != RecordingState.RECORDING && state != RecordingState.EVALUATING)) return@post
                                // Invalidate before awarding: duplicate/late SDK events cannot award twice.
                                attempt++
                                release(false)
                                val parsed = parseGroupedWordEvaluation(msg, targets)
                                if (parsed.all { it.uncertain }) {
                                    state = RecordingState.ERROR
                                    message = "这次没听清，请重试"
                                    return@post
                                }
                                attempted = true
                                val diagnostics = prepareGroupedWordDiagnostics(msg, targets)
                                targets.zip(parsed).forEachIndexed { index, (target, result) ->
                                    val key = target.practiceProgressKey()
                                    results[key] = result
                                    val diagnostic = diagnostics[index]
                                    // A diagnostic is a side channel, never evidence for awarding a star.
                                    // Only the parser's definite errors, in this original word's coordinates, qualify.
                                    if (!result.uncertain && !result.passed && diagnostic != null &&
                                        result.wrongCharacterIndexes.isNotEmpty() &&
                                        result.wrongCharacterIndexes.all {
                                            it in target.displayText.indices && target.displayText[it] in '\u4e00'..'\u9fff'
                                        }) {
                                        scope.launch {
                                            viewModel.recordFailedDiagnostic(
                                                target, java.util.UUID.randomUUID().toString(),
                                                result.wrongCharacterIndexes, reference.textMode,
                                                target.displayText.length, diagnostic.words, diagnostic.reasons,
                                                diagnostic.raw, diagnostic.rawStatus
                                            )
                                        }
                                    }
                                    if (result.passed && currentItems.first { it.first.practiceProgressKey() == key }.second < target.requiredCorrectReadings()) {
                                        award(target, 1)
                                    }
                                }
                                state = RecordingState.FINISHED
                                message = if (pending().isEmpty()) "太棒啦，这组词读完了！" else "已保留读对的词，只补剩下的词"
                            }
                        }
                        override fun onError(request: OralEvaluationRequest?, clientException: ClientException?, serverException: ServerException?, response: String?) {
                            main.post {
                                if (!alive || id != attempt) return@post
                                attempt++
                                release(false)
                                state = RecordingState.ERROR
                                message = "评测连接失败，请重试"
                            }
                        }
                    }
                    val recordListener = object : OralEvaluationStateListener {
                        override fun onStartRecord(request: OralEvaluationRequest?) = Unit
                        override fun onStopRecord(request: OralEvaluationRequest?) = Unit
                        override fun onAudioData(audioData: ShortArray?, readBufferLength: Int) = Unit
                    }
                    controller = TAIOralController(TAIConfig.Builder()
                        .appID(credentials.appId).secretID(credentials.secretId)
                        .secretKey(credentials.secretKey).token(credentials.token)
                        .apiParams(params).enableVAD(false).dataSource(TAIDataSource(false)).build())
                    state = RecordingState.RECORDING
                    message = "正在录音：${targets.joinToString("、") { it.displayText }}"
                    controller?.startOralEvaluation(listener, recordListener)
                } catch (error: Exception) {
                    if (!alive || id != attempt) return@launch
                    release(true)
                    state = RecordingState.ERROR
                    message = error.message ?: "准备失败，请重试"
                }
            }
        }
        DisposableEffect(active) {
            alive = active
            onDispose {
                alive = false
                attempt++
                release(true)
            }
        }
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, active) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP && active) {
                    attempt++
                    release(true)
                    state = RecordingState.ERROR
                    message = "录音已取消，请重新开始"
                    stateChanged(state)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        LaunchedEffect(active) {
            if (active) begin()
        }
        LaunchedEffect(active, state) {
            if (active) stateChanged(state)
        }
        val busy = active && state in listOf(RecordingState.PREPARING, RecordingState.RECORDING, RecordingState.EVALUATING)
        GroupedWordPracticeRow(
            items = items, state = state, active = active,
            otherRecordingActive = otherRecordingActive, attempted = attempted,
            message = message, results = results, highlighted = highlighted,
            onLongPress = { target, character, index ->
                if (!busy && !otherRecordingActive) {
                    val key = target.practiceProgressKey()
                    highlighted.clear()
                    highlighted[key] = index
                    context.sendBroadcast(Intent(ACTION_LITERACY_READING_CONTENT_CLICKED)
                        .setPackage(context.packageName)
                        .putExtra(EXTRA_READING_TARGET_TYPE, "word")
                        .putExtra(EXTRA_READING_CONTENT, target.displayText)
                        .putExtra(EXTRA_READING_CLICKED_CHARACTER, character.toString())
                        .putExtra(EXTRA_READING_CHARACTER_INDEX, index))
                    onSpeak(target, target.displayText)
                    scope.launch { viewModel.recordHelpRequest(target, character, index) }
                }
            },
            onAction = {
                if (!active) onStart()
                else if (state == RecordingState.RECORDING) {
                    state = RecordingState.EVALUATING
                    message = "正在生成评测结果…"
                    val id = attempt
                    runCatching { controller?.stopOralEvaluation() }.onFailure {
                        attempt++
                        release(true)
                        state = RecordingState.ERROR
                        message = "停止录音失败，请重试"
                    }
                    main.postDelayed({
                        if (alive && attempt == id && state == RecordingState.EVALUATING) {
                            attempt++
                            release(true)
                            state = RecordingState.ERROR
                            message = "评测结果未返回，请重试"
                        }
                    }, 15_000)
                } else begin()
            }
        )
    }
}
