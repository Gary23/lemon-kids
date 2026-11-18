package com.lemonkids.kidtask.feature.pomodoro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.shared.repository.AuthRepository
import com.lemonkids.shared.auth.SessionRecoveryCoordinator
import com.lemonkids.shared.repository.TaskRepository
import com.lemonkids.shared.repository.RewardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PomodoroViewModel @Inject constructor(
    private val manager: PomodoroManager,
    private val authRepository: AuthRepository,
    private val recoveryCoordinator: SessionRecoveryCoordinator,
    private val taskRepository: TaskRepository,
    private val rewardRepository: RewardRepository
) : ViewModel() {

    val uiState: StateFlow<PomodoroUiState> = manager.state
    val requiresSessionRecovery: StateFlow<Boolean> = recoveryCoordinator.required

    init {
        viewModelScope.launch {
            authRepository.observeCurrentUser().map { it?.uid }.distinctUntilChanged().collect { uid ->
                if (uid == null) manager.unbind() else manager.bind(uid)
            }
        }
        viewModelScope.launch {
            var recoveryWasRequired = false
            recoveryCoordinator.required.collect { required ->
                if (required) {
                    recoveryWasRequired = true
                } else if (recoveryWasRequired) {
                    recoveryWasRequired = false
                    // Same UID re-login does not emit a distinct user; explicitly retry old
                    // counts/uploads and wake task observers after the recovery dialog succeeds.
                    if (authRepository.currentUserId != null) {
                        manager.refreshCounts()
                        taskRepository.refreshTasks()
                        rewardRepository.requestPointsRefresh()
                    }
                }
            }
        }
    }

    fun startFocus() = manager.startFocus()
    fun startNextRound() = manager.startNextRound()
    fun startBreak() = manager.startBreak()
    fun pauseResume() = manager.pauseResume()
    fun stopAlert() = manager.stopAlert()
    fun endCycle() = manager.endCycle()
    fun setDurations(focusMinutes: Int, breakMinutes: Int) =
        manager.setDurations(focusMinutes, breakMinutes)
    fun refreshCounts() = manager.refreshCounts()

    override fun onCleared() {
        // 番茄钟界面结束时，运行中的计时随内存对象一起释放；不涉及任何持久记录。
        manager.unbind()
        super.onCleared()
    }
}
