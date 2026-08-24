package com.lemonkids.shared.ui.auth

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.shared.model.Family
import com.lemonkids.shared.model.User
import com.lemonkids.shared.model.UserRole
import com.lemonkids.shared.auth.SessionRecoveryCoordinator
import com.lemonkids.shared.repository.AuthRepository
import com.lemonkids.shared.repository.AlreadyBoundException
import com.lemonkids.shared.repository.BindingCodeInfo
import com.lemonkids.shared.repository.ChildCredentials
import com.lemonkids.shared.repository.ChildUserInfo
import com.lemonkids.shared.repository.FamilyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val isFirstCheckComplete: Boolean = false,
    val isLoggedIn: Boolean = false,
    val currentUser: User? = null,
    val familyInviteCode: String? = null,
    val childCredentials: ChildCredentials? = null,
    val childUsers: List<ChildUserInfo> = emptyList(),
    val errorMessage: String? = null,
    val needsFamilySetup: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val isBindingCodeMode: Boolean = false,
    val bindingCodeStatus: String? = null,
    val boundDeviceId: String? = null,
    val bindingCodes: List<BindingCodeInfo> = emptyList(),
    /** 由根层展示，必须压过任意业务弹层的会话恢复弹窗。 */
    val requiresSessionRecovery: Boolean = false,
    val isRecoveringSession: Boolean = false,
    val sessionRecoveryMessage: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val familyRepository: FamilyRepository,
    private val sessionRecoveryCoordinator: SessionRecoveryCoordinator,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState(isLoading = true))
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var firstCheckDone = false

    private val literacyBindingPreferences by lazy {
        context.getSharedPreferences(LITERACY_BINDING_PREFERENCES, Context.MODE_PRIVATE)
    }
    private val isLiteracyApp get() = context.packageName == LITERACY_APP_PACKAGE

    init {
        viewModelScope.launch {
            restoreExistingSession()
        }
        viewModelScope.launch {
            authRepository.observeCurrentUser().collect { user ->
                if (firstCheckDone || user != null) {
                    applyUserState(user)
                }
            }
        }
        viewModelScope.launch {
            sessionRecoveryCoordinator.required.collect { required ->
                _uiState.value = _uiState.value.copy(
                    requiresSessionRecovery = required,
                    isRecoveringSession = if (required) _uiState.value.isRecoveringSession else false,
                    sessionRecoveryMessage = if (required) _uiState.value.sessionRecoveryMessage else null
                )
            }
        }
    }

    private suspend fun restoreExistingSession() {
        while (true) {
            authRepository.restoreSession().fold(
                onSuccess = { user ->
                    if (user == null && isLiteracyApp && restoreLiteracyBinding()) return
                    firstCheckDone = true
                    applyUserState(user)
                    return
                },
                onFailure = { error ->
                    // 认字端已有本地绑定码时，刷新失败不再无限停留在启动加载页。
                    // 根层恢复弹层会让孩子/家长明确选择重试或静默重新登录。
                    if (isLiteracyApp && authRepository.hasAuthSession) {
                        firstCheckDone = true
                        _uiState.value = _uiState.value.copy(
                            isFirstCheckComplete = false,
                            isLoading = false,
                            sessionRecoveryMessage = "登录凭证刷新失败，请恢复登录后继续。"
                        )
                        sessionRecoveryCoordinator.requireRecovery()
                        return
                    } else if (authRepository.hasAuthSession) {
                        _uiState.value = _uiState.value.copy(
                            isFirstCheckComplete = false,
                            isLoading = true,
                            errorMessage = null
                        )
                        delay(5000)
                    } else {
                        firstCheckDone = true
                        _uiState.value = AuthUiState(
                            isFirstCheckComplete = true,
                            isLoading = false,
                            errorMessage = error.message
                        )
                        return
                    }
                }
            )
        }
    }

    /** 认证会话失效时，使用认字端本地已验证的绑定码静默恢复登录。 */
    private suspend fun restoreLiteracyBinding(): Boolean {
        val code = literacyBindingPreferences.getString(LITERACY_BINDING_CODE_KEY, null)
            ?.takeIf { it.length == BINDING_CODE_LENGTH }
            ?: return false
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: return false
        val bindingResult = authRepository.exchangeBindingCode(code, deviceId, LITERACY_BINDING_TYPE)
            .getOrElse { return false }
        val user = authRepository.signInWithEmailPassword(
            bindingResult.email,
            authRepository.childPassword(bindingResult.childUid)
        ).getOrElse { return false }
        applyUserState(user)
        sessionRecoveryCoordinator.markRecovered()
        return true
    }

    /** 在功能请求发现 token 不可用后，重新尝试刷新当前会话。 */
    fun retrySessionRefresh() {
        viewModelScope.launch {
            updateSessionRecoveryUi(isRecovering = true, message = null)
            authRepository.restoreSession().fold(
                onSuccess = { user ->
                    if (user != null) {
                        applyUserState(user)
                        sessionRecoveryCoordinator.markRecovered()
                    } else {
                        updateSessionRecoveryUi(
                            isRecovering = false,
                            message = "没有可刷新的登录凭证，请使用绑定码重新登录。"
                        )
                    }
                },
                onFailure = {
                    updateSessionRecoveryUi(
                        isRecovering = false,
                        message = "刷新登录状态失败，请检查网络后重试，或使用绑定码重新登录。"
                    )
                }
            )
        }
    }

    /** 使用本机保存且已验证的认字绑定码，静默换取一份全新会话。 */
    fun restoreLiteracyBindingFromDialog() {
        viewModelScope.launch {
            updateSessionRecoveryUi(isRecovering = true, message = null)
            if (!isLiteracyApp || !restoreLiteracyBinding()) {
                updateSessionRecoveryUi(
                    isRecovering = false,
                    message = "本机绑定码无法恢复登录，请重新进入认字应用后按提示绑定。"
                )
            }
        }
    }

    private fun updateSessionRecoveryUi(isRecovering: Boolean, message: String?) {
        _uiState.value = _uiState.value.copy(
            requiresSessionRecovery = true,
            isRecoveringSession = isRecovering,
            sessionRecoveryMessage = message
        )
    }

    private fun applyUserState(user: User?) {
        firstCheckDone = true
        val hasFamily = user?.familyId?.isNotEmpty() == true
        _uiState.value = AuthUiState(
            isFirstCheckComplete = true,
            isLoading = false,
            isLoggedIn = user != null,
            currentUser = user,
            familyInviteCode = _uiState.value.familyInviteCode,
            childCredentials = _uiState.value.childCredentials,
            childUsers = _uiState.value.childUsers,
            errorMessage = _uiState.value.errorMessage,
            needsFamilySetup = user != null && !hasFamily,
            isUploadingAvatar = _uiState.value.isUploadingAvatar,
            // 用户状态刷新时保留家庭管理页已加载的绑定码，避免列表被重置为空。
            bindingCodes = _uiState.value.bindingCodes,
            requiresSessionRecovery = _uiState.value.requiresSessionRecovery,
            isRecoveringSession = _uiState.value.isRecoveringSession,
            sessionRecoveryMessage = _uiState.value.sessionRecoveryMessage
        )

        val fid = user?.familyId
        if (!fid.isNullOrEmpty() && _uiState.value.familyInviteCode == null) {
            viewModelScope.launch {
                familyRepository.getFamily(fid).fold(
                    onSuccess = { family ->
                        _uiState.value = _uiState.value.copy(familyInviteCode = family.inviteCode)
                    },
                    onFailure = { }
                )
                authRepository.fetchChildUsers(fid).fold(
                    onSuccess = { children ->
                        _uiState.value = _uiState.value.copy(childUsers = children)
                    },
                    onFailure = { }
                )
            }
        }
    }

    fun registerAndLogin(
        email: String,
        password: String,
        name: String,
        role: UserRole,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepository.signUp(email, password, User(name = name, role = role))
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        currentUser = it,
                        needsFamilySetup = true
                    )
                    onSuccess()
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = it.message ?: "注册失败，请重试"
                    )
                }
            )
        }
    }

    fun login(
        email: String,
        password: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepository.signIn(email, password)
            result.fold(
                onSuccess = { user ->
                    val hasFamily = user.familyId?.isNotEmpty() == true
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        currentUser = user,
                        needsFamilySetup = !hasFamily
                    )
                    onSuccess()
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = it.message ?: "登录失败，请检查邮箱和密码"
                    )
                }
            )
        }
    }

    fun createFamily(name: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = familyRepository.createFamily(name)
            result.fold(
                onSuccess = { family ->
                    val user = _uiState.value.currentUser
                    if (user != null) {
                        authRepository.updateUser(user.copy(familyId = family.id)).fold(
                            onSuccess = {
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    currentUser = user.copy(familyId = family.id),
                                    familyInviteCode = family.inviteCode,
                                    needsFamilySetup = false
                                )
                                onSuccess()
                            },
                            onFailure = {
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    errorMessage = "创建家庭成功但绑定失败，请重试"
                                )
                            }
                        )
                    }
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = it.message ?: "创建家庭失败"
                    )
                }
            )
        }
    }

    fun joinFamily(inviteCode: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val user = _uiState.value.currentUser ?: return@launch
            val result = familyRepository.joinByInviteCode(inviteCode.trim(), user.uid)
            result.fold(
                onSuccess = { family ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        currentUser = user.copy(familyId = family.id),
                        needsFamilySetup = false
                    )
                    onSuccess()
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = it.message ?: "邀请码无效，请检查后重试"
                    )
                }
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            if (isLiteracyApp) {
                literacyBindingPreferences.edit().remove(LITERACY_BINDING_CODE_KEY).apply()
            }
            _uiState.value = AuthUiState()
        }
    }

    /**
     * 通过绑定码登录（Kid 端使用）
     * @param code 6 位绑定码
     * @param deviceId 设备唯一标识（ANDROID_ID）
     * @param type 绑定码类型（"task" 或 "monitor"）
     */
    fun enterBindingCode(code: String, deviceId: String, type: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                bindingCodeStatus = null,
                boundDeviceId = null
            )

            // 1. 兑换绑定码获取认证信息
            val exchangeResult = authRepository.exchangeBindingCode(code.trim(), deviceId, type)
            if (exchangeResult.isFailure) {
                val error = exchangeResult.exceptionOrNull()
                if (error is AlreadyBoundException) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        bindingCodeStatus = "already_bound",
                        boundDeviceId = error.boundDeviceId
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error?.message ?: "兑换失败"
                    )
                }
                return@launch
            }

            val bindingResult = exchangeResult.getOrThrow()
            val password = authRepository.childPassword(bindingResult.childUid)

            // 2. 使用邮箱和密码登录获取 Suapbase session
            val signInResult = authRepository.signInWithEmailPassword(bindingResult.email, password)
            if (signInResult.isFailure) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "登录失败: ${signInResult.exceptionOrNull()?.message}"
                )
                return@launch
            }

            val user = signInResult.getOrThrow()
            if (isLiteracyApp && type == LITERACY_BINDING_TYPE) {
                literacyBindingPreferences.edit().putString(LITERACY_BINDING_CODE_KEY, code.trim()).apply()
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isLoggedIn = true,
                currentUser = user,
                needsFamilySetup = false,
                bindingCodeStatus = "success",
                errorMessage = null
            )
        }
    }

    /** 监控码强制解绑重绑 */
    fun forceRebindBindingCode(code: String, deviceId: String, type: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                bindingCodeStatus = null
            )

            val exchangeResult = authRepository.exchangeBindingCode(
                code.trim(), deviceId, type, forceRebind = true
            )

            if (exchangeResult.isFailure) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = exchangeResult.exceptionOrNull()?.message ?: "解绑失败"
                )
                return@launch
            }

            val bindingResult = exchangeResult.getOrThrow()
            val password = authRepository.childPassword(bindingResult.childUid)

            val signInResult = authRepository.signInWithEmailPassword(bindingResult.email, password)
            if (signInResult.isFailure) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "登录失败"
                )
                return@launch
            }

            val user = signInResult.getOrThrow()
            if (isLiteracyApp && type == LITERACY_BINDING_TYPE) {
                literacyBindingPreferences.edit().putString(LITERACY_BINDING_CODE_KEY, code.trim()).apply()
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isLoggedIn = true,
                currentUser = user,
                needsFamilySetup = false,
                bindingCodeStatus = "success",
                boundDeviceId = null,
                errorMessage = null
            )
        }
    }

    /** 家长端：为孩子生成绑定码 */
    fun generateChildBindingCode(childUid: String?, childName: String?, type: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            authRepository.generateBindingCode(childUid, childName, type).fold(
                onSuccess = { code ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        bindingCodeStatus = "generated:$code"
                    )
                    // 刷新绑定码列表
                    val fid = _uiState.value.currentUser?.familyId
                    if (fid != null) loadBindingCodes(fid)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "生成绑定码失败"
                    )
                }
            )
        }
    }

    /** 清除绑定码相关状态 */
    fun clearBindingCodeStatus() {
        _uiState.value = _uiState.value.copy(
            bindingCodeStatus = null,
            boundDeviceId = null,
            errorMessage = null
        )
    }

    fun createChildAccount(childName: String) {
        viewModelScope.launch {
            val code = _uiState.value.familyInviteCode
            val familyId = _uiState.value.currentUser?.familyId
            if (code == null || familyId == null) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "请先创建家庭后再生成孩子账号"
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            authRepository.createChildAccount(childName, familyId, code).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isLoading = false, childCredentials = it)
                    refreshChildUsers(familyId)
                },
                onFailure = { e ->
                    if ((e.message ?: "").contains("already registered", ignoreCase = true)) {
                        refreshChildUsers(familyId)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            childCredentials = null,
                            errorMessage = "该邀请码已生成过账号，请查看下方列表"
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "创建孩子账号失败"
                        )
                    }
                }
            )
        }
    }

    fun refreshChildUsers(familyId: String? = null) {
        val fid = familyId ?: _uiState.value.currentUser?.familyId ?: return
        viewModelScope.launch {
            authRepository.fetchChildUsers(fid).fold(
                onSuccess = { _uiState.value = _uiState.value.copy(childUsers = it) },
                onFailure = { }
            )
            loadBindingCodes(fid)
        }
    }

    /** 家长端进入家庭管理页时，加载全部孩子的活跃绑定码。 */
    fun refreshBindingCodes() {
        val familyId = _uiState.value.currentUser?.familyId ?: return
        viewModelScope.launch {
            loadBindingCodes(familyId)
        }
    }

    private suspend fun loadBindingCodes(familyId: String) {
        authRepository.getChildBindingCodes(familyId).fold(
            onSuccess = { codes ->
                _uiState.value = _uiState.value.copy(bindingCodes = codes)
            },
            onFailure = { error ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = error.message ?: "加载绑定码失败"
                )
            }
        )
    }

    fun deleteChildUser(childUid: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            authRepository.deleteChildUser(childUid).fold(
                onSuccess = {
                    val fid = _uiState.value.currentUser?.familyId
                    if (fid != null) refreshChildUsers(fid)
                    _uiState.value = _uiState.value.copy(isLoading = false)
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = it.message ?: "删除失败"
                    )
                }
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun uploadAvatar(imageBytes: ByteArray, fileName: String) {
        Log.d("AuthVM", "开始上传头像: $fileName, size=${imageBytes.size}")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploadingAvatar = true)
            try {
                val user = authRepository.observeCurrentUser().first()
                val userId = user?.uid
                if (userId == null) {
                    Log.e("AuthVM", "无法获取当前用户")
                    _uiState.value = _uiState.value.copy(
                        isUploadingAvatar = false,
                        errorMessage = "请先登录"
                    )
                    return@launch
                }

                val result = withTimeout(30_000L) {
                    authRepository.uploadAndSetAvatar(userId, imageBytes, fileName)
                }
                result.fold(
                    onSuccess = { url ->
                        Log.d("AuthVM", "头像上传成功: $url")
                        _uiState.value = _uiState.value.copy(isUploadingAvatar = false)
                    },
                    onFailure = { e ->
                        Log.e("AuthVM", "头像上传失败", e)
                        _uiState.value = _uiState.value.copy(
                            isUploadingAvatar = false,
                            errorMessage = "头像上传失败：${e.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e("AuthVM", "头像上传异常", e)
                _uiState.value = _uiState.value.copy(
                    isUploadingAvatar = false,
                    errorMessage = "上传超时或异常：${e.message}"
                )
            }
        }
    }

    fun updateName(newName: String) {
        viewModelScope.launch {
            val userId = authRepository.currentUserId ?: return@launch
            authRepository.updateName(userId, newName).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(errorMessage = null)
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "更新失败：${it.message}"
                    )
                }
            )
        }
    }
}

private const val LITERACY_BINDING_PREFERENCES = "literacy_binding"
private const val LITERACY_BINDING_CODE_KEY = "binding_code"
private const val LITERACY_BINDING_TYPE = "task"
private const val BINDING_CODE_LENGTH = 6
private const val LITERACY_APP_PACKAGE = "com.lemonkids.kidliteracy"
