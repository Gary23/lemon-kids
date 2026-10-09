package com.lemonkids.kidtask.feature.reward

import android.util.Log
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.shared.model.RewardSnapshot
import com.lemonkids.shared.model.RewardRedemptionStatus
import com.lemonkids.shared.repository.AuthRepository
import com.lemonkids.shared.repository.RewardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class RewardAction { REDEEM, USE, CANCEL }

data class RewardConfirmation(val action: RewardAction, val id: String, val title: String, val cost: Int)

internal data class RewardImageEntry(
    val familyId: String,
    val path: String,
    val url: String,
    val signedAtMillis: Long
)

private const val IMAGE_URL_LIFETIME_MILLIS = 10 * 60 * 1000L
private const val IMAGE_URL_RESIGN_MILLIS = 9 * 60 * 1000L

internal fun retainRewardImageEntries(
    family: String,
    snapshot: RewardSnapshot,
    entries: Map<String, RewardImageEntry>,
    nowMillis: Long
): Map<String, RewardImageEntry> = snapshot.rewards.mapNotNull { reward ->
    val entry = entries[reward.id] ?: return@mapNotNull null
    val age = nowMillis - entry.signedAtMillis
    if (reward.familyId == family && entry.familyId == family &&
        reward.imagePath == entry.path && age in 0 until IMAGE_URL_LIFETIME_MILLIS
    ) reward.id to entry else null
}.toMap()

data class RewardUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val snapshot: RewardSnapshot? = null,
    val imageUrls: Map<String, String> = emptyMap(),
    val error: String? = null,
    val feedback: String? = null,
    val confirmation: RewardConfirmation? = null,
    val submitting: Boolean = false,
    /** 网络结果未知时保留请求 ID，同一意图只能以此 ID 重试。 */
    val pendingRequestId: String? = null,
    val pendingRewardId: String? = null
)

@HiltViewModel
class RewardViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val rewardRepository: RewardRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(RewardUiState())
    val uiState = _uiState.asStateFlow()
    private var childId: String? = null
    private var familyId: String? = null
    private var imageEntries: Map<String, RewardImageEntry> = emptyMap()

    init { refresh() }

    fun refresh() {
        if (_uiState.value.refreshing || _uiState.value.submitting) return
        _uiState.value = _uiState.value.copy(refreshing = true, error = null)
        viewModelScope.launch {
            val user = authRepository.observeCurrentUser().first()
            val child = user?.uid ?: authRepository.currentUserId
            val family = user?.familyId
            if (child.isNullOrBlank() || family.isNullOrBlank()) {
                childId = null
                familyId = null
                imageEntries = emptyMap()
                _uiState.value = _uiState.value.copy(loading = false, refreshing = false,
                    snapshot = null, imageUrls = emptyMap(), confirmation = null,
                    pendingRequestId = null, pendingRewardId = null,
                    error = "孩子或家庭信息暂不可用，请重新登录后重试")
                return@launch
            }
            if (childId != null && (childId != child || familyId != family)) {
                imageEntries = emptyMap()
                _uiState.value = _uiState.value.copy(snapshot = null, imageUrls = emptyMap(), confirmation = null,
                    pendingRequestId = null, pendingRewardId = null)
            }
            childId = child
            familyId = family
            rewardRepository.getRewardSnapshot(family, child).fold(
                onSuccess = { snapshot ->
                    imageEntries = retainRewardImageEntries(family, snapshot, imageEntries, SystemClock.elapsedRealtime())
                    val pendingConfirmed = _uiState.value.pendingRequestId?.let { id ->
                        snapshot.redemptions.any { it.id == id }
                    } == true
                    _uiState.value = _uiState.value.copy(
                        loading = false, snapshot = snapshot, imageUrls = imageEntries.mapValues { it.value.url }, error = null,
                        pendingRequestId = if (pendingConfirmed) null else _uiState.value.pendingRequestId,
                        pendingRewardId = if (pendingConfirmed) null else _uiState.value.pendingRewardId,
                        confirmation = if (pendingConfirmed) null else _uiState.value.confirmation,
                        feedback = if (pendingConfirmed) "兑换成功，星星已扣除" else _uiState.value.feedback
                    )
                    rewardRepository.requestPointsRefresh()
                    updateRewardImageUrls(family, snapshot)
                    _uiState.value = _uiState.value.copy(refreshing = false)
                },
                onFailure = { error ->
                    Log.e("RewardViewModel", "奖励页读取奖励快照失败", error)
                    _uiState.value = _uiState.value.copy(
                        loading = false, refreshing = false,
                        error = "奖励读取失败，请稍后重试；若持续失败，请反馈日志"
                    )
                }
            )
        }
    }

    fun confirmRedeem(rewardId: String) {
        val state = _uiState.value
        val snapshot = state.snapshot ?: return
        val reward = snapshot.rewards.firstOrNull { it.id == rewardId } ?: return
        if (state.submitting || state.error != null || state.pendingRequestId != null && state.pendingRewardId != rewardId) return
        if (reward.redemptionBlockReason(snapshot.balance, snapshot.unavailableOneTimeIds) != null) return
        _uiState.value = state.copy(confirmation = RewardConfirmation(RewardAction.REDEEM, rewardId, reward.title, reward.cost))
    }

    fun confirmUse(redemptionId: String) = confirmHeldAction(redemptionId, RewardAction.USE)
    fun confirmCancel(redemptionId: String) = confirmHeldAction(redemptionId, RewardAction.CANCEL)

    private fun confirmHeldAction(redemptionId: String, action: RewardAction) {
        val state = _uiState.value
        if (state.submitting || state.error != null || state.pendingRequestId != null) return
        val item = state.snapshot?.redemptions?.firstOrNull {
            it.id == redemptionId && it.status == RewardRedemptionStatus.HELD
        } ?: return
        _uiState.value = state.copy(confirmation = RewardConfirmation(action, item.id, item.title, item.cost))
    }

    fun dismissConfirmation() {
        if (!_uiState.value.submitting) _uiState.value = _uiState.value.copy(confirmation = null)
    }

    fun clearFeedback() { _uiState.value = _uiState.value.copy(feedback = null) }

    fun submit() {
        val state = _uiState.value
        val action = state.confirmation ?: return
        val child = childId ?: return
        val family = familyId ?: return
        if (state.submitting) return
        viewModelScope.launch {
            val requestId = if (action.action == RewardAction.REDEEM) {
                state.pendingRequestId ?: UUID.randomUUID().toString()
            } else null
            _uiState.value = _uiState.value.copy(
                submitting = true, error = null, feedback = null,
                pendingRequestId = requestId, pendingRewardId = if (requestId != null) action.id else null
            )
            val result = when (action.action) {
                RewardAction.REDEEM -> rewardRepository.redeemReward(action.id, child, requestId!!)
                RewardAction.CANCEL -> rewardRepository.cancelRewardRedemption(action.id, child)
                RewardAction.USE -> rewardRepository.useRewardRedemption(action.id, child)
            }
            // 成功和超时都重新读取服务端事实；网络结果不明时保留同一个兑换请求 ID。
            val snapshotResult = rewardRepository.getRewardSnapshot(family, child)
            val snapshot = snapshotResult.getOrNull()
            val redeemed = requestId != null && snapshot?.redemptions?.any { it.id == requestId } == true
            val actionApplied = when (action.action) {
                RewardAction.REDEEM -> redeemed
                RewardAction.CANCEL -> snapshot?.redemptions?.firstOrNull { it.id == action.id }?.status == RewardRedemptionStatus.CANCELLED
                RewardAction.USE -> snapshot?.redemptions?.firstOrNull { it.id == action.id }?.status == RewardRedemptionStatus.USED
            }
            if (snapshot != null) rewardRepository.requestPointsRefresh()
            if (snapshot != null) {
                imageEntries = retainRewardImageEntries(family, snapshot, imageEntries, SystemClock.elapsedRealtime())
            }
            _uiState.value = _uiState.value.copy(
                submitting = false,
                snapshot = snapshot ?: _uiState.value.snapshot,
                imageUrls = if (snapshot != null) imageEntries.mapValues { it.value.url } else _uiState.value.imageUrls,
                confirmation = if (snapshot != null && actionApplied) null else action,
                pendingRequestId = if (action.action == RewardAction.REDEEM && !redeemed) requestId else null,
                pendingRewardId = if (action.action == RewardAction.REDEEM && !redeemed) action.id else null,
                feedback = if (snapshot != null && actionApplied) when (action.action) {
                    RewardAction.REDEEM -> "兑换成功，星星已扣除"
                    RewardAction.CANCEL -> "兑换已取消，${action.cost} 颗星星已退回"
                    RewardAction.USE -> "已标记为使用，记录已更新"
                } else null,
                error = if (snapshot == null) "操作结果暂无法确认，请刷新或使用同一请求重试" else if (!actionApplied) {
                    rewardFailureMessage(result.exceptionOrNull()?.message)
                } else null
            )
            if (snapshot != null) {
                updateRewardImageUrls(family, snapshot)
            }
        }
    }

    private suspend fun updateRewardImageUrls(family: String, snapshot: RewardSnapshot) {
        val now = SystemClock.elapsedRealtime()
        val needsSigning = snapshot.rewards.filter { reward ->
            !reward.imagePath.isNullOrBlank() && reward.familyId == family &&
                (imageEntries[reward.id]?.let { now - it.signedAtMillis < IMAGE_URL_RESIGN_MILLIS } != true)
        }
        if (needsSigning.isEmpty()) return
        val urls = resolveRewardImageUrls(family, snapshot.copy(rewards = needsSigning),
            rewardRepository::createRewardImageUrl)
        if (_uiState.value.snapshot !== snapshot || familyId != family) return
        val signedAt = SystemClock.elapsedRealtime()
        val retained = retainRewardImageEntries(family, snapshot, imageEntries, signedAt)
        imageEntries = retained + needsSigning.mapNotNull { reward ->
            val path = reward.imagePath ?: return@mapNotNull null
            val url = urls[reward.id] ?: return@mapNotNull null
            reward.id to RewardImageEntry(family, path, url, signedAt)
        }.toMap()
        _uiState.value = _uiState.value.copy(imageUrls = imageEntries.mapValues { it.value.url })
    }
}

internal suspend fun resolveRewardImageUrls(
    family: String,
    snapshot: RewardSnapshot,
    sign: suspend (String, String) -> Result<String>
): Map<String, String> = coroutineScope {
    snapshot.rewards.mapNotNull { reward ->
        val path = reward.imagePath?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        if (reward.familyId != family) return@mapNotNull null
        async {
            val url = try {
                sign(family, path).getOrNull()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            url?.let { reward.id to it }
        }
    }.awaitAll().filterNotNull().toMap()
}
