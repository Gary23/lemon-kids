package com.lemonkids.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class AppLimit(
    @SerialName("id") val id: String = "",
    @SerialName("family_id") val familyId: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("package_name") val packageName: String = "",
    @SerialName("app_name") val appName: String = "",
    @SerialName("daily_limit_minutes") val dailyLimitMinutes: Int = 999,
    @SerialName("single_session_minutes") val singleSessionMinutes: Int = 0,
    @SerialName("cooldown_minutes") val cooldownMinutes: Int = 0,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("updated_at") val updatedAt: String = ""
)
