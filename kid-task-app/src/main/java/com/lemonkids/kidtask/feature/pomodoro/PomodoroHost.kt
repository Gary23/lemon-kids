package com.lemonkids.kidtask.feature.pomodoro

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 位于 Activity 根层，切换任务页面或服务器设置不会销毁计时 ViewModel。 */
@Composable
fun PomodoroHost(
    viewModel: PomodoroViewModel = hiltViewModel(),
    content: @Composable (PomodoroUiState, () -> Unit) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var display by rememberSaveable { mutableStateOf(PomodoroDisplay.HIDDEN) }
    var displayGeneration by rememberSaveable { mutableStateOf(state.bindingGeneration) }
    LaunchedEffect(state.isBound, state.sessionId, state.bindingGeneration) {
        if (displayGeneration != state.bindingGeneration || !state.isBound ||
            (display == PomodoroDisplay.MINI && state.sessionId == null)) {
            display = PomodoroDisplay.HIDDEN
        }
        displayGeneration = state.bindingGeneration
    }
    Box(Modifier.fillMaxSize()) {
        content(state) { if (state.isBound) display = PomodoroDisplay.LARGE }
        if (display == PomodoroDisplay.MINI && state.isBound && state.sessionId != null) {
            PomodoroMiniOverlay(
                state = state,
                onOpen = { display = PomodoroDisplay.LARGE },
                modifier = Modifier.align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)
            )
        }
        if (display == PomodoroDisplay.LARGE && state.isBound) {
            PomodoroOverlay(
                state = state,
                onClose = { display = PomodoroDisplayRules.afterClose(state) },
                onStart = viewModel::startFocus,
                onStartBreak = viewModel::startBreak,
                onPauseResume = viewModel::pauseResume,
                onNextRound = viewModel::startNextRound,
                onStopAlert = viewModel::stopAlert,
                onEndCycle = { viewModel.endCycle(); display = PomodoroDisplay.HIDDEN },
                onSaveSettings = viewModel::setDurations,
                onRefresh = viewModel::refreshCounts
            )
        }
    }
}

enum class PomodoroDisplay { HIDDEN, LARGE, MINI }

object PomodoroDisplayRules {
    fun afterClose(state: PomodoroUiState): PomodoroDisplay =
        if (state.hasUnfinishedRound || state.alert != PomodoroAlert.NONE) PomodoroDisplay.MINI
        else PomodoroDisplay.HIDDEN
}
