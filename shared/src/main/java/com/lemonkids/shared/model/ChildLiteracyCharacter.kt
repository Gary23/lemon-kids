package com.lemonkids.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 一个孩子需要学习的一个汉字，以及字、词、句教学内容和整字学习状态。 */
@Serializable
data class ChildLiteracyCharacter(
    @SerialName("id") val id: String = "",
    @SerialName("family_id") val familyId: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("character") val character: String = "",
    @SerialName("character_audio_url") val characterAudioUrl: String = "",
    @SerialName("character_audio_version") val characterAudioVersion: String? = null,
    @SerialName("character_audio_hash") val characterAudioHash: String? = null,
    @SerialName("words") val words: List<LiteracyExample> = emptyList(),
    @SerialName("sentences") val sentences: List<LiteracyExample> = emptyList(),
    @SerialName("character_read_status") val characterReadStatus: LiteracyReadStatus = LiteracyReadStatus.UNREAD,
    @SerialName("word_read_status") val wordReadStatus: LiteracyReadStatus = LiteracyReadStatus.UNREAD,
    @SerialName("sentence_read_status") val sentenceReadStatus: LiteracyReadStatus = LiteracyReadStatus.UNREAD,
    @SerialName("character_read_date") val characterReadDate: String? = null,
    @SerialName("word_read_date") val wordReadDate: String? = null,
    @SerialName("sentence_read_date") val sentenceReadDate: String? = null,
    /** 所有字、词、句学习项均已获得三星时写入，且保留首次完成时间。 */
    @SerialName("learned_at") val learnedAt: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0
)

@Serializable
data class LiteracyExample(
    @SerialName("text") val text: String = "",
    /**
     * 与 text 中汉字顺序一一对应的不带声调拼音，例如“组长”是 ["zu", "zhang"]。
     * 旧数据没有此字段时保持空列表，客户端会兼容使用腾讯内置词典评测。
     */
    @SerialName("pinyins") val pinyins: List<String> = emptyList(),
    @SerialName("audio_url") val audioUrl: String = "",
    @SerialName("audio_version") val audioVersion: String? = null,
    @SerialName("audio_hash") val audioHash: String? = null
)

@Serializable
enum class LiteracyReadStatus {
    @SerialName("unread") UNREAD,
    @SerialName("read") READ
}
