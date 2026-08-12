package com.lemonkids.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class PointRecord(
    @SerialName("id") val id: String = "",
    @SerialName("family_id") val familyId: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("amount") val amount: Int = 0,
    @SerialName("balance") val balance: Int = 0,
    @SerialName("reason") val reason: String = "",
    @SerialName("type") val type: PointRecordType = PointRecordType.TASK_COMPLETE,
    @SerialName("related_task_id") val relatedTaskId: String? = null,
    @SerialName("related_reward_id") val relatedRewardId: String? = null,
    @SerialName("timestamp") val timestamp: String = ""
)

@Serializable
enum class PointRecordType {
    @SerialName("task_complete") TASK_COMPLETE,
    @SerialName("task_expired") TASK_EXPIRED,
    @SerialName("reward_redeem") REWARD_REDEEM,
    @SerialName("manual") MANUAL
}
