package com.lemonkids.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 服务端按上海日期聚合的番茄完成数。 */
@Serializable
data class PomodoroDailyCount(
    @SerialName("completed_date") val date: String,
    val count: Int
)
