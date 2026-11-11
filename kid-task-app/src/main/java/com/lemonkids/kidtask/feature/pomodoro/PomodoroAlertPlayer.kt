package com.lemonkids.kidtask.feature.pomodoro

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 番茄到点提醒：循环铃声 + 循环震动。
 *
 * 两个通道由同一个 [stop] 精确释放，重复调用不会叠加播放；设备静音、勿扰或没有
 * 振动硬件时只记录能力受限，不冒充已经成功提醒。
 */
@Singleton
class PomodoroAlertPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var ringing = false

    val isRinging: Boolean get() = ringing

    @Synchronized
    fun start() {
        if (ringing) return
        ringing = true
        startSound()
        startVibration()
    }

    @Synchronized
    fun stop() {
        ringing = false
        stopSound()
        stopVibration()
    }

    private fun startSound() {
        stopSound()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
        }.onFailure { Log.w(TAG, "无法播放番茄提醒铃声", it) }.getOrNull()
    }

    private fun stopSound() {
        player?.let { media ->
            runCatching { if (media.isPlaying) media.stop() }
            runCatching { media.release() }
        }
        player = null
    }

    private fun startVibration() {
        val service = context.getSystemService(Vibrator::class.java) ?: return
        if (!service.hasVibrator()) return
        vibrator = service
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                service.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, 0))
            } else {
                @Suppress("DEPRECATION")
                service.vibrate(VIBRATION_PATTERN, 0)
            }
        }.onFailure { Log.w(TAG, "无法启动番茄提醒震动", it) }
    }

    private fun stopVibration() {
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private companion object {
        const val TAG = "PomodoroAlert"
        val VIBRATION_PATTERN = longArrayOf(0, 500, 350)
    }
}
