package com.lemonkids.shared.repository.impl

import com.lemonkids.shared.model.User
import com.lemonkids.shared.model.UserRole
import com.lemonkids.shared.repository.AlreadyBoundException
import com.lemonkids.shared.repository.AuthRepository
import com.lemonkids.shared.repository.BindingCodeInfo
import com.lemonkids.shared.repository.BindingCodeResult
import com.lemonkids.shared.repository.ChildCredentials
import com.lemonkids.shared.repository.ChildUserInfo
import com.lemonkids.shared.util.Constants
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val supabase: SupabaseClient
) : AuthRepository {

    private val auth get() = supabase.pluginManager.getPlugin(Auth)
    private val postgrest get() = supabase.pluginManager.getPlugin(Postgrest)

    private val _currentUser = MutableStateFlow<User?>(null)
    private val avatarCache = ConcurrentHashMap<String, String>()
    private val nameCache = ConcurrentHashMap<String, String>()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            restoreSession()
        }
    }

    override val currentUserId: String?
        get() = auth.currentUserOrNull()?.id

    override val isLoggedIn: Boolean
        get() = auth.currentUserOrNull() != null

    override val hasAuthSession: Boolean
        get() = auth.currentSessionOrNull() != null

    override suspend fun restoreSession(): Result<User?> = runCatching {
        auth.awaitInitialization()
        val session = auth.currentSessionOrNull() ?: run {
            _currentUser.value = null
            return@runCatching null
        }

        // 刷新失败时不能继续拿旧 session.user 把界面判定为“已登录”。
        // 旧用户资料可留在内存中，但 access token 已可能不可用于任何受保护请求。
        auth.refreshCurrentSession()

        val uid = auth.currentUserOrNull()?.id
            ?: auth.currentSessionOrNull()?.user?.id
            ?: throw Exception("已恢复会话但未能获取用户信息")
        loadCurrentUser(uid)
    }

    private suspend fun loadCurrentUser(uid: String): User {
        val user = postgrest.from("users").select {
            filter { eq("uid", uid) }
        }.decodeSingle<User>()
        _currentUser.value = user
        return user
    }

    override suspend fun signUp(email: String, password: String, user: User): Result<User> =
        runCatching {
            auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val authUser = auth.currentUserOrNull()
                ?: throw Exception("注册后未能获取用户信息")
            val newUser = user.copy(uid = authUser.id)
            postgrest.from("users").insert(newUser)
            _currentUser.value = newUser
            newUser
        }

    override suspend fun signIn(email: String, password: String): Result<User> =
        runCatching {
            auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val authUser = auth.currentUserOrNull()
                ?: throw Exception("登录后未能获取用户信息")
            loadCurrentUser(authUser.id)
        }

    override suspend fun signInWithEmailPassword(email: String, password: String): Result<User> =
        signIn(email, password)

    override suspend fun signOut() {
        auth.signOut()
        _currentUser.value = null
    }

    override suspend fun generateBindingCode(
        childUid: String?,
        childName: String?,
        type: String
    ): Result<String> = runCatching {
        val params: Map<String, Any?> = mapOf(
            "p_child_uid" to childUid,
            "p_child_name" to childName,
            "p_type" to type
        )
        val result = rpcPost("generate_binding_code", params, authenticated = true)
        result.trim()
    }

    override suspend fun getChildBindingCodes(familyId: String): Result<List<BindingCodeInfo>> = runCatching {
        @Serializable
        data class Row(
            val code: String,
            @SerialName("child_uid") val childUid: String,
            val type: String,
            val status: String
        )
        val params: Map<String, Any?> = mapOf("p_family_id" to familyId)
        val raw = rpcPost("get_child_binding_codes", params, authenticated = true)
        val list = Json.decodeFromString<List<Row>>(raw)
        list.map { BindingCodeInfo(code = it.code, childUid = it.childUid, type = it.type, status = it.status) }
    }

    override suspend fun exchangeBindingCode(
        code: String,
        deviceId: String,
        type: String,
        forceRebind: Boolean
    ): Result<BindingCodeResult> = runCatching {
        @Serializable
        data class ExchangeResponse(
            val status: String,
            @SerialName("child_uid") val childUid: String? = null,
            @SerialName("family_id") val familyId: String? = null,
            val email: String? = null,
            @SerialName("device_id") val boundDeviceId: String? = null
        )

        val params: Map<String, Any?> = mapOf(
            "p_code" to code,
            "p_device_id" to deviceId,
            "p_type" to type,
            "p_force_rebind" to forceRebind
        )
        val raw = rpcPost("exchange_binding_code", params, authenticated = false)

        val response = Json.decodeFromString<ExchangeResponse>(raw)

        when (response.status) {
            "success" -> {
                val childUid = response.childUid ?: throw Exception("未获取到用户信息")
                val familyId = response.familyId ?: throw Exception("未获取到家庭信息")
                val email = response.email ?: throw Exception("未获取到邮箱")
                BindingCodeResult(childUid = childUid, familyId = familyId, email = email)
            }
            "already_bound" -> throw AlreadyBoundException(
                boundDeviceId = response.boundDeviceId ?: "unknown"
            )
            "expired" -> throw Exception("绑定码无效或已被删除，请重新生成")
            "not_found" -> throw Exception("绑定码无效，请检查后重试")
            else -> throw Exception("未知错误: ${response.status}")
        }
    }

    private suspend fun rpcPost(function: String, params: Map<String, Any?>, authenticated: Boolean): String {
        val url = "https://ebiikfxehhcrtrkioxqa.supabase.co/rest/v1/rpc/$function"
        val apiKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImViaWlrZnhlaGhjcnRya2lveHFhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODAzMjQxMTMsImV4cCI6MjA5NTkwMDExM30.PbYlbBiUN7CI4EFedzzEWANrcLI1gElvAjBTlGKi7Go"
        val token = if (authenticated) auth.currentSessionOrNull()?.accessToken else null

        val bodyJson = buildJsonBody(params)

        val client = HttpClient()
        try {
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                headers {
                    append("apikey", apiKey)
                    if (token != null) {
                        append("Authorization", "Bearer $token")
                    }
                }
                setBody(bodyJson)
            }
            return response.bodyAsText()
        } finally {
            client.close()
        }
    }

    private fun buildJsonBody(params: Map<String, Any?>): String {
        val entries = params.entries.joinToString(",") { (key, value) ->
            val jsonValue = when (value) {
                null -> "null"
                is String -> "\"${value.replace("\"", "\\\"")}\""
                is Boolean -> value.toString()
                is Number -> value.toString()
                else -> "\"$value\""
            }
            "\"$key\":$jsonValue"
        }
        return "{$entries}"
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    override fun childPassword(childUid: String): String =
        "LmK!d_" + md5(childUid + "salt_lemon_2024")

    override fun observeCurrentUser(): Flow<User?> = _currentUser.asStateFlow()

    override suspend fun updateUser(user: User): Result<Unit> = runCatching {
        postgrest.from("users").update(user) { filter { eq("uid", user.uid) } }
        _currentUser.value = user
    }

    override suspend fun createChildAccount(
        childName: String,
        familyId: String,
        inviteCode: String
    ): Result<ChildCredentials> = runCatching {
        val email = "kid_${inviteCode.lowercase()}@lkids.local"
        val password = "kid${inviteCode.lowercase()}"

        val parentSession = auth.currentSessionOrNull()

        try {
            auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
        } catch (e: Exception) {
            if ((e.message ?: "").contains("already registered", ignoreCase = true)) {
                // 已注册，直接登录获取 uid
            } else {
                throw e
            }
        }

        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }

        val childId = auth.currentUserOrNull()?.id
            ?: throw Exception("创建孩子账号失败")

        postgrest.from("users").insert(
            mapOf(
                "uid" to childId,
                "name" to childName,
                "role" to "child",
                "family_id" to familyId
            )
        )

        if (parentSession != null) {
            auth.importSession(parentSession)
        }

        ChildCredentials(email = email, password = password, childName = childName)
    }

    override suspend fun deleteChildUser(childUid: String): Result<Unit> = runCatching {
        runCatching { postgrest.from("point_records").delete { filter { eq("child_id", childUid) } } }
        runCatching { postgrest.from("tasks").delete { filter { eq("child_id", childUid) } } }
        runCatching { postgrest.from("app_usage").delete { filter { eq("child_id", childUid) } } }
        runCatching { postgrest.from("app_limits").delete { filter { eq("child_id", childUid) } } }
        postgrest.from("users").delete { filter { eq("uid", childUid) } }
    }

    override suspend fun fetchChildUsers(familyId: String): Result<List<ChildUserInfo>> = runCatching {
        postgrest.from("users").select {
            filter { eq("family_id", familyId); eq("role", "child") }
        }.decodeList<User>().map { user ->
            ChildUserInfo(uid = user.uid, name = user.name, totalPoints = user.totalPoints, avatarUrl = user.avatarUrl)
        }
    }

    override suspend fun fetchParentUser(familyId: String): Result<ChildUserInfo?> = runCatching {
        val parent = postgrest.from("users").select {
            filter { eq("family_id", familyId); eq("role", "parent") }
        }.decodeSingleOrNull<User>()
        parent?.let {
            avatarCache[it.uid] = it.avatarUrl ?: ""
            nameCache[it.uid] = it.name
            ChildUserInfo(uid = it.uid, name = it.name, totalPoints = it.totalPoints, avatarUrl = it.avatarUrl)
        }
    }

    override suspend fun updateAvatar(userId: String, avatarUrl: String): Result<Unit> = runCatching {
        postgrest.from("users").update(mapOf("avatar_url" to avatarUrl)) {
            filter { eq("uid", userId) }
        }
        _currentUser.value = _currentUser.value?.copy(avatarUrl = avatarUrl)
        avatarCache[userId] = avatarUrl
    }

    override suspend fun uploadAndSetAvatar(userId: String, imageBytes: ByteArray, fileName: String): Result<String> =
        runCatching {
            val storage = supabase.pluginManager.getPlugin(Storage)
            storage.from(Constants.STORAGE_AVATARS)
                .upload(path = fileName, data = imageBytes, upsert = true)

            val url = storage.from(Constants.STORAGE_AVATARS).publicUrl(fileName)

            postgrest.from("users").update(mapOf("avatar_url" to url)) {
                filter { eq("uid", userId) }
            }
            _currentUser.value = _currentUser.value?.copy(avatarUrl = url)
            url
        }

    override suspend fun updateName(userId: String, newName: String): Result<Unit> = runCatching {
        postgrest.from("users").update(mapOf("name" to newName)) {
            filter { eq("uid", userId) }
        }
        _currentUser.value = _currentUser.value?.copy(name = newName)
        nameCache[userId] = newName
    }

    override suspend fun refreshUserInfo(userId: String): Result<ChildUserInfo> = runCatching {
        val user = postgrest.from("users").select {
            filter { eq("uid", userId) }
        }.decodeSingle<User>()
        avatarCache[userId] = user.avatarUrl ?: ""
        nameCache[userId] = user.name
        ChildUserInfo(uid = user.uid, name = user.name, totalPoints = user.totalPoints, avatarUrl = user.avatarUrl)
    }
}
