package com.lemonkids.parent.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.shared.model.BadgeCatalog
import com.lemonkids.shared.model.BadgeProgress
import com.lemonkids.shared.model.BadgeSpec
import com.lemonkids.shared.repository.AuthRepository
import com.lemonkids.shared.repository.BadgeProgressRepository
import com.lemonkids.shared.repository.ChildUserInfo
import com.lemonkids.shared.repository.impl.BadgeProgressConflict
import com.lemonkids.shared.repository.impl.BadgeProgressSaveUnconfirmed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class BadgeProgressUiState(
    val children: List<ChildUserInfo> = emptyList(),
    val selectedChildId: String? = null,
    val records: List<BadgeProgress> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saveError: String? = null
) {
    fun record(key: String, segment: String = ""): BadgeProgress? = records.firstOrNull { it.badgeKey == key && it.segment == segment }
    fun englishValues(): Map<String, Int> = records.filter { it.badgeKey == "english_reading" }.associate { it.segment to it.value }
}

@HiltViewModel
class BadgeProgressViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val progressRepository: BadgeProgressRepository
) : ViewModel() {
    private val mutableState = MutableStateFlow(BadgeProgressUiState())
    val state: StateFlow<BadgeProgressUiState> = mutableState.asStateFlow()
    init { viewModelScope.launch { loadChildren() } }

    private suspend fun loadChildren() {
        val familyId = authRepository.observeCurrentUser().first()?.familyId
        if (familyId == null) {
            mutableState.value = mutableState.value.copy(isLoading = false, error = "未找到家庭，请重新登录")
            return
        }
        authRepository.fetchChildUsers(familyId).fold(
            onSuccess = { children ->
                mutableState.value = mutableState.value.copy(children = children, isLoading = false)
                children.firstOrNull()?.let { selectChild(it.uid) }
            },
            onFailure = { mutableState.value = mutableState.value.copy(isLoading = false, error = "读取孩子列表失败，请重试") }
        )
    }

    fun selectChild(childId: String) {
        if (mutableState.value.isSaving || mutableState.value.children.none { it.uid == childId }) return
        mutableState.value = mutableState.value.copy(selectedChildId = childId, records = emptyList(), isLoading = true, error = null, saveError = null)
        viewModelScope.launch { reload(childId) }
    }

    fun refresh() {
        val childId = mutableState.value.selectedChildId ?: return
        mutableState.value = mutableState.value.copy(isLoading = true, error = null, saveError = null)
        viewModelScope.launch { reload(childId) }
    }

    private suspend fun reload(childId: String) {
        progressRepository.getForChild(childId).fold(
            onSuccess = { if (mutableState.value.selectedChildId == childId) mutableState.value = mutableState.value.copy(records = it, isLoading = false, error = null) },
            onFailure = { if (mutableState.value.selectedChildId == childId) mutableState.value = mutableState.value.copy(isLoading = false, error = "读取进度失败，请重试") }
        )
    }

    fun save(key: String, segment: String, value: Int, onSuccess: () -> Unit) {
        val childId = mutableState.value.selectedChildId ?: return
        if (!BadgeCatalog.validate(key, segment, value) || mutableState.value.isSaving ) return
        val old = mutableState.value.record(key, segment)
        if (old?.value == value || (old == null && value == 0)) { onSuccess(); return }
        mutableState.value = mutableState.value.copy(isSaving = true, saveError = null)
        viewModelScope.launch {
            progressRepository.setAbsolute(childId, key, segment, value, old?.version ?: 0).fold(
                onSuccess = { result ->
                    if (mutableState.value.selectedChildId == childId) {
                        mutableState.value = mutableState.value.copy(isSaving = false, saveError = null, records = mutableState.value.records.filterNot { it.badgeKey == key && it.segment == segment } + result)
                        onSuccess()
                    }
                },
                onFailure = { failure ->
                    val latest = (failure as? BadgeProgressConflict)?.current
                    val records = if (failure is BadgeProgressConflict) {
                        mutableState.value.records.filterNot { it.badgeKey == key && it.segment == segment } + listOfNotNull(latest)
                    } else mutableState.value.records
                    mutableState.value = mutableState.value.copy(isSaving = false, records = records, saveError = badgeSaveErrorMessage(failure))
                }
            )
        }
    }

    fun clearError() { mutableState.value = mutableState.value.copy(error = null, saveError = null) }
}

internal fun badgeSaveErrorMessage(error: Throwable): String = when (error) {
    is BadgeProgressConflict -> "进度已在其他地方更新，当前为 ${error.current?.value ?: 0}。请核对后再保存"
    is BadgeProgressSaveUnconfirmed -> "保存结果暂时无法确认，请刷新后核对"
    else -> "保存失败，请检查网络或登录状态后重试"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgeProgressScreen(onBack: () -> Unit, viewModel: BadgeProgressViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var editing by remember { mutableStateOf<Pair<BadgeSpec, String>?>(null) }
    var details by remember { mutableStateOf<BadgeSpec?>(null) }
    var englishExpanded by remember { mutableStateOf(false) }
    val englishValues = state.englishValues()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column {
            TopAppBar(title = { Text("勋章进度") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = { TextButton(onClick = viewModel::refresh, enabled = !state.isSaving) { Text("刷新") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp))
            if (state.children.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.children.forEach { child ->
                        FilterChip(selected = state.selectedChildId == child.uid, onClick = {
                            editing = null; details = null; englishExpanded = false; viewModel.selectChild(child.uid)
                        }, label = { Text(child.name) })
                    }
                }
            }
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(32.dp)) }
                state.error != null -> Column(Modifier.padding(20.dp)) {
                    Text(state.error ?: "读取失败", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { viewModel.clearError(); viewModel.refresh() }) { Text("重新读取") }
                }
                state.selectedChildId == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("请先在家庭管理中添加孩子") }
                else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text("输入累计完成总数，可调低纠错。专项勋章只依据家长记录。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    BadgeCatalog.manual.groupBy { it.group }.forEach { (group, specs) ->
                        item { Text(group, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
                        items(specs, key = { it.key }) { spec ->
                            if (spec.key == "english_reading") {
                                Card(Modifier.fillMaxWidth().clickable { englishExpanded = !englishExpanded }) {
                                    Column(Modifier.padding(16.dp)) {
                                        val level = BadgeCatalog.englishLevel(englishValues)
                                        Text("英语阅读 · ${if (level == 0) "尚未点亮" else "Lv.$level ${BadgeCatalog.englishName(level - 1)}"}", fontWeight = FontWeight.Bold)
                                        Text("已计入 ${BadgeCatalog.englishCountedTotal(englishValues)} / 2049 篇 · 点开逐级录入", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                            TextButton(onClick = { details = spec }) { Text("查看详情") }
                                        }
                                        if (englishExpanded) BadgeCatalog.englishGoals.forEachIndexed { index, goal ->
                                            val letter = BadgeCatalog.englishLetter(index)
                                            Row(Modifier.fillMaxWidth().clickable { editing = spec to letter }.padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("${BadgeCatalog.englishName(index)}（$letter 级）")
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text("${englishValues[letter] ?: 0} / $goal 篇")
                                                    state.record(spec.key, letter)?.let { Text(formatBadgeTime(it.updatedAt), style = MaterialTheme.typography.bodySmall) }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                val value = state.record(spec.key)?.value ?: 0
                                Card(Modifier.fillMaxWidth().clickable { editing = spec to "" }) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text("${spec.title} · ${if (value == 0) "尚未点亮" else "Lv.${spec.level(value)} ${spec.stageName(value)}"}", fontWeight = FontWeight.Bold)
                                        Text("已记录 $value ${spec.unit} · ${when {
                                            spec.isComplete(value) -> "目标已完成"
                                            spec.nextThreshold(value) != null -> "下一级 ${spec.nextThreshold(value)} ${spec.unit}"
                                            else -> "六年目标 ${spec.graduationGoal} ${spec.unit}"
                                        }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        state.record(spec.key)?.let { Text("更新于 ${formatBadgeTime(it.updatedAt)}", style = MaterialTheme.typography.bodySmall) }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                            TextButton(onClick = { details = spec }) { Text("查看详情") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { Text("高效英语、思维火箭：后续开放。任务担当者、坚持足迹按任务统计。", modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
    details?.let { spec -> BadgeLevelDetailsDialog(spec, onDismiss = { details = null }) }
    editing?.let { (spec, segment) ->
        val old = state.record(spec.key, segment)?.value ?: 0
        val goal = if (segment.isNotEmpty()) BadgeCatalog.englishGoals[BadgeCatalog.englishIndex(segment)!!] else spec.limit
        var input by remember(state.selectedChildId, spec.key, segment) { mutableStateOf(old.toString()) }
        val value = input.toIntOrNull()
        val valid = value != null && BadgeCatalog.validate(spec.key, segment, value)
        val label = if (segment.isEmpty()) spec.title else "${BadgeCatalog.englishName(BadgeCatalog.englishIndex(segment)!!)}（$segment 级）"
        AlertDialog(onDismissRequest = { if (!state.isSaving) editing = null },
            title = { Text(label) },
            text = { Column {
                Text("当前 $old ${spec.unit}${goal?.let { " · 上限 $it ${spec.unit}" } ?: ""}")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(input, { if (it.isEmpty() || it.all(Char::isDigit)) input = it }, label = { Text("累计完成数量") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (value != null) Text("$old → $value ${spec.unit}${if (segment.isEmpty()) " · ${spec.stageName(old)} → ${spec.stageName(value)}" else ""}")
                if (!valid) Text("请输入 0${goal?.let { "～$it" } ?: " 以上的整数"}", color = MaterialTheme.colorScheme.error)
                state.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(onClick = { value?.let { viewModel.save(spec.key, segment, it) { editing = null } } }, enabled = valid && !state.isSaving) {
                if (state.isSaving) CircularProgressIndicator(Modifier.size(16.dp)) else Text("保存")
            } },
            dismissButton = { TextButton(onClick = { editing = null; viewModel.clearError() }, enabled = !state.isSaving) { Text("取消") } })
    }
}

private fun formatBadgeTime(value: String): String = runCatching {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.of("Asia/Shanghai")).format(Instant.parse(value))
}.getOrElse { value }
