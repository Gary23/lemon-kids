package com.lemonkids.kidtask.feature.pomodoro

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.lemonkids.shared.repository.PomodoroRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class PomodoroUiState(
    val isBound: Boolean = false,
    val phase: PomodoroPhase = PomodoroPhase.SETUP,
    val sessionId: String? = null,
    val focusMinutes: Int = PomodoroRules.DEFAULT_FOCUS_MINUTES,
    val breakMinutes: Int = PomodoroRules.DEFAULT_BREAK_MINUTES,
    val remainingMillis: Long = PomodoroRules.DEFAULT_FOCUS_MINUTES * PomodoroRules.MINUTE_MILLIS,
    val alert: PomodoroAlert = PomodoroAlert.NONE,
    val todayDate: String = PomodoroRules.completionDate(System.currentTimeMillis()),
    /** 已知日期的计数；缺失表示该日期尚未取得数据，不能当作 0。 */
    val countsByDate: Map<String, Int> = emptyMap(),
    val countsFetched: Boolean = false,
    val pendingCount: Int = 0,
    val isRefreshingCounts: Boolean = false,
    val syncError: String? = null,
    val storageError: String? = null
) {
    val isRunning: Boolean get() = phase == PomodoroPhase.FOCUS || phase == PomodoroPhase.BREAK
    val todayCount: Int? get() = countsByDate[todayDate]
}

/**
 * 应用内唯一番茄钟协调器。
 *
 * - 阶段与提醒分离；停止提醒只释放声音/震动，不推进阶段。
 * - 专注到点先本地补记一次完成，再切休息；同一 sessionId 最多入账一次。
 * - 所有状态变更在应用主线程串行；进行中的计时只保存在内存，不写磁盘。
 * - 关闭 App 即结束当前轮；切后台、回前台不做任何特殊处理，交给系统自然运行。
 * - 网络只影响待同步标记和远端数量，不阻塞下一轮计时。
 */
@Singleton
class PomodoroManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PomodoroRepository,
    private val localStore: PomodoroLocalStore,
    private val alertPlayer: PomodoroAlertPlayer
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PomodoroUiState())
    val state: StateFlow<PomodoroUiState> = _state.asStateFlow()

    private var childId: String? = null
    private var data = PomodoroStoredData()
    private var ticker: Job? = null
    private var syncJob: Job? = null
    private var alertTimeoutJob: Job? = null
    private var pendingRefresh: Pair<LocalDate, LocalDate>? = null

    fun bind(childId: String) {
        if (this.childId == childId) {
            tick()
            return
        }
        this.childId = childId
        cancelAlertTimeout()
        alertPlayer.stop()
        ticker?.cancel()
        ticker = null
        syncJob?.cancel()
        syncJob = null
        val stored = localStore.read(childId)
        // 进行中的计时不跨进程恢复：重新打开 App 等于退出上一轮番茄钟。
        // 只保留设置、已完成记录和数量缓存；旧版本遗留的会话快照一并丢弃。
        data = stored.copy(session = null)
        persist()
        val now = System.currentTimeMillis()
        publish(now)
        ensureTicker()
        refreshCounts(
            LocalDate.now(PomodoroRules.SHANGHAI).minusDays(31),
            LocalDate.now(PomodoroRules.SHANGHAI).plusDays(31)
        )
    }

    fun setDurations(focusMinutes: Int, breakMinutes: Int) {
        val session = data.session
        if (session != null && (session.phase == PomodoroPhase.FOCUS || session.phase == PomodoroPhase.BREAK)) return
        val sanitized = PomodoroRules.sanitizeSettings(focusMinutes, breakMinutes)
        data = data.copy(settings = sanitized)
        persist()
        publish(System.currentTimeMillis())
    }

    /** 准备态开始专注，也用于等待态的“下一轮”。运行中重复调用不会重复开轮。 */
    fun startFocus() {
        if (childId == null) return
        val session = data.session
        if (session != null && (session.phase == PomodoroPhase.FOCUS || session.phase == PomodoroPhase.BREAK)) return
        cancelAlertTimeout()
        alertPlayer.stop()
        val now = System.currentTimeMillis()
        data = data.copy(
            session = PomodoroRules.startSession(
                focusMinutes = data.settings.focusMinutes,
                breakMinutes = data.settings.breakMinutes,
                nowMillis = now
            )
        )
        persist()
        publish(now)
        ensureTicker()
        startService()
    }

    fun startNextRound() = startFocus()

    /** 停止声音和震动，只清除提醒；休息或等待阶段保持不变。 */
    fun stopAlert() {
        cancelAlertTimeout()
        alertPlayer.stop()
        val session = data.session ?: return
        if (session.alert == PomodoroAlert.NONE) return
        data = data.copy(session = session.copy(alert = PomodoroAlert.NONE))
        persist()
        publish(System.currentTimeMillis())
        if (session.phase == PomodoroPhase.WAITING) stopService()
    }

    /** 结束本轮：停止运行时计时和声音，不删除已完成的番茄。 */
    fun endCycle() {
        cancelAlertTimeout()
        alertPlayer.stop()
        ticker?.cancel()
        ticker = null
        data = data.copy(session = null)
        persist()
        publish(System.currentTimeMillis())
        stopService()
    }

    /**
     * 番茄钟界面结束时释放运行时资源。
     *
     * 当前轮只存在于内存，本方法不写盘、不删除任何历史记录；释放后发布准备态，
     * 避免同一进程内再次打开时读到旧的运行状态。
     */
    fun releaseRuntime() {
        cancelAlertTimeout()
        alertPlayer.stop()
        ticker?.cancel()
        ticker = null
        syncJob?.cancel()
        syncJob = null
        pendingRefresh = null
        data = data.copy(session = null)
        publish(System.currentTimeMillis())
        stopService()
    }

    fun refreshCounts(fromDate: LocalDate = LocalDate.now(PomodoroRules.SHANGHAI).minusDays(31),
                      toDate: LocalDate = LocalDate.now(PomodoroRules.SHANGHAI).plusDays(31)) {
        if (childId == null) return
        val child = childId ?: return
        if (syncJob?.isActive == true) {
            pendingRefresh = fromDate to toDate
            return
        }
        syncJob = scope.launch {
            _state.update { it.copy(isRefreshingCounts = true, syncError = null) }
            var uploadError: String? = null
            val accepted = mutableSetOf<String>()
            val pending = data.completions.filterNot { it.synced }
            var stopUploads = false
            for (completion in pending) {
                val result = repository.recordCompletion(
                    sessionId = completion.sessionId,
                    focusMinutes = completion.focusMinutes,
                    breakMinutes = completion.breakMinutes,
                    startedAt = Instant.ofEpochMilli(completion.startedAtMillis).toString(),
                    completedAt = Instant.ofEpochMilli(completion.completedAtMillis).toString()
                )
                result.onSuccess { accepted += completion.sessionId }
                    .onFailure {
                        uploadError = friendlyError(it)
                        if (isServiceUnavailable(it)) stopUploads = true
                    }
                if (stopUploads) break
            }
            if (childId != child) {
                _state.update { it.copy(isRefreshingCounts = false) }
                return@launch
            }
            val counts = repository.getDailyCounts(fromDate.toString(), toDate.toString())
            if (childId != child) {
                _state.update { it.copy(isRefreshingCounts = false) }
                return@launch
            }
            counts.fold(
                onSuccess = { daily ->
                    val merged = data.cachedCounts.toMutableMap()
                    val rangeDates = mutableSetOf<String>()
                    var date = fromDate
                    while (!date.isAfter(toDate)) {
                        merged[date.toString()] = 0
                        rangeDates += date.toString()
                        date = date.plusDays(1)
                    }
                    daily.forEach { merged[it.date] = it.count }
                    data = data.copy(
                        completions = data.completions.map {
                            if (it.sessionId in accepted && it.completedDate in rangeDates) {
                                it.copy(synced = true)
                            } else {
                                it
                            }
                        },
                        cachedCounts = merged,
                        countsFetchedAtMillis = System.currentTimeMillis()
                    )
                    persist()
                },
                onFailure = {
                    if (uploadError == null) uploadError = friendlyError(it)
                }
            )
            _state.update { it.copy(isRefreshingCounts = false, syncError = uploadError) }
            publish(System.currentTimeMillis())
            val next = pendingRefresh
            pendingRefresh = null
            syncJob = null
            if (next != null) refreshCounts(next.first, next.second)
        }
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        val today = PomodoroRules.completionDate(now)
        val session = data.session ?: run {
            if (_state.value.todayDate != today) publish(now)
            return
        }
        val result = PomodoroRules.advance(session, now)
        if (result.session != session) {
            data = data.copy(session = result.session)
            result.completion?.let { addCompletion(it) }
            persist()
            if (result.completion != null) {
                val todayLocal = LocalDate.now(PomodoroRules.SHANGHAI)
                refreshCounts(todayLocal.minusDays(31), todayLocal.plusDays(31))
            }
            if (result.session.alert != PomodoroAlert.NONE) {
                startAlert()
                startService()
            }
            if (result.session.phase == PomodoroPhase.WAITING) {
                ticker?.cancel()
                ticker = null
            }
        }
        publish(now)
    }

    private fun startAlert() {
        if (!PomodoroRules.hasActiveAlert(data.session)) return
        alertPlayer.start()
        alertTimeoutJob?.cancel()
        alertTimeoutJob = scope.launch {
            delay(ALERT_MAX_MILLIS)
            alertTimeoutJob = null
            if (PomodoroRules.hasActiveAlert(data.session)) stopAlert()
        }
    }

    private fun cancelAlertTimeout() {
        alertTimeoutJob?.cancel()
        alertTimeoutJob = null
    }

    private fun addCompletion(completion: FocusCompletion) {
        if (data.completions.any { it.sessionId == completion.sessionId }) return
        val local = LocalPomodoroCompletion(
            sessionId = completion.sessionId,
            focusMinutes = completion.focusMinutes,
            breakMinutes = completion.breakMinutes,
            startedAtMillis = completion.startedAtMillis,
            completedAtMillis = completion.completedAtMillis,
            completedDate = PomodoroRules.completionDate(completion.completedAtMillis)
        )
        data = data.copy(completions = data.completions + local)
    }

    private fun publish(now: Long) {
        val session = data.session
        val today = PomodoroRules.completionDate(now)
        val counts = displayCounts()
        _state.value = _state.value.copy(
            isBound = childId != null,
            phase = session?.phase ?: PomodoroPhase.SETUP,
            sessionId = session?.sessionId,
            focusMinutes = data.settings.focusMinutes,
            breakMinutes = data.settings.breakMinutes,
            remainingMillis = if (session == null) {
                data.settings.focusMinutes * PomodoroRules.MINUTE_MILLIS
            } else {
                PomodoroRules.remainingMillis(session, now)
            },
            alert = session?.alert ?: PomodoroAlert.NONE,
            todayDate = today,
            countsByDate = counts,
            countsFetched = data.countsFetchedAtMillis > 0L || data.completions.isNotEmpty(),
            pendingCount = data.completions.count { !it.synced },
            syncError = _state.value.syncError,
            storageError = _state.value.storageError
        )
    }

    /**
     * 展示总数 = 远端已同步聚合 + 本机未同步记录。只有成功取得远端数据后才把上传的
     * 记录标为已同步，避免“上传成功但拉取失败”时数量先增后减。
     */
    private fun displayCounts(): Map<String, Int> =
        PomodoroRules.mergeCounts(data.cachedCounts, data.completions)

    private fun persist() {
        val child = childId ?: return
        val success = localStore.write(child, data)
        if (!success) _state.update { it.copy(storageError = "本机存储失败，计时可能无法恢复") }
        else if (_state.value.storageError != null) _state.update { it.copy(storageError = null) }
    }

    private fun ensureTicker() {
        val session = data.session
        val shouldTick = session != null &&
            (session.phase == PomodoroPhase.FOCUS || session.phase == PomodoroPhase.BREAK)
        if (!shouldTick) {
            ticker?.cancel()
            ticker = null
            return
        }
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                tick()
                delay(200)
            }
        }
    }

    private fun startService() {
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PomodoroTimerService::class.java)
            )
        }.onFailure { Log.w(TAG, "无法启动番茄钟前台服务", it) }
    }

    private fun stopService() {
        runCatching { context.stopService(Intent(context, PomodoroTimerService::class.java)) }
    }

    private fun friendlyError(error: Throwable): String {
        val text = error.message.orEmpty()
        return when {
            text.contains("PGRST202") || text.contains("does not exist") ||
                text.contains("Could not find the function") || text.contains("schema cache") ||
                text.contains("record_pomodoro_completion") ->
                "记录服务未就绪，已暂存本机"
            text.contains("Failed to connect") || text.contains("Unable to resolve host") ||
                text.contains("timeout", ignoreCase = true) -> "网络不可用，番茄记录待同步"
            else -> "番茄数量更新失败，点击重试"
        }
    }

    private fun isServiceUnavailable(error: Throwable): Boolean {
        val text = error.message.orEmpty()
        return text.contains("PGRST202") || text.contains("does not exist") ||
            text.contains("Could not find the function") || text.contains("schema cache")
    }

    companion object {
        private const val TAG = "PomodoroManager"
        private const val ALERT_MAX_MILLIS = 60_000L
    }
}
