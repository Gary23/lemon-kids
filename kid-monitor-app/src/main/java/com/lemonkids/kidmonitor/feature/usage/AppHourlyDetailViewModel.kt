package com.lemonkids.kidmonitor.feature.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class AppHourlyDetailUiState(
    val isLoading: Boolean = true,
    val appName: String = "",
    val packageName: String = "",
    val todayTotalMinutes: Long = 0,
    val hourlyUsages: List<HourlyUsage> = emptyList()
)

@HiltViewModel
class AppHourlyDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppHourlyDetailUiState())
    val uiState: StateFlow<AppHourlyDetailUiState> = _uiState.asStateFlow()

    fun load(packageName: String, appName: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, appName = appName, packageName = packageName)
        viewModelScope.launch {
            val today = LocalDate.now()
            val zone = ZoneId.systemDefault()
            val startMs = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val endMs = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

            withContext(Dispatchers.IO) {
                val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val usageEvents = runCatching {
                    usageStatsManager.queryEvents(startMs, endMs)
                }.getOrNull()

                if (usageEvents == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@withContext
                }

                val hourMsBuckets = collectAppHourlyMinutes(usageEvents, packageName, startMs, endMs, zone)

                val hourlyUsages = (0..23).map { hour ->
                    HourlyUsage(
                        hour = hour,
                        label = "${hour}:00",
                        minutes = hourMsBuckets[hour] / 60_000
                    )
                }

                val totalMinutes = hourlyUsages.sumOf { it.minutes }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    todayTotalMinutes = totalMinutes,
                    hourlyUsages = hourlyUsages
                )
            }
        }
    }

    /**
     * 从 UsageEvents 中提取指定应用在当天的逐小时前台使用时长
     * 算法：追踪 MOVE_TO_FOREGROUND/MOVE_TO_BACKGROUND 事件，按小时分桶累加
     */
    private fun collectAppHourlyMinutes(
        usageEvents: UsageEvents,
        targetPackage: String,
        dayStartMs: Long,
        dayEndMs: Long,
        zone: ZoneId
    ): LongArray {
        val hourMs = LongArray(24)

        data class ForegroundInterval(val startMs: Long, val endMs: Long)

        val intervals = mutableListOf<ForegroundInterval>()
        var currentStartMs: Long = 0L
        var isInForeground = false

        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.packageName != targetPackage) continue

            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    if (isInForeground && currentStartMs > 0) {
                        intervals.add(ForegroundInterval(currentStartMs, event.timeStamp))
                    }
                    isInForeground = true
                    currentStartMs = event.timeStamp
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (isInForeground) {
                        intervals.add(ForegroundInterval(currentStartMs, event.timeStamp))
                        isInForeground = false
                    }
                }
            }
        }
        // 如果当前仍在前台（正在使用中），补齐到当天结束
        if (isInForeground && currentStartMs > 0) {
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
                // 跨小时边界，拆分时长
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
}
