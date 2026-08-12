package com.lemonkids.kidmonitor.feature.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.shared.model.AppUsageRecord
import com.lemonkids.shared.repository.AppUsageRepository
import com.lemonkids.shared.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class AppUsageDetailUiState(
    val isLoading: Boolean = true,
    val appName: String = "",
    val packageName: String = "",
    val averageMinutes: Long = 0,
    val dailyUsages: List<DailyTotal> = emptyList()
)

@HiltViewModel
class AppUsageDetailViewModel @Inject constructor(
    private val appUsageRepository: AppUsageRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUsageDetailUiState())
    val uiState: StateFlow<AppUsageDetailUiState> = _uiState.asStateFlow()

    private val dayOfWeekLabels = mapOf(
        DayOfWeek.MONDAY to "一", DayOfWeek.TUESDAY to "二",
        DayOfWeek.WEDNESDAY to "三", DayOfWeek.THURSDAY to "四",
        DayOfWeek.FRIDAY to "五", DayOfWeek.SATURDAY to "六",
        DayOfWeek.SUNDAY to "日"
    )

    fun load(packageName: String, startDate: String, endDate: String, appName: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, appName = appName, packageName = packageName)
        viewModelScope.launch {
            val userId = authRepository.currentUserId ?: return@launch
            val records = appUsageRepository.getDateRangeUsage(userId, startDate, endDate)
            val appRecords = records.filter { it.packageName == packageName }
            val recordsByDate = appRecords.groupBy { it.date }

            val start = LocalDate.parse(startDate)
            val end = LocalDate.parse(endDate)
            val today = LocalDate.now()
            val days = ChronoUnit.DAYS.between(start, end).toInt()

            val dailyUsages = (0..days).map { offset ->
                val date = start.plusDays(offset.toLong())
                val dateStr = date.toString()
                val dayRecords = recordsByDate[dateStr].orEmpty()
                DailyTotal(
                    date = dateStr,
                    dayLabel = if (date == today) "今日" else (dayOfWeekLabels[date.dayOfWeek] ?: dateStr.takeLast(5)),
                    minutes = dayRecords.sumOf { it.durationSeconds } / 60
                )
            }

            val totalMinutes = dailyUsages.sumOf { it.minutes }
            val dayCount = dailyUsages.size.coerceAtLeast(1)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                averageMinutes = totalMinutes / dayCount.toLong(),
                dailyUsages = dailyUsages
            )
        }
    }
}
