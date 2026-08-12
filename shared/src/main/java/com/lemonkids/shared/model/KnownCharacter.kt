package com.lemonkids.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 已由孩子掌握并收录到字库的单个汉字。 */
@Serializable
data class KnownCharacter(
    @SerialName("character") val character: String,
    @SerialName("learned_at") val learnedAt: String? = null
)
