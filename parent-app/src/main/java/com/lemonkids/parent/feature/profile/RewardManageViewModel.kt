package com.lemonkids.parent.feature.profile

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.shared.model.Reward
import com.lemonkids.shared.model.UserRole
import com.lemonkids.shared.repository.AuthRepository
import com.lemonkids.shared.repository.RewardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RewardDraft(
    val title: String,
    val cost: String,
    val repeatable: Boolean,
    val description: String,
    val coverKey: String,
    val isFeatured: Boolean
)

internal fun validateRewardDraft(draft: RewardDraft): String? = when {
    draft.title.isBlank() -> "请输入奖励名称"
    draft.cost.toIntOrNull()?.let { it > 0 } != true -> "积分价格必须是正整数"
    draft.coverKey !in RewardManageViewModel.COVER_KEYS -> "请选择有效的封面"
    else -> null
}

data class RewardManageUiState(
    val rewards: List<Reward> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val operatingRewardId: String? = null,
    val selectedImageBytes: ByteArray? = null,
    val removeImage: Boolean = false,
    val isPreparingImage: Boolean = false,
    val imageUrls: Map<String, String> = emptyMap(),
    val imageErrors: Set<String> = emptySet(),
    val errorMessage: String? = null,
    val familyId: String? = null
) {
    val isBusy: Boolean get() = isSaving || isPreparingImage || operatingRewardId != null
}

@HiltViewModel
class RewardManageViewModel @Inject constructor(
    private val rewardRepository: RewardRepository,
    private val authRepository: AuthRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(RewardManageUiState())
    val uiState: StateFlow<RewardManageUiState> = _uiState.asStateFlow()
    private val cleanupPrefs = context.getSharedPreferences("reward_image_cleanup", Context.MODE_PRIVATE)

    init { refresh() }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun beginEdit() {
        _uiState.value = _uiState.value.copy(
            selectedImageBytes = null, removeImage = false, isPreparingImage = false, errorMessage = null
        )
    }

    fun selectImage(uri: Uri) {
        if (_uiState.value.isBusy) return
        _uiState.value = _uiState.value.copy(isPreparingImage = true, errorMessage = null)
        viewModelScope.launch {
            runCatching { prepareRewardImage(context, uri) }.fold(
                onSuccess = { bytes ->
                    _uiState.value = _uiState.value.copy(
                        selectedImageBytes = bytes, removeImage = false, isPreparingImage = false
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isPreparingImage = false,
                        errorMessage = "选择图片失败：${error.message ?: "请换一张图片"}"
                    )
                }
            )
        }
    }

    fun removeImage() {
        if (_uiState.value.isBusy) return
        _uiState.value = _uiState.value.copy(selectedImageBytes = null, removeImage = true, errorMessage = null)
    }

    fun retryImage(reward: Reward) {
        val path = reward.imagePath ?: return
        val familyId = _uiState.value.familyId ?: return
        _uiState.value = _uiState.value.copy(
            imageUrls = _uiState.value.imageUrls - path,
            imageErrors = _uiState.value.imageErrors - path
        )
        viewModelScope.launch {
            rewardRepository.createRewardImageUrl(familyId, path).fold(
                onSuccess = { url -> _uiState.value = _uiState.value.copy(imageUrls = _uiState.value.imageUrls + (path to url)) },
                onFailure = { _uiState.value = _uiState.value.copy(imageErrors = _uiState.value.imageErrors + path) }
            )
        }
    }

    fun refresh() = viewModelScope.launch { load(showLoading = _uiState.value.rewards.isEmpty()) }

    private suspend fun load(showLoading: Boolean) {
        if (showLoading) _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        val user = authRepository.observeCurrentUser().first()
        val familyId = user?.familyId
        if (user?.role != UserRole.PARENT || familyId.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                familyId = null,
                errorMessage = "没有可管理的家庭，请先完成家庭设置"
            )
            return
        }
        _uiState.value = _uiState.value.copy(familyId = familyId)
        rewardRepository.getAllRewards(familyId).fold(
            onSuccess = { rewards ->
                _uiState.value = _uiState.value.copy(
                    rewards = rewards,
                    familyId = familyId,
                    isLoading = false,
                    errorMessage = null,
                    imageUrls = emptyMap(),
                    imageErrors = emptySet()
                )
                rewards.filter { it.imagePath != null }.forEach(::retryImage)
                retryPendingCleanup(familyId, rewards)
            },
            onFailure = { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "加载奖励失败：${error.message ?: "请检查网络后重试"}"
                )
            }
        )
    }

    private fun rememberCleanup(path: String) {
        cleanupPrefs.edit().putStringSet(
            "paths", cleanupPrefs.getStringSet("paths", emptySet()).orEmpty() + path
        ).apply()
    }

    private fun retryPendingCleanup(familyId: String, rewards: List<Reward>) {
        val referenced = rewards.mapNotNull { it.imagePath }.toSet()
        cleanupPrefs.getStringSet("paths", emptySet()).orEmpty()
            .filter { it.startsWith("$familyId/") && it !in referenced }
            .forEach { path ->
                viewModelScope.launch {
                    rewardRepository.deleteRewardImage(familyId, path).onSuccess {
                        cleanupPrefs.edit().putStringSet(
                            "paths", cleanupPrefs.getStringSet("paths", emptySet()).orEmpty() - path
                        ).apply()
                    }.onFailure { Log.w("RewardManage", "待清理奖励图片重试失败：$path", it) }
                }
            }
    }

    fun save(draft: RewardDraft, editing: Reward?, onSaved: () -> Unit) {
        if (_uiState.value.isBusy) return
        val title = draft.title.trim()
        val price = draft.cost.toIntOrNull()
        val familyId = _uiState.value.familyId
        val validationError = if (familyId.isNullOrBlank()) "未获取到家庭信息，请刷新后重试"
            else validateRewardDraft(draft)
        if (validationError != null) {
            _uiState.value = _uiState.value.copy(errorMessage = validationError)
            return
        }
        val confirmedFamilyId = familyId ?: return
        val reward = Reward(
            id = editing?.id.orEmpty(),
            familyId = confirmedFamilyId,
            title = title,
            cost = price!!,
            repeatable = draft.repeatable,
            isActive = editing?.isActive ?: true,
            description = draft.description.trim().takeIf { it.isNotEmpty() },
            coverKey = draft.coverKey,
            isFeatured = draft.isFeatured,
            imagePath = if (_uiState.value.removeImage) null else editing?.imagePath
        )
        val selectedBytes = _uiState.value.selectedImageBytes
        val oldPath = editing?.imagePath
        _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            var newPath: String? = null
            val result = try {
                if (selectedBytes != null) {
                    newPath = rewardRepository.uploadRewardImage(confirmedFamilyId, selectedBytes).getOrThrow()
                }
                val updated = reward.copy(imagePath = newPath ?: reward.imagePath)
                if (editing == null) rewardRepository.createReward(updated).map { Unit }
                else rewardRepository.updateReward(updated)
            } catch (error: Exception) { Result.failure(error) }
            result.fold(
                onSuccess = {
                    if (oldPath != null && oldPath != (newPath ?: reward.imagePath)) {
                        rewardRepository.deleteRewardImage(confirmedFamilyId, oldPath).onFailure {
                            rememberCleanup(oldPath)
                            Log.w("RewardManage", "旧奖励图片清理失败：$oldPath", it)
                        }
                    }
                    load(showLoading = false)
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    onSaved()
                },
                onFailure = { error ->
                    newPath?.let { path ->
                        rewardRepository.deleteRewardImage(confirmedFamilyId, path).onFailure {
                            rememberCleanup(path)
                            Log.w("RewardManage", "未引用的新奖励图片清理失败：$path", it)
                        }
                    }
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        errorMessage = saveError(error)
                    )
                }
            )
        }
    }

    fun setActive(reward: Reward, active: Boolean) {
        val familyId = _uiState.value.familyId ?: return
        if (_uiState.value.isBusy) return
        _uiState.value = _uiState.value.copy(operatingRewardId = reward.id, errorMessage = null)
        viewModelScope.launch {
            rewardRepository.setRewardActive(reward.id, familyId, active).fold(
                onSuccess = { load(showLoading = false) },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(errorMessage = saveError(error))
                }
            )
            _uiState.value = _uiState.value.copy(operatingRewardId = null)
        }
    }

    private fun saveError(error: Throwable): String {
        val detail = error.message ?: "请检查网络后重试"
        return if (detail.contains("rewards_one_active_featured_per_family"))
            "本家庭已有启用的大心愿，请先取消原大心愿"
        else "保存奖励失败：$detail"
    }

    companion object {
        val COVER_KEYS = setOf("gift", "toy", "book", "outing", "treat", "wish")
    }
}
