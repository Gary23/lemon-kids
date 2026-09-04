package com.lemonkids.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 家庭可复用的任务定义；执行日期和孩子在安排任务时才确定。 */
@Serializable
data class TaskTemplate(
    @SerialName("id") val id: String = "",
    @SerialName("family_id") val familyId: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("description") val description: String = "",
    @SerialName("category") val category: String = "默认",
    @SerialName("reward_points") val rewardPoints: Int = 5,
    @SerialName("penalty_points") val penaltyPoints: Int = 2,
    @SerialName("created_at") val createdAt: String = ""
)
