package com.lemonkids.kidtask.feature.pomodoro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.shared.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PomodoroViewModel @Inject constructor(
    private val manager: PomodoroManager,
    private val authRepository: AuthRepository
) : ViewModel() {

    val uiState: StateFlow<PomodoroUiState> = manager.state

    init {
        bindCurrentUser()
    }

    fun bindCurrentUser() {
        authRepository.currentUserId?.let {
            manager.bind(it)
            return
        }
        viewModelScope.launch {
            authRepository.observeCurrentUser().first()?.uid?.let(manager::bind)
        }
    }

    fun startFocus() = manager.startFocus()
    fun startNextRound() = manager.startNextRound()
    fun stopAlert() = manager.stopAlert()
    fun endCycle() = manager.endCycle()
    fun setDurations(focusMinutes: Int, breakMinutes: Int) =
        manager.setDurations(focusMinutes, breakMinutes)
    fun refreshCounts() = manager.refreshCounts()

    override fun onCleared() {
        // 番茄钟界面结束时，运行中的计时随内存对象一起释放；不涉及任何持久记录。
        manager.releaseRuntime()
        super.onCleared()
    }
}
