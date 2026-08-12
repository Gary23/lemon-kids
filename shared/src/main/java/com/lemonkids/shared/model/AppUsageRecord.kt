package com.lemonkids.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class AppUsageRecord(
    @SerialName("id") val id: String = "",
    @SerialName("family_id") val familyId: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("package_name") val packageName: String = "",
    @SerialName("app_name") val appName: String = "",
    @SerialName("duration_seconds") val durationSeconds: Long = 0,
    @SerialName("date") val date: String = "",
    @SerialName("collected_at") val collectedAt: String = ""
)
