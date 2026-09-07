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
    val isSaving: Boolean = false,
    val isRefreshingMonitorDevices: Boolean = false
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
    private var childLoadJob: Job? = null

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
                selected?.let { loadChildAlarms(it, restoreSession = false) }
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
        loadChildAlarms(child, restoreSession = false)
    }

    fun refresh() {
        // 应用从锁屏恢复后，内存中的用户资料仍可能存在，但 Supabase 的 access token
        // 已失效。先恢复会话，避免用匿名身份查询并把权限错误误判为“未绑定 Pad”。
        _uiState.value.selectedChild?.let { loadChildAlarms(it, restoreSession = true) }
    }

    private fun loadChildAlarms(child: ChildUserInfo, restoreSession: Boolean) {
        val fid = familyId ?: return
        childLoadJob?.cancel()
        alarmObserveJob?.cancel()
        childLoadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshingMonitorDevices = true, error = null)
            if (restoreSession) {
                val restoredUser = authRepository.restoreSession().getOrElse {
                    _uiState.value = _uiState.value.copy(
                        isRefreshingMonitorDevices = false,
                        error = "登录状态恢复失败，请稍后重试"
                    )
                    return@launch
                }
                if (restoredUser == null) {
                    _uiState.value = _uiState.value.copy(
                        isRefreshingMonitorDevices = false,
                        error = "登录状态已失效，请重新登录后重试"
                    )
                    return@launch
                }
            }
            remoteAlarmRepository.getMonitorDevices(fid, child.uid).fold(
                onSuccess = { devices ->
                    _uiState.value = _uiState.value.copy(
                        monitorDevices = devices,
                        isRefreshingMonitorDevices = false,
                        error = null
                    )
                    alarmObserveJob = viewModelScope.launch {
                        remoteAlarmRepository.observeParentAlarms(child.uid).collect { alarms ->
                            _uiState.value = _uiState.value.copy(remoteAlarms = alarms)
                        }
                    }
                },
                onFailure = {
                    // 网络、会话或权限异常时保留上一次成功读取到的设备，不能把“暂时
                    // 无法读取”伪装成“尚未绑定”。原始异常可能包含 URL/请求头，也不应展示。
                    _uiState.value = _uiState.value.copy(
                        isRefreshingMonitorDevices = false,
                        error = "暂时无法读取监控 Pad，请检查网络或登录状态后重试"
                    )
                }
            )
        }
    }

    fun saveRemoteAlarm(
        existing: RemoteAlarm?,
        targetDeviceId: String,
        triggerAt: Instant,
        endAt: Instant,
        title: String,
        message: String,
        requiresConfirmation: Boolean,
        onSuccess: () -> Unit
    ) {
        val state = _uiState.value
        val child = state.selectedChild ?: return
        val fid = familyId ?: return
        val selectedDeviceId = targetDeviceId.trim()
        if (selectedDeviceId.isBlank()) {
            _uiState.value = state.copy(error = "请选择要响铃的监控 Pad")
            return
        }
        // 只允许把闹钟发送给本次为当前孩子读取到的有效 monitor 设备，不能再默认
        // 取列表中的第一台。服务端 RLS 也会在写入时再次校验该设备仍处于有效绑定状态。
        val device = state.monitorDevices.firstOrNull { it.deviceId == selectedDeviceId } ?: run {
            _uiState.value = state.copy(error = "所选监控 Pad 已失效或不属于当前孩子，请刷新后重新选择")
            return
        }
        if (endAt.isBefore(Instant.now().plusSeconds(30))) {
            _uiState.value = state.copy(error = "结束日期的提醒时间至少要在 30 秒后")
            return
        }
        if (endAt.isBefore(triggerAt)) {
            _uiState.value = state.copy(error = "结束日期不能早于开始日期")
            return
        }
        val alarm = (existing ?: RemoteAlarm(
            id = UUID.randomUUID().toString(),
            familyId = fid,
            childId = child.uid,
            targetDeviceId = device.deviceId
        )).copy(
            targetDeviceId = device.deviceId,
            triggerAt = triggerAt.toString(),
            endAt = endAt.toString(),
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
                onSuccess = {
                    // 写入已被服务端确认后立即更新本页，Pad 回执仍由后台轮询校准。
                    val latest = _uiState.value
                    val updated = if (existing == null) {
                        (latest.remoteAlarms + ParentAlarmStatus(alarm, null))
                            .distinctBy { it.alarm.id }
                            .sortedBy { it.alarm.triggerAt }
                    } else {
                        latest.remoteAlarms.map { item ->
                            if (item.alarm.id == alarm.id) item.copy(alarm = alarm) else item
                        }
                    }
                    _uiState.value = latest.copy(isSaving = false, remoteAlarms = updated)
                    onSuccess()
                },
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
        val cancelled = alarm.copy(enabled = false, revision = alarm.revision + 1)
        remoteAlarmRepository.updateAlarm(cancelled).fold(
            onSuccess = {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    remoteAlarms = _uiState.value.remoteAlarms.map {
                        if (it.alarm.id == cancelled.id) it.copy(alarm = cancelled) else it
                    }
                )
            },
            onFailure = { error ->
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = "取消闹钟失败：${error.message ?: "请稍后重试"}"
                )
            }
        )
    }
}
