package com.lemonkids.kidtask.feature.pomodoro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.shared.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PomodoroViewModel @Inject constructor(
    private val manager: PomodoroManager,
    private val authRepository: AuthRepository
) : ViewModel() {

    val uiState: StateFlow<PomodoroUiState> = manager.state

    init {
        viewModelScope.launch {
            authRepository.observeCurrentUser().map { it?.uid }.distinctUntilChanged().collect { uid ->
                if (uid == null) manager.unbind() else manager.bind(uid)
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
