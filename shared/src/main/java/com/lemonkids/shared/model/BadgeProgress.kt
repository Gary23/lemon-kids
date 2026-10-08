package com.lemonkids.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BadgeProgress(
    @SerialName("child_id") val childId: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("badge_key") val badgeKey: String,
    val segment: String = "",
    val value: Int = 0,
    val version: Int = 0,
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("updated_by") val updatedBy: String? = null
)
