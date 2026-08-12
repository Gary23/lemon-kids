package com.lemonkids.shared.repository

import com.lemonkids.shared.model.User
import kotlinx.coroutines.flow.Flow

data class ChildCredentials(
    val email: String,
    val password: String,
    val childName: String
)

data class ChildUserInfo(
    val uid: String,
    val name: String,
    val totalPoints: Int,
    val avatarUrl: String? = null
)

data class BindingCodeResult(
    val childUid: String,
    val familyId: String,
    val email: String
)

data class BindingCodeInfo(
    val code: String,
    val childUid: String,
    val type: String,
    val status: String
)

class AlreadyBoundException(val boundDeviceId: String) : Exception("绑定码已绑定其他设备")

interface AuthRepository {
    val currentUserId: String?
    val isLoggedIn: Boolean
    val hasAuthSession: Boolean
    suspend fun restoreSession(): Result<User?>
    suspend fun signUp(email: String, password: String, user: User): Result<User>
    suspend fun signIn(email: String, password: String): Result<User>
    suspend fun signOut()
    fun observeCurrentUser(): Flow<User?>
    suspend fun updateUser(user: User): Result<Unit>
    suspend fun createChildAccount(
        childName: String,
        familyId: String,
        inviteCode: String
    ): Result<ChildCredentials>
    suspend fun fetchChildUsers(familyId: String): Result<List<ChildUserInfo>>
    suspend fun fetchParentUser(familyId: String): Result<ChildUserInfo?>
    suspend fun deleteChildUser(childUid: String): Result<Unit>
    suspend fun updateAvatar(userId: String, avatarUrl: String): Result<Unit>
    suspend fun uploadAndSetAvatar(userId: String, imageBytes: ByteArray, fileName: String): Result<String>
    suspend fun updateName(userId: String, newName: String): Result<Unit>
    suspend fun refreshUserInfo(userId: String): Result<ChildUserInfo>

    suspend fun generateBindingCode(childUid: String?, childName: String?, type: String): Result<String>

    suspend fun getChildBindingCodes(familyId: String): Result<List<BindingCodeInfo>>

    suspend fun exchangeBindingCode(
        code: String,
        deviceId: String,
        type: String,
        forceRebind: Boolean = false
    ): Result<BindingCodeResult>

    suspend fun signInWithEmailPassword(email: String, password: String): Result<User>

    fun childPassword(childUid: String): String
}
