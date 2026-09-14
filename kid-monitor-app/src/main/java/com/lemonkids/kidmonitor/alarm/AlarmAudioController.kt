package com.lemonkids.kidmonitor.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.lemonkids.shared.model.AlarmBackgroundMusic
import java.util.Locale

/**
 * 单次响铃生命周期内唯一的音频协调器。
 *
 * 背景部分使用应用内合成的钟声或海边环境音乐循环，避免到点依赖网络或第三方音乐版权；人声优先
 * 使用落地后的语音资产（后续接入），当前以系统 TTS 作为可靠离线实现。两个声源共用一次
 * `USAGE_ALARM` 焦点，人声开始时只压低背景，不会中断自己的闹铃音。
 */
class AlarmAudioController(private val context: Context) {
    data class Config(
        val backgroundMusicId: String = AlarmBackgroundMusic.DEFAULT_ID,
        val voiceEnabled: Boolean = true,
        val voiceText: String = ""
    )

    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val musicAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val speechAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private var focusRequest: AudioFocusRequest? = null
    private var backgroundTrack: AudioTrack? = null
    private var fallbackPlayer: MediaPlayer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var config = Config()
    private var stopped = true

    private val repeatVoice = Runnable { speakCurrentText() }
    private val focusListener = AudioManager.OnAudioFocusChangeListener { focus ->
        when (focus) {
            AudioManager.AUDIOFOCUS_LOSS -> stop()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> setMusicVolume(0f)
            AudioManager.AUDIOFOCUS_GAIN -> setMusicVolume(MUSIC_VOLUME)
        }
    }

    fun start(initial: Config = Config(voiceEnabled = false)) {
        stop()
        stopped = false
        requestFocus()
        startBackground(AlarmBackgroundMusic.normalized(initial.backgroundMusicId))
        config = initial
        ensureTts()
        if (initial.voiceEnabled) speakCurrentText()
    }

    /** 配置在 Room 读取完成后更新；不在 AlarmReceiver 冷启动路径执行网络请求。 */
    fun updateAndSpeak(updated: Config) {
        if (stopped) return
        config = updated.copy(backgroundMusicId = AlarmBackgroundMusic.normalized(updated.backgroundMusicId))
        handler.removeCallbacks(repeatVoice)
        textToSpeech?.stop()
        ensureTts()
        speakCurrentText()
    }

    fun stop() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        ttsReady = false
        backgroundTrack?.runCatching { pause(); flush(); release() }
        backgroundTrack = null
        fallbackPlayer?.runCatching { stop(); release() }
        fallbackPlayer = null
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
        focusRequest = null
    }

    private fun requestFocus() {
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(musicAttributes)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        audioManager.requestAudioFocus(requireNotNull(focusRequest))
    }

    private fun startBackground(musicId: String) {
        // 音乐 ID 已在入口白名单化；资源均由代码本地合成并循环播放。
        runCatching {
            val pcm = when (musicId) {
                AlarmBackgroundMusic.GENTLE_BELL_V1 -> gentleBellPcm()
                AlarmBackgroundMusic.SEASIDE_SUNRISE_V1 -> seasideSunrisePcm()
                else -> error("不支持的背景音乐：$musicId")
            }
            AudioTrack.Builder()
                .setAudioAttributes(musicAttributes)
                .setAudioFormat(AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
                .also { track ->
                    check(track.write(pcm, 0, pcm.size) == pcm.size)
                    track.setLoopPoints(0, pcm.size, -1)
                    track.setVolume(MUSIC_VOLUME)
                    track.play()
                    backgroundTrack = track
                }
        }.onFailure { error ->
            // 极少数设备无法创建静态 AudioTrack 时保留系统闹钟声兜底，不能静默失败。
            Log.w(TAG, "内置背景音乐初始化失败，回退系统闹钟声", error)
            val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            fallbackPlayer = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(musicAttributes)
                    setDataSource(context, sound)
                    isLooping = true
                    prepare()
                    start()
                }
            }.getOrNull()
        }
    }

    private fun ensureTts() {
        if (textToSpeech != null) return
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS || stopped) return@TextToSpeech
            val engine = textToSpeech ?: return@TextToSpeech
            engine.language = Locale.SIMPLIFIED_CHINESE
            engine.setAudioAttributes(speechAttributes)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) {
                    handler.post { setMusicVolume(DUCKED_VOLUME) }
                }
                override fun onDone(utteranceId: String) {
                    handler.post { onVoiceFinished() }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String) {
                    handler.post { onVoiceFinished() }
                }
                override fun onError(utteranceId: String, errorCode: Int) {
                    handler.post { onVoiceFinished() }
                }
            })
            ttsReady = true
            speakCurrentText()
        }
    }

    private fun speakCurrentText() {
        if (stopped || !config.voiceEnabled || config.voiceText.isBlank()) return
        if (!ttsReady) {
            ensureTts()
            return
        }
        handler.removeCallbacks(repeatVoice)
        setMusicVolume(DUCKED_VOLUME)
        val result = textToSpeech?.speak(config.voiceText, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        if (result != TextToSpeech.SUCCESS) onVoiceFinished()
    }

    private fun onVoiceFinished() {
        if (stopped) return
        setMusicVolume(MUSIC_VOLUME)
        if (config.voiceEnabled && config.voiceText.isNotBlank()) {
            handler.removeCallbacks(repeatVoice)
            handler.postDelayed(repeatVoice, VOICE_REPEAT_INTERVAL_MILLIS)
        }
    }

    private fun setMusicVolume(volume: Float) {
        backgroundTrack?.setVolume(volume)
        fallbackPlayer?.setVolume(volume, volume)
    }

    /** 6 秒的钟声动机，循环播放；不依赖外置媒体或网络。 */
    private fun gentleBellPcm(): ShortArray = ShortArray(SAMPLE_RATE * GENTLE_BELL_LOOP_SECONDS) { index ->
        val second = index.toDouble() / SAMPLE_RATE
        val noteStart = (second / NOTE_PERIOD_SECONDS).toInt() * NOTE_PERIOD_SECONDS
        val elapsed = second - noteStart
        val note = NOTES[((second / NOTE_PERIOD_SECONDS).toInt()) % NOTES.size]
        val envelope = if (elapsed < 0.9) kotlin.math.exp(-3.5 * elapsed) else 0.0
        // AudioTrack 的播放音量已经是 100%，因此提高 PCM 振幅以再将背景铃声音量加倍。
        (kotlin.math.sin(2.0 * Math.PI * note * elapsed) * envelope * Short.MAX_VALUE * 0.52).toInt().toShort()
    }

    /**
     * 12 秒海边晨光环境声：缓慢起伏的海浪、两次海鸥鸣叫与明亮和声音色。
     * 全部样本由确定性公式产生，既可离线播放也不需要引入受版权限制的录音素材。
     */
    private fun seasideSunrisePcm(): ShortArray = ShortArray(SAMPLE_RATE * SEASIDE_LOOP_SECONDS) { index ->
        val second = index.toDouble() / SAMPLE_RATE
        val waveRise = 0.5 + 0.5 * kotlin.math.sin(2.0 * Math.PI * 0.085 * second - Math.PI / 2)
        val foam = seaNoise(second) * (0.075 + 0.09 * waveRise)
        val undertow = kotlin.math.sin(2.0 * Math.PI * 93.0 * second) * 0.025
        val surf = foam + undertow
        val gull = gullCall(second, 2.2) + gullCall(second, 8.1)
        val sunlight = sunlightChord(second, 0.25) + sunlightChord(second, 6.25)
        ((surf + gull + sunlight).coerceIn(-0.92, 0.92) * Short.MAX_VALUE * SEASIDE_VOLUME).toInt().toShort()
    }

    private fun gullCall(second: Double, start: Double): Double {
        val elapsed = second - start
        if (elapsed !in 0.0..0.72) return 0.0
        val contour = kotlin.math.sin(Math.PI * elapsed / 0.72)
        val frequency = if (elapsed < 0.36) 1_050.0 + elapsed * 1_250.0 else 1_500.0 - (elapsed - 0.36) * 1_000.0
        return kotlin.math.sin(2.0 * Math.PI * frequency * elapsed) * contour * 0.12
    }

    private fun sunlightChord(second: Double, start: Double): Double {
        val elapsed = second - start
        if (elapsed !in 0.0..1.6) return 0.0
        val envelope = kotlin.math.exp(-2.2 * elapsed)
        return (kotlin.math.sin(2.0 * Math.PI * 523.25 * elapsed) +
            kotlin.math.sin(2.0 * Math.PI * 659.25 * elapsed) +
            kotlin.math.sin(2.0 * Math.PI * 783.99 * elapsed)) * envelope * 0.018
    }

    /** 使用与 12 秒循环对齐的多个高频正弦波模拟海浪白噪声，循环接缝保持连续。 */
    private fun seaNoise(second: Double): Double =
        (kotlin.math.sin(2.0 * Math.PI * 53.0 * second / SEASIDE_LOOP_SECONDS + 0.3) +
            kotlin.math.sin(2.0 * Math.PI * 89.0 * second / SEASIDE_LOOP_SECONDS + 1.1) +
            kotlin.math.sin(2.0 * Math.PI * 149.0 * second / SEASIDE_LOOP_SECONDS + 2.4) +
            kotlin.math.sin(2.0 * Math.PI * 257.0 * second / SEASIDE_LOOP_SECONDS + 0.7)) / 4.0

    companion object {
        private const val TAG = "AlarmAudioController"
        private const val SAMPLE_RATE = 16_000
        private const val GENTLE_BELL_LOOP_SECONDS = 6
        private const val SEASIDE_LOOP_SECONDS = 12
        private const val NOTE_PERIOD_SECONDS = 1.5
        private val NOTES = doubleArrayOf(523.25, 659.25, 783.99, 659.25)
        private const val SEASIDE_VOLUME = 0.5
        private const val MUSIC_VOLUME = 1f
        private const val DUCKED_VOLUME = .25f
        private const val VOICE_REPEAT_INTERVAL_MILLIS = 6_000L
        private const val UTTERANCE_ID = "lemon-alarm-voice"
    }
}
