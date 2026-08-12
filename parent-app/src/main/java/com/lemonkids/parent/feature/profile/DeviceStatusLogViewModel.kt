package com.lemonkids.parent.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.shared.model.DeviceStatusLog
import com.lemonkids.shared.repository.AuthRepository
import com.lemonkids.shared.repository.ChildUserInfo
import com.lemonkids.shared.repository.DeviceStatusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceStatusLogUiState(
    val isLoading: Boolean = true,
    val children: List<ChildUserInfo> = emptyList(),
    val selectedChild: ChildUserInfo? = null,
    val logs: List<DeviceStatusLog> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class DeviceStatusLogViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val deviceStatusRepository: DeviceStatusRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceStatusLogUiState())
    val uiState: StateFlow<DeviceStatusLogUiState> = _uiState.asStateFlow()

    private var logsJob: Job? = null

    init {
        loadChildren()
    }

    private fun loadChildren() {
        viewModelScope.launch {
            val user = authRepository.observeCurrentUser().first()
            val familyId = user?.familyId
            if (familyId == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "未找到家庭信息"
                )
                return@launch
            }

            authRepository.fetchChildUsers(familyId).fold(
                onSuccess = { children ->
                    val selected = _uiState.value.selectedChild ?: children.firstOrNull()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        children = children,
                        selectedChild = selected,
                        errorMessage = null
                    )
                    selected?.let { observeLogs(it.uid) }
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "加载孩子列表失败"
                    )
                }
            )
        }
    }

    fun selectChild(child: ChildUserInfo) {
        _uiState.value = _uiState.value.copy(selectedChild = child, logs = emptyList())
        observeLogs(child.uid)
    }

    fun refresh() {
        val childId = _uiState.value.selectedChild?.uid ?: return
        viewModelScope.launch {
            deviceStatusRepository.getStatusLogs(childId).fold(
                onSuccess = { logs -> _uiState.value = _uiState.value.copy(logs = logs, errorMessage = null) },
                onFailure = { _uiState.value = _uiState.value.copy(errorMessage = "刷新设备日志失败") }
            )
        }
    }

    private fun observeLogs(childId: String) {
        logsJob?.cancel()
        logsJob = viewModelScope.launch {
            deviceStatusRepository.observeStatusLogs(childId).collect { logs ->
                _uiState.value = _uiState.value.copy(logs = logs, errorMessage = null)
            }
        }
    }
}
