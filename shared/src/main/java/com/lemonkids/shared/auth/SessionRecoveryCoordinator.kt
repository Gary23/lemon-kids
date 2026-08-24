package com.lemonkids.shared.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 在任意功能请求发现本地会话不可用时，通知应用根层展示会话恢复弹层。
 *
 * 使用状态而不是一次性事件，避免弹层、录音等异步 UI 重组时漏掉恢复提示。
 */
@Singleton
class SessionRecoveryCoordinator @Inject constructor() {
    private val _required = MutableStateFlow(false)
    val required: StateFlow<Boolean> = _required.asStateFlow()

    fun requireRecovery() {
        _required.value = true
    }

    fun markRecovered() {
        _required.value = false
    }
}
