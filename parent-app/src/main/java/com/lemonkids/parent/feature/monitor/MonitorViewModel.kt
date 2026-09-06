package com.lemonkids.parent.feature.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.shared.model.AppLimit
import com.lemonkids.shared.model.AppUsageRecord
import com.lemonkids.shared.repository.AppUsageRepository
import com.lemonkids.shared.repository.AuthRepository
import com.lemonkids.shared.repository.ChildUserInfo
import com.lemonkids.shared.repository.RemoteAlarmRepository
import com.lemonkids.shared.model.MonitorDevice
import com.lemonkids.shared.model.ParentAlarmStatus
import com.lemonkids.shared.model.RemoteAlarm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
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
    val isSavingLimit: Boolean = false,
    val monitorDevices: List<MonitorDevice> = emptyList(),
    val remoteAlarms: List<ParentAlarmStatus> = emptyList(),
    val remoteAlarmError: String? = null,
    val isSavingRemoteAlarm: Boolean = false
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
    private val authRepository: AuthRepository,
    private val remoteAlarmRepository: RemoteAlarmRepository
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

        val fid = familyId
        if (fid != null) {
            viewModelScope.launch {
                remoteAlarmRepository.getMonitorDevices(fid, childId).fold(
                    onSuccess = { devices -> _uiState.value = _uiState.value.copy(monitorDevices = devices, remoteAlarmError = null) },
                    onFailure = { error -> _uiState.value = _uiState.value.copy(monitorDevices = emptyList(), remoteAlarmError = "读取监控 Pad 失败：${error.message ?: "请稍后重试"}") }
                )
            }
        }
        viewModelScope.launch {
            remoteAlarmRepository.observeParentAlarms(childId).collect { alarms ->
                _uiState.value = _uiState.value.copy(remoteAlarms = alarms)
            }
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

    fun saveRemoteAlarm(
        existing: RemoteAlarm?,
        triggerAt: Instant,
        title: String,
        message: String,
        requiresConfirmation: Boolean
    ) {
        val state = _uiState.value
        val child = state.selectedChild ?: return
        val fid = familyId ?: return
        val device = state.monitorDevices.firstOrNull() ?: run {
            _uiState.value = state.copy(remoteAlarmError = "请先在孩子的 Pad 上完成监控端绑定")
            return
        }
        if (triggerAt.isBefore(Instant.now().plusSeconds(30))) {
            _uiState.value = state.copy(remoteAlarmError = "闹钟时间至少要在 30 秒后")
            return
        }
        val alarm = (existing ?: RemoteAlarm(
            id = UUID.randomUUID().toString(), familyId = fid, childId = child.uid, targetDeviceId = device.deviceId
        )).copy(
            triggerAt = triggerAt.toString(), timezone = ZoneId.systemDefault().id,
            title = title.trim(), message = message.trim(), enabled = true,
            requiresConfirmation = requiresConfirmation,
            revision = if (existing == null) 1 else existing.revision + 1
        )
        if (alarm.title.isBlank()) {
            _uiState.value = state.copy(remoteAlarmError = "请填写闹钟标题")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingRemoteAlarm = true, remoteAlarmError = null)
            val result = if (existing == null) remoteAlarmRepository.createAlarm(alarm) else remoteAlarmRepository.updateAlarm(alarm)
            result.fold(
                onSuccess = { _uiState.value = _uiState.value.copy(isSavingRemoteAlarm = false) },
                onFailure = { error -> _uiState.value = _uiState.value.copy(isSavingRemoteAlarm = false, remoteAlarmError = "保存闹钟失败：${error.message ?: "请稍后重试"}") }
            )
        }
    }

    fun cancelRemoteAlarm(alarm: RemoteAlarm) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingRemoteAlarm = true, remoteAlarmError = null)
            remoteAlarmRepository.updateAlarm(alarm.copy(enabled = false, revision = alarm.revision + 1)).fold(
                onSuccess = { _uiState.value = _uiState.value.copy(isSavingRemoteAlarm = false) },
                onFailure = { error -> _uiState.value = _uiState.value.copy(isSavingRemoteAlarm = false, remoteAlarmError = "取消闹钟失败：${error.message ?: "请稍后重试"}") }
            )
        }
    }
}
