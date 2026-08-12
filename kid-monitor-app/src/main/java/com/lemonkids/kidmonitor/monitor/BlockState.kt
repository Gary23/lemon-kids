package com.lemonkids.kidmonitor.monitor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object BlockState {
    data class BlockInfo(val title: String, val message: String)

    var blockInfo by mutableStateOf<BlockInfo?>(null)
        private set

    fun set(title: String, message: String) {
        blockInfo = BlockInfo(title, message)
    }

    fun clear() {
        blockInfo = null
    }

    val isBlocking: Boolean get() = blockInfo != null
}
