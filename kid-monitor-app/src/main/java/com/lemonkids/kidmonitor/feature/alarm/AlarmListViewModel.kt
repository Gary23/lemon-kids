package com.lemonkids.kidmonitor.feature.alarm

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.kidmonitor.alarm.AlarmBackgroundMusicCache
import com.lemonkids.kidmonitor.alarm.AlarmDao
import com.lemonkids.kidmonitor.alarm.DeviceAlarmEntity
import com.lemonkids.kidmonitor.alarm.AlarmRingService
import com.lemonkids.kidmonitor.alarm.RemoteAlarmSyncCoordinator
import com.lemonkids.shared.repository.AlarmBackgroundMusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.util.Log
import javax.inject.Inject

/** 只读取本机已下发的闹钟，不直接展示云端尚未部署的配置。 */
@HiltViewModel
class AlarmListViewModel @Inject constructor(
    alarmDao: AlarmDao,
    private val syncCoordinator: RemoteAlarmSyncCoordinator,
    private val musicRepository: AlarmBackgroundMusicRepository,
    private val musicCache: AlarmBackgroundMusicCache,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val alarms: StateFlow<List<DeviceAlarmEntity>> = alarmDao.observeEffectiveAlarms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _musicTestStatus = MutableStateFlow<String?>(null)
    val musicTestStatus: StateFlow<String?> = _musicTestStatus.asStateFlow()
    private val _isTestingMusic = MutableStateFlow(false)
    val isTestingMusic: StateFlow<Boolean> = _isTestingMusic.asStateFlow()

    fun refresh() {
        viewModelScope.launch { syncCoordinator.sync() }
    }

    /**
     * Debug APK 的真机试听入口：重新读取家庭曲库、校验并缓存指定上传文件，再将本地
     * 绝对路径交给同一响铃服务播放。这样能覆盖真实下载/校验/循环播放链路，不会误播内置兜底音。
     */
    fun testSeaSaltSunlightMusic() {
        if (_isTestingMusic.value) return
        viewModelScope.launch {
            _isTestingMusic.value = true
            _musicTestStatus.value = "正在下载并校验《海盐日光》Remix坚果果冻…"
            runCatching {
                // 同名文件可能是用户在修复上传失败后重新上传的版本。目录按创建时间正序
                // 返回时，不能因为较早的残留元数据对应对象已不存在，就阻断最新文件试听。
                val candidates = musicRepository.getPublishedMusic().getOrThrow()
                    .filter { it.name.normalizedMusicName() == TEST_MUSIC_NAME }
                    .asReversed()
                require(candidates.isNotEmpty()) { "未在本家庭曲库中找到《海盐日光》Remix坚果果冻" }
                val (music, cached) = candidates.firstNotNullOfOrNull { candidate ->
                    musicCache.cache(candidate)
                        .onFailure { error ->
                            // 只记录受控摘要；Storage 原始异常可能包含签名 URL 或请求头。
                            Log.w(
                                MUSIC_TEST_LOG_TAG,
                                "背景音乐缓存失败：id=${candidate.id}，${safeMusicFailureSummary(error)}"
                            )
                        }
                        .getOrNull()
                        ?.let { candidate to it }
                } ?: error("该音乐的文件尚未成功上传到云端，请在家长端重新上传后再试听")
                val file = musicCache.cachedFile(cached.fileName)
                    ?: error("音乐缓存文件不存在")
                ContextCompat.startForegroundService(
                    context,
                    AlarmRingService.debugStartIntent(
                        context = context,
                        alarmId = "debug-sea-salt-sunlight",
                        revision = System.currentTimeMillis(),
                        title = "背景音乐试听",
                        message = "《海盐日光》Remix坚果果冻正在播放",
                        requiresConfirmation = true,
                        backgroundMusicId = music.id,
                        backgroundMusicFilePath = file.absolutePath,
                        voiceEnabled = true,
                        voiceText = "正在试听海盐日光背景音乐"
                    )
                )
            }.onSuccess {
                _musicTestStatus.value = "正在试听《海盐日光》Remix坚果果冻；可在提醒页点击关闭"
            }.onFailure {
                Log.w(MUSIC_TEST_LOG_TAG, "背景音乐试听准备失败：${it::class.simpleName}")
                _musicTestStatus.value = "试听失败：${musicTestFailureMessage(it)}"
            }
            _isTestingMusic.value = false
        }
    }

    private companion object {
        const val TEST_MUSIC_NAME = "《海盐日光》Remix坚果果冻"
        const val MUSIC_TEST_LOG_TAG = "AlarmMusicTest"
    }

    /** Storage SDK 的 message 含 URL/请求头，不能直接展示到界面。 */
    private fun musicTestFailureMessage(error: Throwable): String = when {
        error.message?.contains("未在本家庭曲库") == true -> "未在本家庭曲库中找到指定音乐"
        error.message?.contains("尚未成功上传") == true -> "该音乐文件在云端不存在，请在家长端重新上传"
        else -> "请检查网络、登录状态和音乐上传结果"
    }

    /** 供真机诊断使用，绝不写入 Storage 的原始异常文本，避免泄露签名 URL/请求头。 */
    private fun safeMusicFailureSummary(error: Throwable): String = when {
        error.message?.contains("not_found", ignoreCase = true) == true -> "云端对象不存在或无读取权限"
        error.message?.contains("permission", ignoreCase = true) == true -> "云端对象无读取权限"
        error.message?.contains("401") == true -> "登录会话已失效"
        error.message?.contains("403") == true -> "云端对象无读取权限"
        error.message?.contains("404") == true -> "云端对象不存在"
        error.message?.contains("MIME") == true -> "音频格式校验失败"
        error.message?.contains("SHA-256") == true -> "音频完整性校验失败"
        error.message?.contains("大小") == true -> "音频大小校验失败"
        else -> "失败类型=${error::class.simpleName}"
    }

    private fun String.normalizedMusicName(): String = trim()
        .removeSuffix(".mp3")
        .removeSuffix(".ogg")
}
