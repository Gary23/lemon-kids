package com.lemonkids.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class Reward(
    @SerialName("id") val id: String = "",
    @SerialName("family_id") val familyId: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("cost") val cost: Int = 0,
    @SerialName("repeatable") val repeatable: Boolean = true,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("description") val description: String? = null,
    @SerialName("cover_key") val coverKey: String = "gift",
    @SerialName("is_featured") val isFeatured: Boolean = false,
    @SerialName("created_at") val createdAt: String = ""
)
