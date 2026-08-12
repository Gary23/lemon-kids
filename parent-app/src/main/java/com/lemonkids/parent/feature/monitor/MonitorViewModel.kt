package com.lemonkids.parent.feature.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.shared.model.AppLimit
import com.lemonkids.shared.model.AppUsageRecord
import com.lemonkids.shared.repository.AppUsageRepository
import com.lemonkids.shared.repository.AuthRepository
import com.lemonkids.shared.repository.ChildUserInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class MonitorUiState(
    val isLoading: Boolean = true,
    val children: List<ChildUserInfo> = emptyList(),
    val selectedChild: ChildUserInfo? = null,
    val selectedDate: LocalDate = LocalDate.now(),
    val todayTotalMinutes: Long = 0,
    val appUsages: List<AppUsageUiItem> = emptyList(),
    val appLimits: List<AppLimitUiItem> = emptyList(),
    val showLimitDialog: Boolean = false,
    val limitDialogPackageName: String = "",
    val limitDialogAppName: String = "",
    val limitDialogDailyMinutes: Int = 999,
    val limitDialogSessionMinutes: Int = 0,
    val limitDialogCooldownMinutes: Int = 0,
    val editingLimitId: String? = null,
    val limitDialogError: String? = null,
    val isSavingLimit: Boolean = false
)

data class AppUsageUiItem(
    val appName: String,
    val packageName: String,
    val minutes: Long
)

data class AppLimitUiItem(
    val id: String,
    val appName: String,
    val packageName: String,
    val dailyLimitMinutes: Int,
    val singleSessionMinutes: Int,
    val cooldownMinutes: Int,
    val isActive: Boolean
)

@HiltViewModel
class MonitorViewModel @Inject constructor(
    private val appUsageRepository: AppUsageRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MonitorUiState())
    val uiState: StateFlow<MonitorUiState> = _uiState.asStateFlow()

    private var familyId: String? = null

    init {
        loadChildren()
    }

    private fun loadChildren() {
        viewModelScope.launch {
            val user = authRepository.observeCurrentUser().first() ?: return@launch
            familyId = user.familyId ?: return@launch

            authRepository.fetchChildUsers(familyId!!).fold(
                onSuccess = { children ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        children = children,
                        selectedChild = _uiState.value.selectedChild ?: children.firstOrNull()
                    )
                    _uiState.value.selectedChild?.let { loadChildData(it.uid) }
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            )
        }
    }

    fun selectChild(child: ChildUserInfo) {
        _uiState.value = _uiState.value.copy(selectedChild = child)
        loadChildData(child.uid)
    }

    fun selectDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
        val childId = _uiState.value.selectedChild?.uid ?: return
        loadChildData(childId)
    }

    private fun loadChildData(childId: String) {
        val dateStr = _uiState.value.selectedDate.toString()

        viewModelScope.launch {
            val records = appUsageRepository.getTodayUsage(childId, dateStr)
            val totalSeconds = records.sumOf { it.durationSeconds }
            val grouped = records.groupBy { it.packageName }
            val usageItems = grouped.map { (_, list) ->
                val first = list.first()
                AppUsageUiItem(
                    appName = first.appName,
                    packageName = first.packageName,
                    minutes = list.sumOf { it.durationSeconds } / 60
                )
            }.sortedByDescending { it.minutes }

            _uiState.value = _uiState.value.copy(
                todayTotalMinutes = totalSeconds / 60,
                appUsages = usageItems
            )
        }

        viewModelScope.launch {
            appUsageRepository.observeAppLimits(childId).collect { limits ->
                val limitItems = limits.map {
                    AppLimitUiItem(
                        id = it.id,
                        appName = it.appName,
                        packageName = it.packageName,
                        dailyLimitMinutes = it.dailyLimitMinutes,
                        singleSessionMinutes = it.singleSessionMinutes,
                        cooldownMinutes = it.cooldownMinutes,
                        isActive = it.isActive
                    )
                }
                _uiState.value = _uiState.value.copy(appLimits = limitItems)
            }
        }
    }

    fun openLimitDialog(packageName: String, appName: String, existingLimit: AppLimitUiItem?) {
        _uiState.value = _uiState.value.copy(
            showLimitDialog = true,
            limitDialogPackageName = packageName,
            limitDialogAppName = appName,
            limitDialogDailyMinutes = existingLimit?.dailyLimitMinutes ?: 999,
            limitDialogSessionMinutes = existingLimit?.singleSessionMinutes ?: 0,
            limitDialogCooldownMinutes = existingLimit?.cooldownMinutes ?: 0,
            editingLimitId = existingLimit?.id,
            limitDialogError = null
        )
    }

    fun updateLimitDailyMinutes(minutes: Int) {
        _uiState.value = _uiState.value.copy(limitDialogDailyMinutes = minutes.coerceIn(0, 1440))
    }

    fun updateLimitSessionMinutes(minutes: Int) {
        _uiState.value = _uiState.value.copy(limitDialogSessionMinutes = minutes.coerceIn(0, 1440))
    }

    fun updateLimitCooldownMinutes(minutes: Int) {
        _uiState.value = _uiState.value.copy(limitDialogCooldownMinutes = minutes.coerceIn(0, 1440))
    }

    fun dismissLimitDialog() {
        _uiState.value = _uiState.value.copy(
            showLimitDialog = false,
            limitDialogError = null,
            isSavingLimit = false
        )
    }

    fun saveLimit() {
        val state = _uiState.value
        val childId = state.selectedChild?.uid ?: return
        val fid = familyId ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingLimit = true, limitDialogError = null)

            val existingId = state.editingLimitId
                ?: state.appLimits.firstOrNull { it.packageName == state.limitDialogPackageName }?.id
            val limit = AppLimit(
                id = existingId ?: UUID.randomUUID().toString(),
                familyId = fid,
                childId = childId,
                packageName = state.limitDialogPackageName,
                appName = state.limitDialogAppName,
                dailyLimitMinutes = state.limitDialogDailyMinutes,
                singleSessionMinutes = state.limitDialogSessionMinutes,
                cooldownMinutes = state.limitDialogCooldownMinutes,
                isActive = true
            )
            val result = if (existingId != null) {
                appUsageRepository.updateAppLimit(limit)
            } else {
                appUsageRepository.setAppLimit(limit).map { Unit }
            }

            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        showLimitDialog = false,
                        isSavingLimit = false,
                        limitDialogError = null
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSavingLimit = false,
                        limitDialogError = "保存失败：${error.message ?: "请稍后重试"}"
                    )
                }
            )
        }
    }

    fun refresh() {
        val childId = _uiState.value.selectedChild?.uid ?: return
        loadChildData(childId)
    }

    fun removeLimit(limitId: String) {
        viewModelScope.launch {
            appUsageRepository.removeAppLimit(limitId)
        }
    }
}
