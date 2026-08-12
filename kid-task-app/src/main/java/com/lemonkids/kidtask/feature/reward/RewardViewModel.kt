package com.lemonkids.kidtask.feature.reward

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class RewardUiState(
    val rewards: List<RewardUiItem> = emptyList(),
    val points: Int = 0,
    val pointRecords: List<PointRecordUiItem> = emptyList(),
    val isLoading: Boolean = false
)

data class RewardUiItem(
    val id: String,
    val title: String,
    val cost: Int,
    val affordable: Boolean,
    val redeemed: Boolean
)

data class PointRecordUiItem(
    val id: String,
    val reason: String,
    val amount: Int,
    val balance: Int,
    val time: String
)

@HiltViewModel
class RewardViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(RewardUiState())
    val uiState: StateFlow<RewardUiState> = _uiState.asStateFlow()

    fun redeemReward(rewardId: String) {
        val reward = _uiState.value.rewards.find { it.id == rewardId } ?: return
        val updated = _uiState.value.rewards.map {
            if (it.id == rewardId) it.copy(redeemed = true) else it
        }
        val newPoints = _uiState.value.points - reward.cost
        _uiState.value = _uiState.value.copy(
            rewards = updated,
            points = newPoints
        )
    }
}
