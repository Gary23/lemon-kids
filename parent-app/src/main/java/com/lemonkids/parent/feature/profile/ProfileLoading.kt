package com.lemonkids.parent.feature.profile

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 仅包住单个页面加载任务；取消仍由生命周期或重试负责传播。 */
internal fun CoroutineScope.launchProfileLoad(
    onError: (Exception) -> Unit,
    block: suspend CoroutineScope.() -> Unit
): Job = launch {
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Log.e("ProfileLoading", "页面数据加载失败", error)
        onError(error)
    }
}
