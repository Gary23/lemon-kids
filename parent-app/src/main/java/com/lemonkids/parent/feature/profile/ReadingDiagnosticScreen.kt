package com.lemonkids.parent.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.lemonkids.shared.repository.AuthRepository
import com.lemonkids.shared.repository.ChildUserInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.Auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private const val FUNCTION_URL = "https://1255826305-i7udpf1i9o.ap-beijing.tencentscf.com"
private val DIAGNOSTIC_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    .withZone(ZoneId.systemDefault())

private fun displayDiagnosticTime(value: String): String = runCatching {
    DIAGNOSTIC_TIME_FORMAT.format(Instant.parse(value))
}.getOrDefault(value)

private fun JsonObject.string(key: String): String = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun diagnosticType(value: String) = when (value) {
    "character" -> "字"; "word" -> "词"; "sentence" -> "句"; else -> value.ifBlank { "未知类型" }
}

private fun diagnosticReason(value: String) = when (value) {
    "missing_words" -> "没有可用的腾讯逐项结果"
    "low_pron_accuracy" -> "至少一项发音准确度低于 60（含特殊值 -1）"
    "match_tag_fallback" -> "没有可用分数，依据腾讯匹配标记判定"
    "unreturned_expected_characters" -> "返回的汉字少于目标文本"
    "mapping_uncertain" -> "返回内容与目标文本难以对齐"
    else -> "未收录原因：$value"
}

private fun JsonObject.failedCharacters(): String {
    val target = string("targetText")
    val indexes = (this["wrongCharacterIndexes"] as? JsonArray).orEmpty()
        .mapNotNull { it.jsonPrimitive.contentOrNull?.toIntOrNull() }
    if (indexes.isEmpty()) return "未记录标红位置"
    return indexes.distinct().joinToString("、") { index ->
        target.getOrNull(index)?.let { "${it}（位置 ${index + 1}）" } ?: "位置 ${index + 1}（超出原文）"
    }
}

private fun JsonObject.reasons(): String = (this["failureReason"] as? JsonArray).orEmpty()
    .mapNotNull { it.jsonPrimitive.contentOrNull }.takeIf { it.isNotEmpty() }
    ?.joinToString("；") { diagnosticReason(it) } ?: "未记录原因"

private fun JsonObject.tencentWordSummary(): String {
    val word = string("Word").ifBlank { "未提供" }
    val accuracy = string("PronAccuracy")
    val accuracyText = when (accuracy) {
        "" -> "未提供"
        "-1", "-1.0" -> "-1（完全不匹配）"
        else -> accuracy
    }
    val match = string("MatchTag")
    val matchText = when (match) {
        "0" -> "0（匹配）"; "1" -> "1（新增）"; "2" -> "2（缺少）"
        "3" -> "3（错读）"; "4" -> "4（未录入）"
        "" -> "未提供"; else -> "$match（未收录含义）"
    }
    return "腾讯逐项：$word｜发音准确度：$accuracyText｜匹配标记：$matchText"
}

data class DiagnosticUiState(
    val children: List<ChildUserInfo> = emptyList(),
    val child: ChildUserInfo? = null,
    val records: List<JsonObject> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val refreshedAt: String? = null
)

@HiltViewModel
class ReadingDiagnosticViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val supabase: SupabaseClient
) : androidx.lifecycle.ViewModel() {
    private val _state = MutableStateFlow(DiagnosticUiState())
    val state = _state.asStateFlow()
    private var requestJob: Job? = null
    private var generation = 0

    private var childrenJob: Job? = null
    init { loadChildren() }

    private fun loadChildren() {
        childrenJob?.cancel()
        childrenJob = viewModelScope.launchProfileLoad(onError = {
            _state.value = _state.value.copy(loading = false, error = "加载孩子列表失败，请重试")
        }) {
            val familyId = authRepository.observeCurrentUser().first()?.familyId
            if (familyId.isNullOrBlank()) {
                _state.value = _state.value.copy(loading = false, error = "未找到家庭信息")
                return@launchProfileLoad
            }
            authRepository.fetchChildUsers(familyId).fold(
                onSuccess = { children ->
                    _state.value = _state.value.copy(children = children, child = children.firstOrNull(), loading = false, error = null)
                    if (children.isNotEmpty()) refresh()
                },
                onFailure = {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    _state.value = _state.value.copy(loading = false, error = "加载孩子列表失败")
                }
            )
        }
    }

    fun selectChild(child: ChildUserInfo) {
        generation++
        requestJob?.cancel()
        _state.value = _state.value.copy(child = child, records = emptyList(), error = null, refreshedAt = null)
        refresh()
    }

    fun refresh() {
        val childId = _state.value.child?.uid ?: run { loadChildren(); return }
        if (requestJob?.isActive == true) return
        val ticket = generation
        requestJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            runCatching { query(childId) }.fold(
                onSuccess = { records ->
                    if (ticket == generation) _state.value = _state.value.copy(records = records, loading = false,
                        error = null, refreshedAt = displayDiagnosticTime(Instant.now().toString()))
                },
                onFailure = { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    if (ticket == generation) _state.value = _state.value.copy(loading = false,
                        error = error.message ?: "查询失败，请重试")
                }
            )
        }
    }

    private suspend fun query(childId: String): List<JsonObject> = withContext(Dispatchers.IO) {
        val token = supabase.pluginManager.getPlugin(Auth).currentSessionOrNull()?.accessToken
            ?: error("登录已过期")
        val connection = (URL(FUNCTION_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5000
            readTimeout = 10000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            connection.outputStream.use { it.write(Json.encodeToString(JsonObject.serializer(),
                JsonObject(mapOf("action" to kotlinx.serialization.json.JsonPrimitive("list_failed_reading_diagnostics"),
                    "childId" to kotlinx.serialization.json.JsonPrimitive(childId)))).toByteArray()) }
            val code = connection.responseCode
            val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val result = Json.parseToJsonElement(raw).jsonObject
            if (code !in 200..299) error(result["error"]?.jsonPrimitive?.contentOrNull ?: "服务器查询失败")
            result["records"]?.jsonArray?.mapNotNull { it as? JsonObject }.orEmpty()
        } finally { connection.disconnect() }
    }
}

@Composable
fun ReadingDiagnosticScreen(onBack: () -> Unit, onGuideClick: () -> Unit,
                            viewModel: ReadingDiagnosticViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresh()
            while (true) { delay(15_000); viewModel.refresh() }
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                Text("认字诊断记录")
                IconButton(onClick = viewModel::refresh) { Icon(Icons.Filled.Refresh, contentDescription = "刷新") }
            }
        }
        item { Text("近 24 小时最多 50 条；每 15 秒刷新。日志投递可能稍后出现。") }
        item { Text("腾讯返回的字和评分不代表孩子实际说出的内容。") }
        item { TextButton(onClick = onGuideClick) { Text("字段说明：查看腾讯官方字段和数值含义") } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.children.forEach { child ->
                    FilterChip(selected = child.uid == state.child?.uid, onClick = { viewModel.selectChild(child) }, label = { Text(child.name) })
                }
            }
        }
        state.refreshedAt?.let { item { Text("上次刷新：$it") } }
        if (state.loading) item { Text("正在查询…") }
        state.error?.let { item { Text(it) } }
        if (!state.loading && state.error == null && state.child != null && state.records.isEmpty()) item { Text("暂无未通过的朗读诊断记录") }
        if (state.child == null && !state.loading) item { Text("请先添加孩子") }
        items(state.records, key = { it["attemptId"]?.jsonPrimitive?.contentOrNull.orEmpty() }) { record ->
            var expanded by remember { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    fun value(key: String) = record.string(key)
                    Text("${displayDiagnosticTime(value("time"))} · ${diagnosticType(value("targetType"))} · ${value("targetText")}", fontWeight = FontWeight.Bold)
                    Text("标红：${record.failedCharacters()}")
                    Text("柠檬认字判定：${record.reasons()}")
                    if (expanded) {
                        Text("本次记录编号：${value("attemptId")}；评测模式：${value("textMode")}")
                        val words = record["words"] as? JsonArray
                        if (words.isNullOrEmpty()) Text("腾讯逐项：没有可用数据；不能据此判断孩子是否开口。")
                        words?.forEach { word ->
                            val item = word as? JsonObject
                            if (item != null) {
                                Text(item.tencentWordSummary())
                                val extras = listOf("PronFluency" to "流利度", "PronCompletion" to "完整度", "TotalScore" to "整体得分")
                                    .mapNotNull { (key, label) -> item.string(key).takeIf { it.isNotBlank() }?.let { "$label：$it" } }
                                if (extras.isNotEmpty()) Text(extras.joinToString("；"))
                                Text("腾讯逐项原值：$item")
                            }
                        }
                        val raw = record["rawEvaluation"] as? JsonObject
                        val status = value("rawEvaluationStatus")
                        Text("腾讯评测原文：${when (status) {
                            "complete" -> "完整保存"
                            "redacted" -> "已剔除凭证或音频等内容"
                            "too_large" -> "原文过大，未保存"
                            "invalid_json" -> "返回内容不是有效 JSON，未保存"
                            else -> "旧记录未保存原文或原文不可用"
                        }}")
                        if (raw != null) {
                            val pretty = remember(raw) { Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), raw) }
                            TextButton(onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("腾讯评测原文", pretty))
                            }) { Text("复制腾讯评测原文") }
                            Text(pretty)
                        }
                    }
                }
            }
        }
    }
}
