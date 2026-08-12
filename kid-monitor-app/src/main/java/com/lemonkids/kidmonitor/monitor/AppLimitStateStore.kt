package com.lemonkids.kidmonitor.monitor

import android.content.Context
import com.lemonkids.shared.model.AppLimit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

class AppLimitStateStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun loadLimits(): List<AppLimit> {
        val raw = prefs.getString(KEY_LIMITS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<AppLimit>>(raw) }.getOrDefault(emptyList())
    }

    fun saveLimits(limits: List<AppLimit>) {
        prefs.edit().putString(KEY_LIMITS, json.encodeToString(limits)).apply()
    }

    fun getState(packageName: String, today: String = LocalDate.now().toString()): TrackedAppState {
        val raw = prefs.getString(stateKey(packageName), null)
        val saved = raw?.let {
            runCatching { json.decodeFromString<PersistedTrackedAppState>(it).toState() }.getOrNull()
        } ?: TrackedAppState(packageName = packageName, date = today)
        return AppLimitEvaluator.normalizeForDate(saved, today)
    }

    fun saveState(state: TrackedAppState) {
        prefs.edit()
            .putString(stateKey(state.packageName), json.encodeToString(PersistedTrackedAppState.from(state)))
            .apply()
    }

    fun clearActiveSessions(nowMs: Long, today: String = LocalDate.now().toString()) {
        val editor = prefs.edit()
        prefs.all.forEach { (key, value) ->
            if (!key.startsWith(STATE_KEY_PREFIX) || value !is String) return@forEach
            val state = runCatching {
                json.decodeFromString<PersistedTrackedAppState>(value).toState()
            }.getOrNull() ?: return@forEach
            val normalized = AppLimitEvaluator.normalizeForDate(state, today)
            if (normalized.sessionStartMs <= 0L) return@forEach
            val next = normalized.copy(
                sessionStartMs = 0L,
                lastExitMs = nowMs
            )
            editor.putString(key, json.encodeToString(PersistedTrackedAppState.from(next)))
        }
        editor.apply()
    }

    fun startSession(
        packageName: String,
        nowMs: Long,
        today: String,
        cooldownMinutes: Int = 0
    ): TrackedAppState {
        val state = getState(packageName, today)
        val intervalMs = cooldownMinutes * 60_000L
        val shouldResetSessionCycle = state.sessionStartMs == 0L && (
            cooldownMinutes <= 0 ||
                state.lastExitMs <= 0L ||
                nowMs - state.lastExitMs >= intervalMs
            )
        val next = when {
            state.sessionStartMs > 0L -> state
            shouldResetSessionCycle -> state.copy(
                sessionStartMs = nowMs,
                sessionAccumulatedMs = 0L,
                lastExitMs = 0L,
                cooldownUntilMs = if (state.cooldownUntilMs <= nowMs) 0L else state.cooldownUntilMs
            )
            else -> state.copy(sessionStartMs = nowMs, lastExitMs = 0L)
        }
        saveState(next)
        return next
    }

    fun addUsage(packageName: String, deltaMs: Long, today: String): TrackedAppState {
        val state = getState(packageName, today)
        val next = state.copy(todayMs = (state.todayMs + deltaMs).coerceAtLeast(0L))
        saveState(next)
        return next
    }

    fun updateTodayUsage(packageName: String, todayMs: Long, today: String): TrackedAppState {
        val state = getState(packageName, today)
        val next = state.copy(todayMs = todayMs.coerceAtLeast(0L))
        saveState(next)
        return next
    }

    fun endSession(
        packageName: String,
        nowMs: Long,
        today: String,
        cooldownUntilMs: Long? = null
    ): TrackedAppState {
        val state = getState(packageName, today)
        val activeSegmentMs = if (state.sessionStartMs > 0L) {
            (nowMs - state.sessionStartMs).coerceAtLeast(0L)
        } else {
            0L
        }
        val next = state.copy(
            sessionStartMs = 0L,
            sessionAccumulatedMs = (state.sessionAccumulatedMs + activeSegmentMs).coerceAtLeast(0L),
            lastExitMs = if (activeSegmentMs > 0L) nowMs else state.lastExitMs,
            cooldownUntilMs = cooldownUntilMs ?: state.cooldownUntilMs
        )
        saveState(next)
        return next
    }

    @Serializable
    private data class PersistedTrackedAppState(
        val packageName: String,
        val date: String,
        val todayMs: Long = 0L,
        val sessionStartMs: Long = 0L,
        val cooldownUntilMs: Long = 0L,
        val sessionAccumulatedMs: Long = 0L,
        val lastExitMs: Long = 0L
    ) {
        fun toState(): TrackedAppState = TrackedAppState(
            packageName = packageName,
            date = date,
            todayMs = todayMs,
            sessionStartMs = sessionStartMs,
            cooldownUntilMs = cooldownUntilMs,
            sessionAccumulatedMs = sessionAccumulatedMs,
            lastExitMs = lastExitMs
        )

        companion object {
            fun from(state: TrackedAppState): PersistedTrackedAppState = PersistedTrackedAppState(
                packageName = state.packageName,
                date = state.date,
                todayMs = state.todayMs,
                sessionStartMs = state.sessionStartMs,
                cooldownUntilMs = state.cooldownUntilMs,
                sessionAccumulatedMs = state.sessionAccumulatedMs,
                lastExitMs = state.lastExitMs
            )
        }
    }

    companion object {
        private const val PREFS_NAME = "app_limit_state"
        private const val KEY_LIMITS = "active_limits"
        private const val STATE_KEY_PREFIX = "state_"

        private fun stateKey(packageName: String) = "$STATE_KEY_PREFIX$packageName"
    }
}
