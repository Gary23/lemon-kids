package com.lemonkids.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class User(
    @SerialName("uid") val uid: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("role") val role: UserRole = UserRole.CHILD,
    @SerialName("family_id") val familyId: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("total_points") val totalPoints: Int = 0
)

@Serializable
enum class UserRole {
    @SerialName("child") CHILD,
    @SerialName("parent") PARENT
}
