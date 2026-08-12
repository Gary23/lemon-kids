package com.lemonkids.kidtask.util

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 儿童端文本转语音管理器，使用Android内置TTS引擎，模拟小朋友女生音色。
 *
 * 优先选择讯飞等国产引擎的女声（更自然），备选系统默认中文Voice。
 * pitch设为1.15~1.2模拟童声，语速正常偏慢让小朋友听清。
 */
@Singleton
class KidTtsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: TextToSpeech? = null

    /** TTS是否已初始化完成可正常朗读 */
    val isReady: Boolean get() = _ready
    @Volatile
    private var _ready = false

    @Volatile
    var onSpeakingChanged: ((String?) -> Unit)? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                configureVoice()
                _ready = true
            } else {
                Log.w("KidTtsManager", "TTS初始化失败，status=$status")
            }
        }
        // 使用MUSIC音频属性（非通知音），避免走通话音量通道
        tts?.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                onSpeakingChanged?.invoke(utteranceId)
            }

            override fun onDone(utteranceId: String?) {
                onSpeakingChanged?.invoke(null)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onSpeakingChanged?.invoke(null)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                onSpeakingChanged?.invoke(null)
            }
        })
    }

    private fun configureVoice() {
        val tts = this.tts ?: return

        val targetVoice = selectBestChineseVoice()
        if (targetVoice != null) {
            tts.voice = targetVoice
            Log.d("KidTtsManager", "选择Voice: ${targetVoice.name}")
        } else {
            tts.language = Locale.SIMPLIFIED_CHINESE
            Log.d("KidTtsManager", "未找到特定女声，使用默认中文TTS")
        }

        // pitch 1.15~1.20 模拟自然童声，太高会机械/花栗鼠感
        tts.setPitch(1.18f)
        // 语速0.85，略慢让小朋友能听清每个字
        tts.setSpeechRate(0.85f)
    }

    /**
     * 从系统可用Voice中选出最自然的中文女声。
     *
     * 优先级链：讯飞女声 > 华为女声 > 其他引擎女声 > 任意中文Voice > 默认引擎中文
     */
    private fun selectBestChineseVoice(): Voice? {
        val voices = tts?.voices ?: return null

        // 筛选中文Voice
        val zhVoices = voices.filter { v ->
            v.locale.language == Locale.CHINESE.language ||
            v.locale.language == "zh"
        }

        // --- 第一优先级：讯飞引擎女声（最自然） ---
        // 讯飞常见引擎名：com.iflytek.speechsuite 或包含 iflytek/ifly
        val iflytekVoices = zhVoices.filter { v ->
            v.name.lowercase().let {
                it.contains("iflytek") || it.contains("ifly") ||
                it.contains("讯飞")
            }
        }
        if (iflytekVoices.isNotEmpty()) {
            // 在讯飞Voice中挑选女声
            iflytekVoices.findFemaleVoice()?.let { return it }
            // 退而取第一个讯飞Voice（男声也比默认引擎自然）
            return iflytekVoices.first()
        }

        // --- 第二优先级：华为引擎女声 ---
        val huaweiVoices = zhVoices.filter { v ->
            v.name.lowercase().contains("huawei")
        }
        if (huaweiVoices.isNotEmpty()) {
            huaweiVoices.findFemaleVoice()?.let { return it }
            return huaweiVoices.first()
        }

        // --- 第三优先级：任意引擎的中文女声 ---
        zhVoices.findFemaleVoice()?.let { return it }

        // --- 兜底：任意中文Voice ---
        return zhVoices.firstOrNull()
    }

    /**
     * 从Voice列表中查找女声。
     * 讯飞/华为等国产引擎的Voice命名多含中文名（小燕/小蓉/小芸/小诗等）。
     */
    private fun List<Voice>.findFemaleVoice(): Voice? {
        // 中文女声常见名称（讯飞、华为、百度TTS等）
        val femaleNames = listOf(
            "小燕", "小蓉", "小芸", "小诗", "小美", "小莉",
            "小倩", "小雪", "小静", "小雅", "小玲", "小娟",
            "女声", "女性", "女生"
        )
        for (name in femaleNames) {
            find { it.name.contains(name) }?.let { return it }
        }
        // 英文关键词
        for (kw in listOf("female", "woman", "girl")) {
            find { it.name.lowercase().contains(kw) }?.let { return it }
        }
        return null
    }

    /**
     * 朗读任务的标题和描述。
     *
     * @param taskId 任务ID，用于跟踪朗读状态
     * @param title 任务标题
     * @param description 任务描述（可选，为空时只读标题）
     */
    fun speak(taskId: String, title: String, description: String?) {
        val t = this.tts ?: return
        val text = buildString {
            append(title)
            if (!description.isNullOrBlank()) {
                // 标题和描述之间用逗号长停顿隔开，听起来更自然
                append("，，") // 双逗号≈0.5s停顿
                append(description)
            }
        }

        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, taskId)
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        _ready = false
    }
}
