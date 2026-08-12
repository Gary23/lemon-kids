package com.lemonkids.kidtask.feature.plan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.kidtask.ui.theme.Cream
import com.lemonkids.kidtask.ui.theme.InkBrown
import com.lemonkids.kidtask.ui.theme.Lavender
import com.lemonkids.kidtask.ui.theme.LavenderSoft
import com.lemonkids.kidtask.ui.theme.MutedGray
import com.lemonkids.kidtask.ui.theme.Pink
import com.lemonkids.kidtask.ui.theme.Sunny
import com.lemonkids.shared.model.TaskStatus
import com.lemonkids.shared.repository.AuthRepository
import com.lemonkids.shared.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class PlanUiState(
    val dateGroups: List<DateGroup> = emptyList(),
    val expandedDates: Set<String> = emptySet(),
    val isLoading: Boolean = false
)

data class DateGroup(
    val date: String,
    val display: String,
    val tasks: List<PlanTaskItem>
)

data class PlanTaskItem(
    val id: String,
    val title: String,
    val rewardPoints: Int,
    val dueTime: String?
)

@HiltViewModel
class PlanViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlanUiState(isLoading = true))
    val uiState: StateFlow<PlanUiState> = _uiState.asStateFlow()

    init {
        loadPlan()
    }

    private fun loadPlan() {
        viewModelScope.launch {
            val userId = authRepository.currentUserId ?: return@launch
            taskRepository.observeChildTasks(userId).collect { tasks ->
                val today = LocalDate.now()
                val dayOfWeek = today.dayOfWeek.value
                val endOfWeek = if (dayOfWeek >= 6) {
                    today.plusDays(((7 - dayOfWeek) + 7).toLong())
                } else {
                    today.plusDays((7 - dayOfWeek).toLong())
                }

                val grouped = tasks
                    .filter { task ->
                        try {
                            val d = LocalDate.parse(task.dueDate)
                            d > today && !d.isAfter(endOfWeek) && task.status == TaskStatus.PENDING
                        } catch (_: Exception) { false }
                    }
                    .groupBy { it.dueDate }
                    .mapValues { (_, list) ->
                        list.map {
                            PlanTaskItem(id = it.id, title = it.title, rewardPoints = it.rewardPoints, dueTime = it.dueTime)
                        }
                    }
                    .entries
                    .sortedBy { it.key }
                    .map { (date, tasks) ->
                        val display = try {
                            val d = LocalDate.parse(date)
                            "${d.year}年${"%02d".format(d.monthValue)}月${"%02d".format(d.dayOfMonth)}日计划"
                        } catch (_: Exception) { date }
                        DateGroup(date = date, display = display, tasks = tasks)
                    }

                _uiState.value = _uiState.value.copy(isLoading = false, dateGroups = grouped)
            }
        }
    }

    fun toggleDateExpand(date: String) {
        val current = _uiState.value.expandedDates
        _uiState.value = _uiState.value.copy(
            expandedDates = if (date in current) current - date else current + date
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    onBack: () -> Unit,
    viewModel: PlanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = Cream) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("计划", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream),
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
            )

            if (uiState.isLoading && uiState.dateGroups.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(36.dp), color = Pink)
                }
            } else if (uiState.dateGroups.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🌈", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("暂无计划任务", fontSize = 16.sp, color = MutedGray)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    uiState.dateGroups.forEach { group ->
                        val isExpanded = group.date in uiState.expandedDates
                        item(key = group.date) {
                            DateFoldCard(
                                title = group.display,
                                tasks = group.tasks,
                                expanded = isExpanded,
                                onToggle = { viewModel.toggleDateExpand(group.date) }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun DateFoldCard(
    title: String,
    tasks: List<PlanTaskItem>,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = LavenderSoft.copy(alpha = 0.3f),
        shadowElevation = 4.dp
    ) {
        Column {
            // 折叠标题
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = InkBrown)
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = Lavender,
                    modifier = Modifier.size(24.dp)
                )
            }

            // 展开内容
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tasks.forEach { task ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    task.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = InkBrown,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Star, contentDescription = null, tint = Sunny, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text(
                                        "${task.rewardPoints}",
                                        fontSize = 14.sp,
                                        color = Pink,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (!task.dueTime.isNullOrEmpty()) {
                                        Spacer(Modifier.width(12.dp))
                                        Icon(Icons.Filled.Schedule, contentDescription = null, tint = MutedGray, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(2.dp))
                                        Text(task.dueTime, fontSize = 13.sp, color = MutedGray)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}