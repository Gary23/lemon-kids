package com.lemonkids.kidmonitor.feature.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.shared.model.AppUsageRecord
import com.lemonkids.shared.repository.AppUsageRepository
import com.lemonkids.shared.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject

fun formatMinutes(minutes: Long): String {
    if (minutes <= 0) return "0分钟"
    if (minutes < 60) return "${minutes}分钟"
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return if (remainingMinutes == 0L) "${hours}小时" else "${hours}小时${remainingMinutes}分钟"
}

data class HourlyUsage(
    val hour: Int,
    val label: String,
    val minutes: Long
)

sealed class WeekPeriod {
    abstract val label: String
    abstract val startDate: LocalDate
    abstract val endDate: LocalDate

    object CURRENT_WEEK : WeekPeriod() {
        override val label = "本周"
        override val startDate: LocalDate
            get() = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        override val endDate: LocalDate
            get() = startDate.plusDays(6)
    }

    object LAST_7_DAYS : WeekPeriod() {
        override val label = "近7天"
        override val startDate: LocalDate
            get() = LocalDate.now().minusDays(6)
        override val endDate: LocalDate
            get() = LocalDate.now()
    }

    object LAST_WEEK : WeekPeriod() {
        override val label = "上周"
        override val startDate: LocalDate
            get() = LocalDate.now()
                .with(TemporalAdjusters.previous(DayOfWeek.MONDAY))
                .minusWeeks(1)
        override val endDate: LocalDate
            get() = startDate.plusDays(6)
    }

    data class HISTORY_WEEK(
        override val startDate: LocalDate,
        override val endDate: LocalDate,
        override val label: String
    ) : WeekPeriod()
}

data class UsageDetailUiState(
    val isLoading: Boolean = true,
    val isLoadingHourly: Boolean = false,
    val selectedTab: UsageTab = UsageTab.DAY,
    val selectedPeriod: WeekPeriod = WeekPeriod.CURRENT_WEEK,
    val dayDate: LocalDate = LocalDate.now(),
    val dayDateLabel: String = "今天",
    val yearLabel: String = "",
    val dayData: DayViewData = DayViewData(),
    val weekData: WeekViewData = WeekViewData()
)

enum class UsageTab { DAY, WEEK }

data class DayViewData(
    val totalMinutes: Long = 0,
    val appUsages: List<DayAppUsage> = emptyList(),
    val hourlyUsages: List<HourlyUsage> = emptyList()
)

data class DayAppUsage(
    val appName: String,
    val packageName: String,
    val minutes: Long
)

data class WeekViewData(
    val totalMinutes: Long = 0,
    val dailyTotals: List<DailyTotal> = emptyList(),
    val dailyAppUsages: Map<String, List<DayAppUsage>> = emptyMap(),
    val appBreakdowns: List<AppWeekBreakdown> = emptyList(),
    val startDate: String = "",
    val endDate: String = "",
    val title: String = ""
)

data class DailyTotal(
    val date: String,
    val dayLabel: String,
    val minutes: Long
)

data class AppWeekBreakdown(
    val appName: String,
    val packageName: String,
    val totalMinutes: Long,
    val dailyMinutes: Map<String, Long>
)

@HiltViewModel
class UsageDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appUsageRepository: AppUsageRepository,
    private val authRepository: AuthRepository,
    private val supabase: SupabaseClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(UsageDetailUiState())
    val uiState: StateFlow<UsageDetailUiState> = _uiState.asStateFlow()

    private val dateFormatter = DateTimeFormatter.ofPattern("MM/dd", Locale.getDefault())
    private val weekLabelFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.getDefault())
    private val yearMonthFormatter = DateTimeFormatter.ofPattern("yyyy年M月", Locale.getDefault())

    private val dayOfWeekLabels = mapOf(
        DayOfWeek.MONDAY to "一",
        DayOfWeek.TUESDAY to "二",
        DayOfWeek.WEDNESDAY to "三",
        DayOfWeek.THURSDAY to "四",
        DayOfWeek.FRIDAY to "五",
        DayOfWeek.SATURDAY to "六",
        DayOfWeek.SUNDAY to "日"
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val userId = authRepository.currentUserId ?: return@launch
            val today = LocalDate.now()

            loadDayData(userId, _uiState.value.dayDate)
            // 先同步上传当天数据到 Supabase，确保周视图能读到最新数据
            syncUploadCurrentUsage(userId, today)
            loadWeekDataForPeriod(userId, today, _uiState.value.selectedPeriod)
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun selectTab(tab: UsageTab) {
        if (tab == UsageTab.DAY) {
            val today = LocalDate.now()
            _uiState.value = _uiState.value.copy(
                selectedTab = tab,
                dayDate = today,
                dayDateLabel = "今天",
                yearLabel = today.format(yearMonthFormatter)
            )
        } else {
            _uiState.value = _uiState.value.copy(selectedTab = tab)
        }
    }

    fun shiftDay(forward: Boolean) {
        val today = LocalDate.now()
        val current = _uiState.value.dayDate
        val minDate = today.minusWeeks(1) // 边界：上周的今天

        val newDate = if (forward) current.plusDays(1) else current.minusDays(1)
        if (newDate.isAfter(today) || newDate.isBefore(minDate)) return

        val label = resolveDayLabel(newDate, today)
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            dayDate = newDate,
            dayDateLabel = label,
            yearLabel = newDate.format(yearMonthFormatter)
        )

        viewModelScope.launch {
            val userId = authRepository.currentUserId ?: return@launch
            loadDayData(userId, newDate)
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun selectPeriod(period: WeekPeriod) {
        val current = _uiState.value
        if (current.selectedPeriod == period) return
        _uiState.value = current.copy(isLoading = true)
        viewModelScope.launch {
            val userId = authRepository.currentUserId ?: return@launch
            val today = LocalDate.now()
            loadWeekDataForPeriod(userId, today, period)
            _uiState.value = _uiState.value.copy(
                selectedPeriod = period,
                isLoading = false
            )
        }
    }

    fun shiftPeriod(forward: Boolean) {
        val current = _uiState.value.selectedPeriod
        val periods = buildPeriodList()
        val currentIndex = periods.indexOfFirst {
            it.startDate == current.startDate && it.endDate == current.endDate
        }
        if (currentIndex < 0) return

        val newIndex = if (forward) currentIndex + 1 else currentIndex - 1
        if (newIndex < 0 || newIndex >= periods.size) return

        selectPeriod(periods[newIndex])
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun buildPeriodList(): List<WeekPeriod> {
        val today = LocalDate.now()
        val currentWeekMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val lastWeekMonday = currentWeekMonday.minusWeeks(1)
        val earliestMonday = currentWeekMonday.minusWeeks(12)

        val periods = mutableListOf<WeekPeriod>()

        var weekStart = earliestMonday
        while (weekStart < lastWeekMonday) {
            val weekEnd = weekStart.plusDays(6)
            val label = "${weekLabelFormatter.format(weekStart)}-${weekLabelFormatter.format(weekEnd)}"
            periods.add(WeekPeriod.HISTORY_WEEK(weekStart, weekEnd, label))
            weekStart = weekStart.plusWeeks(1)
        }

        periods.add(WeekPeriod.LAST_WEEK)
        periods.add(WeekPeriod.CURRENT_WEEK)
        periods.add(WeekPeriod.LAST_7_DAYS)

        return periods
    }

    private suspend fun loadDayData(userId: String, date: LocalDate) {
        if (!hasUsageStatsPermission()) {
            _uiState.value = _uiState.value.copy(dayData = DayViewData())
            return
        }

        // 直接从系统 UsageStatsManager 读取实时数据
        val items = withContext(Dispatchers.IO) { collectDayUsageItems(date) }

        val totalMinutes = items.sumOf { it.minutes }
        _uiState.value = _uiState.value.copy(
            dayData = DayViewData(
                totalMinutes = totalMinutes,
                appUsages = items,
                hourlyUsages = _uiState.value.dayData.hourlyUsages
            )
        )

        loadHourlyData(date)
    }

    /** 直接从系统 UsageStatsManager 查询当日各应用使用时长 */
    private fun collectDayUsageItems(date: LocalDate): List<DayAppUsage> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val startMs = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMs = System.currentTimeMillis()
        val statsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startMs, endMs
        )
        val pm = context.packageManager
        return statsList
            .filter { it.totalTimeInForeground > 0 }
            .map { stats ->
                val appName = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(stats.packageName, 0)).toString()
                }.getOrDefault(stats.packageName)
                DayAppUsage(
                    appName = appName,
                    packageName = stats.packageName,
                    minutes = stats.totalTimeInForeground / 60_000
                )
            }
            .sortedByDescending { it.minutes }
    }

    /** 同步上传当天使用数据，等待完成后再返回，供 refresh 使用确保周视图数据一致 */
    private suspend fun syncUploadCurrentUsage(userId: String, date: LocalDate) {
        val user = authRepository.observeCurrentUser().first() ?: return
        val familyId = user.familyId ?: return
        withContext(Dispatchers.IO) {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val startMs = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endMs = System.currentTimeMillis()
            val statsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startMs, endMs
            )
            val pm = context.packageManager
            val records = statsList
                .filter { it.totalTimeInForeground > 0 }
                .map { stats ->
                    val appName = runCatching {
                        pm.getApplicationLabel(pm.getApplicationInfo(stats.packageName, 0)).toString()
                    }.getOrDefault(stats.packageName)
                    AppUsageRecord(
                        familyId = familyId,
                        childId = userId,
                        packageName = stats.packageName,
                        appName = appName,
                        durationSeconds = stats.totalTimeInForeground / 1000,
                        date = date.toString()
                    )
                }
            val postgrest = supabase.pluginManager.getPlugin(Postgrest)
            runCatching {
                postgrest.from("app_usage").delete {
                    filter { eq("child_id", userId); eq("date", date.toString()) }
                }
            }
            if (records.isNotEmpty()) {
                appUsageRepository.uploadUsageRecords(records)
            }
        }
    }

    private suspend fun loadHourlyData(date: LocalDate) {
        if (!hasUsageStatsPermission()) return

        _uiState.value = _uiState.value.copy(isLoadingHourly = true)

        val hourlyUsages = withContext(Dispatchers.IO) {
            val usageStatsManager = context.getSystemService(
                Context.USAGE_STATS_SERVICE
            ) as UsageStatsManager

            val zone = ZoneId.systemDefault()
            val startMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val endMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

            val usageEvents = runCatching {
                usageStatsManager.queryEvents(startMs, endMs)
            }.getOrNull() ?: return@withContext (0..23).map { hour ->
                HourlyUsage(hour = hour, label = "${hour}:00", minutes = 0)
            }

            val hourMsBuckets = collectHourlyMinutes(usageEvents, startMs, endMs, zone)

            (0..23).map { hour ->
                HourlyUsage(
                    hour = hour,
                    label = "${hour}:00",
                    minutes = hourMsBuckets[hour] / 60_000
                )
            }
        }

        _uiState.value = _uiState.value.copy(
            isLoadingHourly = false,
            dayData = _uiState.value.dayData.copy(hourlyUsages = hourlyUsages)
        )
    }

    private fun collectHourlyMinutes(
        usageEvents: UsageEvents,
        dayStartMs: Long,
        dayEndMs: Long,
        zone: ZoneId
    ): LongArray {
        val hourMs = LongArray(24)

        data class ForegroundInterval(
            val startMs: Long,
            val endMs: Long
        )

        val intervals = mutableListOf<ForegroundInterval>()
        var currentPackage: String? = null
        var currentStartMs: Long = 0L

        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    if (currentPackage != null) {
                        intervals.add(ForegroundInterval(currentStartMs, event.timeStamp))
                    }
                    currentPackage = event.packageName
                    currentStartMs = event.timeStamp
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (currentPackage == event.packageName) {
                        intervals.add(ForegroundInterval(currentStartMs, event.timeStamp))
                        currentPackage = null
                    }
                }
            }
        }
        if (currentPackage != null) {
            intervals.add(ForegroundInterval(currentStartMs, dayEndMs))
        }

        for (interval in intervals) {
            val duration = interval.endMs - interval.startMs
            if (duration <= 0) continue

            val startInstant = Instant.ofEpochMilli(interval.startMs)
            val endInstant = Instant.ofEpochMilli(interval.endMs)
            val startHour = startInstant.atZone(zone).hour
            val endHour = endInstant.atZone(zone).hour

            if (startHour == endHour) {
                hourMs[startHour] += duration
            } else {
                val startOfNextHour = startInstant.atZone(zone)
                    .withMinute(0).withSecond(0).withNano(0)
                    .plusHours(1)
                    .toInstant().toEpochMilli()
                hourMs[startHour] += startOfNextHour - interval.startMs

                for (h in (startHour + 1) until endHour) {
                    hourMs[h] += 3_600_000L
                }

                val startOfEndHour = endInstant.atZone(zone)
                    .withMinute(0).withSecond(0).withNano(0)
                    .toInstant().toEpochMilli()
                hourMs[endHour] += interval.endMs - startOfEndHour
            }
        }

        return hourMs
    }

    private suspend fun loadWeekDataForPeriod(
        userId: String,
        today: LocalDate,
        period: WeekPeriod
    ) {
        if (!hasUsageStatsPermission()) {
            _uiState.value = _uiState.value.copy(weekData = WeekViewData())
            return
        }

        val startDate = period.startDate
        val endDate = period.endDate
        val records = appUsageRepository.getDateRangeUsage(
            userId, startDate.toString(), endDate.toString()
        )

        val recordsByDate = records.groupBy { it.date }
        val daysBetween = endDate.toEpochDay() - startDate.toEpochDay()

        // 今日数据优先从系统读取，保证与日视图一致
        val todaySystemItems = if (today in startDate..endDate) {
            withContext(Dispatchers.IO) { collectDayUsageItems(today) }
        } else emptyList()
        val todaySystemMinutes = todaySystemItems.sumOf { it.minutes }
        val todaySystemByApp = todaySystemItems.associateBy { it.packageName }

        val dailyTotals = (0..daysBetween).map { offset ->
            val date = startDate.plusDays(offset)
            val dateStr = date.toString()
            if (date == today) {
                DailyTotal(date = dateStr, dayLabel = resolveDayLabel(date, today), minutes = todaySystemMinutes)
            } else {
                val dayRecords = recordsByDate[dateStr].orEmpty()
                DailyTotal(
                    date = dateStr,
                    dayLabel = resolveDayLabel(date, today),
                    minutes = dayRecords.sumOf { it.durationSeconds } / 60
                )
            }
        }

        // 今日使用系统数据，过往日期使用 Supabase 数据
        val dailyAppUsages = recordsByDate.mapValues { (dateStr, dayRecords) ->
            val date = runCatching { LocalDate.parse(dateStr) }.getOrNull()
            if (date == today) {
                todaySystemItems
            } else {
                val grouped = dayRecords.groupBy { it.packageName }
                grouped.map { (_, list) ->
                    DayAppUsage(
                        appName = list.first().appName,
                        packageName = list.first().packageName,
                        minutes = list.sumOf { it.durationSeconds } / 60
                    )
                }.sortedByDescending { it.minutes }
            }
        }.toMutableMap()
        // 如果 Supabase 中没有今日记录，补上今日系统数据
        val todayStr = today.toString()
        if (todayStr !in dailyAppUsages && todaySystemItems.isNotEmpty()) {
            dailyAppUsages[todayStr] = todaySystemItems
        }

        val recordsByApp = records.groupBy { it.packageName }.toMutableMap()

        // 今日应用数据合并：系统数据覆盖 Supabase 中的今日数据
        if (todaySystemByApp.isNotEmpty()) {
            for ((pkg, systemItem) in todaySystemByApp) {
                val existing = recordsByApp[pkg]
                if (existing != null) {
                    // 已有该应用的历史记录，用今天的系统数据替换掉今天那条
                    val filtered = existing.filter { it.date != todayStr }
                    recordsByApp[pkg] = filtered + AppUsageRecord(
                        id = "", familyId = "", childId = userId,
                        packageName = pkg, appName = systemItem.appName,
                        durationSeconds = systemItem.minutes * 60,
                        date = todayStr
                    )
                }
            }
        }

        val appBreakdowns = recordsByApp.map { (_, list) ->
            val dailyMap = list.groupBy { it.date }
                .mapValues { it.value.sumOf { r -> r.durationSeconds } / 60 }
            AppWeekBreakdown(
                appName = list.first().appName,
                packageName = list.first().packageName,
                totalMinutes = list.sumOf { it.durationSeconds } / 60,
                dailyMinutes = dailyMap
            )
        }.sortedByDescending { it.totalMinutes }

        val weekTotalMinutes = dailyTotals.sumOf { it.minutes }
        _uiState.value = _uiState.value.copy(
            weekData = WeekViewData(
                totalMinutes = weekTotalMinutes,
                dailyTotals = dailyTotals,
                dailyAppUsages = dailyAppUsages,
                appBreakdowns = appBreakdowns,
                startDate = startDate.toString(),
                endDate = endDate.toString(),
                title = period.label
            )
        )
    }

    private fun resolveDayLabel(date: LocalDate, today: LocalDate): String {
        if (date == today) return "今日"
        return dayOfWeekLabels[date.dayOfWeek] ?: date.format(dateFormatter)
    }
}
