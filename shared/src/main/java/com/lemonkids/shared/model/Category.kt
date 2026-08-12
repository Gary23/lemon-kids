package com.lemonkids.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class Category(
    @SerialName("id") val id: String = "",
    @SerialName("family_id") val familyId: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("color") val color: String = "#4CAF50",
    @SerialName("created_at") val createdAt: String = ""
)
