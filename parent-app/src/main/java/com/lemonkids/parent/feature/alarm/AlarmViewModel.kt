package com.lemonkids.parent.feature.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.shared.model.MonitorDevice
import com.lemonkids.shared.model.ParentAlarmStatus
import com.lemonkids.shared.model.RemoteAlarm
import com.lemonkids.shared.repository.AuthRepository
import com.lemonkids.shared.repository.ChildUserInfo
import com.lemonkids.shared.repository.RemoteAlarmRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

data class AlarmUiState(
    val isLoading: Boolean = true,
    val children: List<ChildUserInfo> = emptyList(),
    val selectedChild: ChildUserInfo? = null,
    val monitorDevices: List<MonitorDevice> = emptyList(),
    val remoteAlarms: List<ParentAlarmStatus> = emptyList(),
    val error: String? = null,
    val isSaving: Boolean = false
)

@HiltViewModel
class AlarmViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val remoteAlarmRepository: RemoteAlarmRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AlarmUiState())
    val uiState: StateFlow<AlarmUiState> = _uiState.asStateFlow()

    private var familyId: String? = null
    private var alarmObserveJob: Job? = null

    init {
        loadChildren()
    }

    private fun loadChildren() = viewModelScope.launch {
        val user = authRepository.observeCurrentUser().first()
        val familyId = user?.familyId ?: run {
            _uiState.value = _uiState.value.copy(isLoading = false, error = "未找到家庭信息")
            return@launch
        }
        this@AlarmViewModel.familyId = familyId
        authRepository.fetchChildUsers(familyId).fold(
            onSuccess = { children ->
                val selected = _uiState.value.selectedChild ?: children.firstOrNull()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    children = children,
                    selectedChild = selected,
                    error = null
                )
                selected?.let(::loadChildAlarms)
            },
            onFailure = { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "读取孩子信息失败：${error.message ?: "请稍后重试"}"
                )
            }
        )
    }

    fun selectChild(child: ChildUserInfo) {
        _uiState.value = _uiState.value.copy(selectedChild = child, error = null)
        loadChildAlarms(child)
    }

    fun refresh() {
        _uiState.value.selectedChild?.let(::loadChildAlarms)
    }

    private fun loadChildAlarms(child: ChildUserInfo) {
        val fid = familyId ?: return
        viewModelScope.launch {
            remoteAlarmRepository.getMonitorDevices(fid, child.uid).fold(
                onSuccess = { devices ->
                    _uiState.value = _uiState.value.copy(monitorDevices = devices, error = null)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        monitorDevices = emptyList(),
                        error = "读取监控 Pad 失败：${error.message ?: "请稍后重试"}"
                    )
                }
            )
        }
        alarmObserveJob?.cancel()
        alarmObserveJob = viewModelScope.launch {
            remoteAlarmRepository.observeParentAlarms(child.uid).collect { alarms ->
                _uiState.value = _uiState.value.copy(remoteAlarms = alarms)
            }
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
            _uiState.value = state.copy(error = "请先在孩子的 Pad 上完成监控端绑定")
            return
        }
        if (triggerAt.isBefore(Instant.now().plusSeconds(30))) {
            _uiState.value = state.copy(error = "闹钟时间至少要在 30 秒后")
            return
        }
        val alarm = (existing ?: RemoteAlarm(
            id = UUID.randomUUID().toString(),
            familyId = fid,
            childId = child.uid,
            targetDeviceId = device.deviceId
        )).copy(
            triggerAt = triggerAt.toString(),
            timezone = ZoneId.systemDefault().id,
            title = title.trim(),
            message = message.trim(),
            enabled = true,
            requiresConfirmation = requiresConfirmation,
            revision = if (existing == null) 1 else existing.revision + 1
        )
        if (alarm.title.isBlank()) {
            _uiState.value = state.copy(error = "请填写闹钟标题")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            val result = if (existing == null) {
                remoteAlarmRepository.createAlarm(alarm)
            } else {
                remoteAlarmRepository.updateAlarm(alarm)
            }
            result.fold(
                onSuccess = { _uiState.value = _uiState.value.copy(isSaving = false) },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = "保存闹钟失败：${error.message ?: "请稍后重试"}"
                    )
                }
            )
        }
    }

    fun cancelRemoteAlarm(alarm: RemoteAlarm) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isSaving = true, error = null)
        remoteAlarmRepository.updateAlarm(alarm.copy(enabled = false, revision = alarm.revision + 1)).fold(
            onSuccess = { _uiState.value = _uiState.value.copy(isSaving = false) },
            onFailure = { error ->
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = "取消闹钟失败：${error.message ?: "请稍后重试"}"
                )
            }
        )
    }
}
