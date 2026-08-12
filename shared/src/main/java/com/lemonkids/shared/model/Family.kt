package com.lemonkids.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Family(
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("invite_code") val inviteCode: String = "",
    @SerialName("created_at") val createdAt: String = ""
)
