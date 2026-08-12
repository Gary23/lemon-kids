package com.lemonkids.kidmonitor.feature.usage

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun UsageDetailScreen(
    onBack: () -> Unit,
    onAppClick: (packageName: String, startDate: String, endDate: String, appName: String) -> Unit,
    onDayAppClick: (packageName: String, appName: String) -> Unit = { _, _ -> },
    viewModel: UsageDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("使用详情", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新")
            }
        }

        TabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
            Tab(
                selected = uiState.selectedTab == UsageTab.DAY,
                onClick = { viewModel.selectTab(UsageTab.DAY) },
                text = { Text("日视图") }
            )
            Tab(
                selected = uiState.selectedTab == UsageTab.WEEK,
                onClick = { viewModel.selectTab(UsageTab.WEEK) },
                text = { Text("周视图") }
            )
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        when (uiState.selectedTab) {
            UsageTab.DAY -> DayViewContent(viewModel, uiState.dayData, uiState.dayDateLabel, uiState.yearLabel, uiState.isLoadingHourly, onDayAppClick)
            UsageTab.WEEK -> WeekViewContent(viewModel, uiState.weekData, uiState.selectedPeriod.label, onAppClick)
        }
    }
}

// ==================== 日视图 ====================

@Composable
private fun DayViewContent(
    viewModel: UsageDetailViewModel,
    data: DayViewData,
    dateLabel: String,
    yearLabel: String,
    isLoadingHourly: Boolean,
    onAppClick: (packageName: String, appName: String) -> Unit = { _, _ -> }
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 日期选择器
        item {
            Text(yearLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.shiftDay(false) }) {
                    Icon(Icons.Filled.ChevronLeft, "前一天")
                }
                Text(dateLabel, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                IconButton(onClick = { viewModel.shiftDay(true) }) {
                    Icon(Icons.Filled.ChevronRight, "后一天")
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("今日总使用时长", fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(formatMinutes(data.totalMinutes), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }

        // 整点使用时长柱状图
        item {
            if (isLoadingHourly) {
                Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (data.hourlyUsages.isNotEmpty()) {
                Column {
                    Text("全天使用时段", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    TooltipBarChart(data = data.hourlyUsages.map { BarItem(it.label, it.minutes) }, modifier = Modifier.fillMaxWidth().height(160.dp))
                }
            }
        }

        // 各应用使用时长
        if (data.appUsages.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("暂无使用记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            item {
                Text("今日各应用使用时长", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            items(data.appUsages) { app ->
                val maxMin = data.appUsages.firstOrNull()?.minutes?.toFloat() ?: 1f
                val ratio = if (maxMin > 0) (app.minutes.toFloat() / maxMin) else 0f
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onAppClick(app.packageName, app.appName)
                    }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(app.appName, fontWeight = FontWeight.Medium)
                            Text(formatMinutes(app.minutes), color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth(ratio.coerceIn(0.02f, 1f)).height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== 周视图 ====================

@Composable
private fun WeekViewContent(
    viewModel: UsageDetailViewModel,
    data: WeekViewData,
    title: String,
    onAppClick: (packageName: String, startDate: String, endDate: String, appName: String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 标题行：左箭头 + 标题 + 右箭头
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.shiftPeriod(false) }) {
                    Icon(Icons.Filled.ChevronLeft, "更早")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${title}使用时长", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatMinutes(data.totalMinutes), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { viewModel.shiftPeriod(true) }) {
                    Icon(Icons.Filled.ChevronRight, "更晚")
                }
            }
        }

        // 柱状图
        item {
            if (data.dailyTotals.isNotEmpty()) {
                TooltipBarChart(
                    data = data.dailyTotals.map { BarItem(it.dayLabel, it.minutes) },
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                )
            }
        }

        // 分隔+应用列表标题
        item {
            HorizontalDivider()
            Spacer(modifier = Modifier.height(4.dp))
            Text("应用使用情况", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        if (data.appBreakdowns.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("暂无使用记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(data.appBreakdowns) { app ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onAppClick(app.packageName, data.startDate, data.endDate, app.appName)
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.appName, fontWeight = FontWeight.Medium)
                            Text(formatMinutes(app.totalMinutes), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(">", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ==================== 通用柱状图（现代风格，含动画和浮窗，跟随应用主题色） ====================

data class BarItem(
    val label: String,
    val value: Long
)

@Composable
fun TooltipBarChart(
    data: List<BarItem>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val maxValue = data.maxOfOrNull { it.value }?.toFloat()?.coerceAtLeast(1f) ?: 1f
    val density = LocalDensity.current
    var chartSize by remember { mutableStateOf(IntSize.Zero) }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    val scrollState = rememberScrollState()

    Column(modifier = modifier.onSizeChanged { chartSize = it }) {
        val barAreaHeight = chartSize.height.toFloat() * 0.78f

        // 柱状图区域
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.78f)
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            data.forEachIndexed { idx, item ->
                val isSelected = idx == selectedIndex
                val barHeightFraction = item.value.toFloat() / maxValue
                val targetBarHeight = barAreaHeight * barHeightFraction
                val animatedHeight by animateFloatAsState(
                    targetValue = targetBarHeight.coerceAtLeast(4f),
                    animationSpec = tween(durationMillis = 600, delayMillis = idx * 30),
                    label = "bar$idx"
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(56.dp)
                        .clickable { selectedIndex = if (isSelected) -1 else idx }
                ) {
                    // 悬浮数值标签
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .shadow(4.dp, RoundedCornerShape(6.dp))
                                .background(tertiaryColor, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                formatMinutes(item.value),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // 柱子
                    Box(
                        modifier = Modifier
                            .width(if (isSelected) 32.dp else 24.dp)
                            .height(with(density) { animatedHeight.toDp() })
                            .shadow(
                                elevation = if (isSelected) 6.dp else 2.dp,
                                shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                            )
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(
                                brush = if (isSelected) Brush.verticalGradient(
                                    colors = listOf(secondaryColor, tertiaryColor)
                                ) else Brush.verticalGradient(
                                    colors = listOf(primaryColor, primaryContainer)
                                )
                            )
                    )
                }
            }
        }

        // 底部标签区域
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.22f)
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            data.forEachIndexed { idx, item ->
                Box(
                    modifier = Modifier.width(56.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        item.label,
                        fontSize = 10.sp,
                        color = if (idx == selectedIndex) onSurface else onSurfaceVariant,
                        fontWeight = if (idx == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
