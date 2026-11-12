package com.lemonkids.shared.auth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.gotrue.Auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/** Only task applications opt into this policy; other consumers retain their existing behaviour. */
@Singleton
class TaskSessionGuard @Inject constructor(
    supabase: SupabaseClient,
    @ApplicationContext context: Context,
    coordinator: SessionRecoveryCoordinator
) {
    val enabled = ChildBindingStore.isTaskApplication(context.packageName)
    private val auth = supabase.pluginManager.getPlugin(Auth)
    private val requests = TaskSessionRequests(
        initialize = { auth.awaitInitialization() },
        snapshot = {
            auth.currentSessionOrNull()?.let { session ->
                session.user?.id?.let { TaskSessionSnapshot(it, session.accessToken, session.expiresAt.toEpochMilliseconds()) }
            }
        },
        refresh = { auth.refreshCurrentSession() },
        requireRecovery = coordinator::requireRecovery
    )

    suspend fun ensureReady() {
        if (enabled) requests.ensureReady()
    }

    suspend fun <T> execute(expectedChildId: String? = null, block: suspend () -> T): T =
        if (enabled) requests.execute(expectedChildId, block) else block()
}

internal data class TaskSessionSnapshot(val childId: String, val token: String, val expiresAtMillis: Long)

class TaskSessionUnavailableException : IllegalStateException("登录连接需要恢复，请在弹窗中重试刷新或使用绑定码登录")

/** Application refreshes are serialised; SDK automatic refresh remains enabled and is rechecked. */
internal class TaskSessionRequests(
    private val initialize: suspend () -> Unit,
    private val snapshot: () -> TaskSessionSnapshot?,
    private val refresh: suspend () -> Unit,
    private val requireRecovery: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
    private val recoveryTimeoutMillis: Long = 8_000L
) {
    private val mutex = Mutex()
    // Coalesce failures too, without keeping credentials outside the SDK snapshot.
    private var failedAt: Long? = null
    private var failedSnapshot: TaskSessionSnapshot? = null

    suspend fun ensureReady(): TaskSessionSnapshot = recover(null, null)

    suspend fun <T> execute(expectedChildId: String?, block: suspend () -> T): T {
        val session = recover(expectedChildId, null)
        try {
            return block().also { checkIdentity(session.childId) }
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            if (error is CancellationException) throw error
            if (!isJwtRejection(error)) throw error
            // Only a PostgREST JWT rejection is known to precede business SQL. Never replay
            // timeouts, permission failures or unknown write responses.
            val recovered = recover(session.childId, session.token)
            try {
                return block().also { checkIdentity(recovered.childId) }
            } catch (retryError: Exception) {
                currentCoroutineContext().ensureActive()
                if (retryError is CancellationException) throw retryError
                if (isJwtRejection(retryError)) unavailable()
                throw retryError
            }
        }
    }

    private suspend fun recover(expectedChildId: String?, rejectedToken: String?): TaskSessionSnapshot {
        try {
            return withTimeout(recoveryTimeoutMillis) {
                initialize()
                mutex.withLock {
                    currentCoroutineContext().ensureActive()
                    val before = snapshot() ?: unavailable()
                    if (expectedChildId != null && before.childId != expectedChildId) accountChanged()
                    val fresh = before.expiresAtMillis > now() + EXPIRY_MARGIN_MILLIS
                    // Another request (or the SDK) may already have replaced the rejected JWT.
                    if (fresh && (rejectedToken == null || rejectedToken != before.token)) return@withLock before
                    if (failedSnapshot == before && failedAt?.let { now() - it < FAILURE_COOLDOWN_MILLIS } == true) unavailable()
                    try {
                        refresh()
                        currentCoroutineContext().ensureActive()
                    } catch (error: Exception) {
                        currentCoroutineContext().ensureActive()
                        if (error is CancellationException) throw error
                        failedAt = now()
                        failedSnapshot = snapshot()
                        unavailable()
                    }
                    val after = snapshot() ?: unavailable()
                    if (after.childId != before.childId) accountChanged()
                    if (after.expiresAtMillis <= now() || (rejectedToken != null && after.token == rejectedToken)) unavailable()
                    failedAt = null
                    failedSnapshot = null
                    after
                }
            }
        } catch (error: TimeoutCancellationException) {
            // Own recovery deadline is a visible failure; parent/job cancellation still propagates.
            currentCoroutineContext().ensureActive()
            unavailable()
        }
    }

    private suspend fun checkIdentity(childId: String) {
        currentCoroutineContext().ensureActive()
        val current = snapshot() ?: unavailable()
        if (current.childId != childId) accountChanged()
    }

    private fun unavailable(): Nothing {
        requireRecovery()
        throw TaskSessionUnavailableException()
    }

    private fun accountChanged(): Nothing = throw CancellationException("登录账号已变化，停止旧账号请求")

    companion object {
        private const val EXPIRY_MARGIN_MILLIS = 30_000L
        private const val FAILURE_COOLDOWN_MILLIS = 2_000L

        internal fun isJwtRejection(error: Throwable): Boolean {
            if (error !is RestException || error.statusCode != 401) return false
            // Do not use arbitrary exception.message (which includes URLs/request details).
            // supabase-kt 2.6.1 maps PostgREST.message to error and details/hint to
            // description; the PGRST code is not preserved by its exception parser.
            val jwtMessages = setOf("JWT expired", "JWT is expired", "Invalid JWT", "JWT is invalid", "Invalid JWT signature", "JWSError JWSInvalidSignature")
            return error.error == "PGRST301" || error.error == "PGRST303" ||
                jwtMessages.any { it.equals(error.error.trim(), ignoreCase = true) }
        }
    }
}
