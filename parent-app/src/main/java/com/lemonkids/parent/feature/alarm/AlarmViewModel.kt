package com.lemonkids.parent.feature.alarm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.shared.model.MonitorDevice
import com.lemonkids.shared.model.ParentAlarmStatus
import com.lemonkids.shared.model.RemoteAlarm
import com.lemonkids.shared.model.AlarmBackgroundMusic
import com.lemonkids.shared.model.AlarmVoiceText
import com.lemonkids.shared.model.AlarmBackgroundMusicAsset
import com.lemonkids.shared.model.FamilyAlarmMusicUpload
import com.lemonkids.shared.repository.AlarmBackgroundMusicRepository
import com.lemonkids.shared.repository.AuthRepository
import com.lemonkids.shared.repository.ChildUserInfo
import com.lemonkids.shared.repository.RemoteAlarmRepository
import io.github.jan.supabase.exceptions.RestException
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
    val musicCatalog: List<AlarmBackgroundMusicAsset> = emptyList(),
    val musicCatalogError: String? = null,
    val musicUploadError: String? = null,
    val error: String? = null,
    val isSaving: Boolean = false,
    val isRefreshingMonitorDevices: Boolean = false,
    val isUploadingMusic: Boolean = false
)

@HiltViewModel
class AlarmViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val remoteAlarmRepository: RemoteAlarmRepository,
    private val musicRepository: AlarmBackgroundMusicRepository
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
                    refreshMusicCatalog()
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

    private fun refreshMusicCatalog() = viewModelScope.launch {
        musicRepository.getPublishedMusic().fold(
            onSuccess = { catalog -> _uiState.value = _uiState.value.copy(musicCatalog = catalog, musicCatalogError = null) },
            onFailure = {
                // 保留本进程中上一次成功目录，编辑历史闹钟仍能明确提示下架状态。
                _uiState.value = _uiState.value.copy(musicCatalogError = "背景音乐目录暂时不可更新")
            }
        )
    }

    /** 家庭自定义音乐只写入专属 bucket；上传成功后立即加入当前可选目录。 */
    fun uploadFamilyMusic(upload: FamilyAlarmMusicUpload) {
        val fid = familyId ?: run {
            _uiState.value = _uiState.value.copy(musicUploadError = "未找到家庭信息，暂时无法上传")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploadingMusic = true, musicUploadError = null)
            musicRepository.uploadFamilyMusic(fid, upload).fold(
                onSuccess = { asset ->
                    _uiState.value = _uiState.value.copy(
                        isUploadingMusic = false,
                        musicCatalog = (_uiState.value.musicCatalog + asset).distinctBy { it.id },
                        musicCatalogError = null
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isUploadingMusic = false,
                        musicUploadError = "上传背景音乐失败：${musicUploadFailureMessage(error)}"
                    )
                }
            )
        }
    }

    /** Storage SDK 的原始异常会附带 URL 与请求头，其中可能包含 access token，不能透传到 UI。 */
    private fun musicUploadFailureMessage(error: Throwable): String {
        val restError = error.findRestException()
        // 仅记下状态码与服务端错误码，便于通过 adb 定位；绝不写入 SDK 的 message，
        // 因为其中包含请求 URL 和 Authorization 请求头。
        Log.w(
            MUSIC_UPLOAD_LOG_TAG,
            "家庭背景音乐上传失败：type=${error::class.simpleName}, " +
                "httpStatus=${restError?.statusCode ?: "none"}, serverError=${restError?.error ?: "none"}"
        )
        val message = restError?.let { "${it.error} ${it.description.orEmpty()}" }.orEmpty()
        return when {
            message.startsWith("家庭信息无效") ||
                message.startsWith("请填写音乐名称") ||
                message.startsWith("仅支持 MP3 或 OGG") ||
                message.startsWith("音频文件不能超过") ||
                message.startsWith("音频时长需为") -> message
            restError?.statusCode == 401 -> "登录已过期，请退出后重新登录再上传"
            restError?.statusCode == 403 || message.contains("row-level security", ignoreCase = true) ->
                "服务器未授予家庭音乐上传权限，请执行最新的 Storage 权限修复 SQL 后重试"
            restError?.statusCode == 413 -> "音频文件不能超过 5 MB"
            restError?.statusCode == 415 -> "音频格式不受服务器支持，请选择 MP3 或 OGG"
            restError?.statusCode == 400 -> "服务器拒绝了该音乐信息，请检查 SQL 是否已完整执行"
            else -> "上传服务暂时不可用，请检查网络后重试"
        }
    }

    /** 异常可能被协程或 SDK 包装，最多沿原因链检查六层。 */
    private fun Throwable.findRestException(): RestException? {
        var current: Throwable? = this
        repeat(6) {
            if (current is RestException) return current
            current = current?.cause
        }
        return null
    }

    fun reportMusicUploadError(message: String) {
        _uiState.value = _uiState.value.copy(musicUploadError = message)
    }

    private companion object {
        const val MUSIC_UPLOAD_LOG_TAG = "AlarmMusicUpload"
    }

    fun saveRemoteAlarm(
        existing: RemoteAlarm?,
        targetDeviceId: String,
        triggerAt: Instant,
        endAt: Instant,
        title: String,
        message: String,
        backgroundMusicId: String,
        voiceEnabled: Boolean,
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
        val isPublished = state.musicCatalog.any { it.id == backgroundMusicId }
        val keepsUnpublishedExistingMusic = existing?.backgroundMusicId == backgroundMusicId
        if (!isPublished && !keepsUnpublishedExistingMusic) {
            _uiState.value = state.copy(error = "所选背景音乐不可用，请重新选择")
            return
        }
        val normalizedTitle = title.trim()
        val normalizedMessage = message.trim()
        val voiceText = AlarmVoiceText.build(normalizedTitle, normalizedMessage)
        if (voiceText.length > AlarmVoiceText.MAX_LENGTH) {
            _uiState.value = state.copy(error = "播报内容不能超过 ${AlarmVoiceText.MAX_LENGTH} 个字符")
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
            title = normalizedTitle,
            message = normalizedMessage,
            backgroundMusicId = backgroundMusicId,
            voiceEnabled = voiceEnabled,
            voiceText = voiceText,
            enabled = true,
            deletedAt = null,
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

    /**
     * 关闭仅撤销 Pad 上的系统闹钟，并保留配置供家长再次打开后重新下发。
     */
    fun toggleRemoteAlarm(alarm: RemoteAlarm, enabled: Boolean) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isSaving = true, error = null)
        val updated = alarm.copy(enabled = enabled, revision = alarm.revision + 1)
        remoteAlarmRepository.updateAlarm(updated).fold(
            onSuccess = {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    remoteAlarms = _uiState.value.remoteAlarms.map {
                        if (it.alarm.id == updated.id) it.copy(alarm = updated) else it
                    }
                )
            },
            onFailure = { error ->
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = "更新闹钟开关失败：${error.message ?: "请稍后重试"}"
                )
            }
        )
    }

    /**
     * 不能直接物理删除云端记录：离线 Pad 需要拿到该墓碑版本，才能取消已经注册的
     * AlarmManager PendingIntent。家长端查询会隐藏 deletedAt 非空的记录。
     */
    fun deleteRemoteAlarm(alarm: RemoteAlarm) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isSaving = true, error = null)
        val deleted = alarm.copy(
            enabled = false,
            deletedAt = Instant.now().toString(),
            revision = alarm.revision + 1
        )
        remoteAlarmRepository.updateAlarm(deleted).fold(
            onSuccess = {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    remoteAlarms = _uiState.value.remoteAlarms.filterNot { it.alarm.id == deleted.id }
                )
            },
            onFailure = { error ->
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = "删除闹钟失败：${error.message ?: "请稍后重试"}"
                )
            }
        )
    }
}
