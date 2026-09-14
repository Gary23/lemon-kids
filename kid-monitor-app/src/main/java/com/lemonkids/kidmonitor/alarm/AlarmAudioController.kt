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
 * 背景部分使用应用内合成的轻柔钟声循环，避免到点依赖网络或第三方音乐版权；人声优先
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
        // 音乐 ID 已在入口白名单化；当前首期资源为代码内置的循环轻柔钟声。
        runCatching {
            require(musicId == AlarmBackgroundMusic.GENTLE_BELL_V1)
            val pcm = gentleBellPcm()
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

    /** 6 秒的低音量钟声动机，循环播放；不依赖外置媒体或网络。 */
    private fun gentleBellPcm(): ShortArray = ShortArray(SAMPLE_RATE * LOOP_SECONDS) { index ->
        val second = index.toDouble() / SAMPLE_RATE
        val noteStart = (second / NOTE_PERIOD_SECONDS).toInt() * NOTE_PERIOD_SECONDS
        val elapsed = second - noteStart
        val note = NOTES[((second / NOTE_PERIOD_SECONDS).toInt()) % NOTES.size]
        val envelope = if (elapsed < 0.9) kotlin.math.exp(-3.5 * elapsed) else 0.0
        (kotlin.math.sin(2.0 * Math.PI * note * elapsed) * envelope * Short.MAX_VALUE * 0.13).toInt().toShort()
    }

    companion object {
        private const val TAG = "AlarmAudioController"
        private const val SAMPLE_RATE = 16_000
        private const val LOOP_SECONDS = 6
        private const val NOTE_PERIOD_SECONDS = 1.5
        private val NOTES = doubleArrayOf(523.25, 659.25, 783.99, 659.25)
        private const val MUSIC_VOLUME = 1f
        private const val DUCKED_VOLUME = .25f
        private const val VOICE_REPEAT_INTERVAL_MILLIS = 20_000L
        private const val UTTERANCE_ID = "lemon-alarm-voice"
    }
}
