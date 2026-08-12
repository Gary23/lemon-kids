package com.lemonkids.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 独立“已认识的字”表中的一条记录，不依赖认字任务或字库。 */
@Serializable
data class RecognizedCharacter(
    @SerialName("id") val id: String = "",
    @SerialName("family_id") val familyId: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("character") val character: String = "",
    @SerialName("character_audio_url") val characterAudioUrl: String = "",
    @SerialName("character_audio_version") val characterAudioVersion: String? = null,
    @SerialName("character_audio_hash") val characterAudioHash: String? = null,
    @SerialName("words") val words: List<LiteracyExample> = emptyList(),
    @SerialName("sentences") val sentences: List<LiteracyExample> = emptyList(),
    @SerialName("recognized_at") val recognizedAt: String? = null,
    @SerialName("source") val source: String = "manual",
    @SerialName("note") val note: String = "",
    /** 系统转入时保留原认字任务，供教学音频精确清理。 */
    @SerialName("source_literacy_character_id") val sourceLiteracyCharacterId: String? = null
)
