package com.lemonkids.parent.feature.profile

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
    val errorMessage: String? = null,
    val familyId: String? = null
) {
    val isBusy: Boolean get() = isSaving || operatingRewardId != null
}

@HiltViewModel
class RewardManageViewModel @Inject constructor(
    private val rewardRepository: RewardRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(RewardManageUiState())
    val uiState: StateFlow<RewardManageUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
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
                    errorMessage = null
                )
            },
            onFailure = { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "加载奖励失败：${error.message ?: "请检查网络后重试"}"
                )
            }
        )
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
        val reward = Reward(
            id = editing?.id.orEmpty(),
            familyId = familyId!!,
            title = title,
            cost = price!!,
            repeatable = draft.repeatable,
            isActive = editing?.isActive ?: true,
            description = draft.description.trim().takeIf { it.isNotEmpty() },
            coverKey = draft.coverKey,
            isFeatured = draft.isFeatured
        )
        _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            val result = if (editing == null) rewardRepository.createReward(reward).map { Unit }
                else rewardRepository.updateReward(reward)
            result.fold(
                onSuccess = {
                    load(showLoading = false)
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    onSaved()
                },
                onFailure = { error ->
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
