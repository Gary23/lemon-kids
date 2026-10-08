package com.lemonkids.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GrowthSnapshot(
    @SerialName("child_id") val childId: String,
    @SerialName("as_of") val asOf: String,
    @SerialName("completed_tasks") val completedTasks: Int,
    @SerialName("checkin_days") val checkinDays: Int,
    @SerialName("duty_days") val dutyDays: Int,
    @SerialName("total_exp") val totalExp: Long,
    @SerialName("badge_progress") val badgeProgress: List<GrowthBadgeProgress>,
    val events: List<GrowthEvent>
)

@Serializable
data class GrowthBadgeProgress(
    @SerialName("badge_key") val badgeKey: String,
    val segment: String,
    val value: Int,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class GrowthEvent(
    val kind: String,
    @SerialName("occurred_at") val occurredAt: String,
    val subject: String,
    val value: Long,
    @SerialName("badge_key") val badgeKey: String? = null,
    val segment: String? = null,
    val level: Int? = null
)
