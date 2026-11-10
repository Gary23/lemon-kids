package com.lemonkids.kidtask.feature.local

/** Run after the old process has exited, so it cannot restore credentials or cached tasks. */
internal suspend fun switchLocalEnvironment(
    clearSession: suspend () -> Unit,
    clearBinding: () -> Boolean,
    clearCaches: () -> Boolean,
    saveAddress: () -> Boolean
) {
    clearSession()
    check(clearBinding()) { "无法清除旧绑定信息" }
    check(clearCaches()) { "无法清除旧任务缓存" }
    check(saveAddress()) { "无法保存本地服务地址" }
}
