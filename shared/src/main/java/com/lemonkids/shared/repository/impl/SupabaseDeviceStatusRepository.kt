package com.lemonkids.shared.repository.impl

import android.util.Log
import com.lemonkids.shared.model.DeviceStatusLog
import com.lemonkids.shared.repository.DeviceStatusRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseDeviceStatusRepository @Inject constructor(
    private val supabase: SupabaseClient
) : DeviceStatusRepository {

    private val postgrest get() = supabase.pluginManager.getPlugin(Postgrest)

    override suspend fun uploadStatusLog(log: DeviceStatusLog): Result<Unit> = runCatching {
        postgrest.from("device_status_logs").insert(log)
    }

    override suspend fun getStatusLogs(childId: String, limit: Int): Result<List<DeviceStatusLog>> =
        runCatching {
            postgrest.from("device_status_logs").select {
                filter { eq("child_id", childId) }
                order("created_at", Order.DESCENDING)
                limit(limit.toLong())
            }.decodeList()
        }

    override fun observeStatusLogs(childId: String, limit: Int): Flow<List<DeviceStatusLog>> = callbackFlow {
        suspend fun fetch() {
            getStatusLogs(childId, limit).fold(
                onSuccess = { trySend(it) },
                onFailure = { Log.e(TAG, "设备状态日志查询失败 childId=$childId", it) }
            )
        }
        fetch()
        while (true) {
            delay(30_000)
            fetch()
        }
        awaitClose()
    }

    companion object {
        private const val TAG = "DeviceStatusRepo"
    }
}
