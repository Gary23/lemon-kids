package com.lemonkids.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class Task(
    @SerialName("id") val id: String = "",
    @SerialName("family_id") val familyId: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("description") val description: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("created_by") val createdBy: String = "",
    @SerialName("status") val status: TaskStatus = TaskStatus.PENDING,
    @SerialName("category") val category: String = "默认",
            @SerialName("due_date") val dueDate: String = "",
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("due_time") val dueTime: String? = null,    @SerialName("reward_points") val rewardPoints: Int = 5,
    @SerialName("penalty_points") val penaltyPoints: Int = 2,
    @SerialName("require_photo") val requirePhoto: Boolean = false,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("verified_at") val verifiedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
enum class TaskStatus {
    @SerialName("pending") PENDING,
    @SerialName("done") DONE,
    @SerialName("verified") VERIFIED,
    @SerialName("expired") EXPIRED,
    @SerialName("rejected") REJECTED
}

// @Serializable
// enum class TaskCategory {
//     @SerialName("study") STUDY,
//     @SerialName("chore") CHORE,
//     @SerialName("reading") READING,
//     @SerialName("exercise") EXERCISE,
//     @SerialName("other") OTHER
// }
