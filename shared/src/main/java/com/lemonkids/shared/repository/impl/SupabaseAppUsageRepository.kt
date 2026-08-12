package com.lemonkids.shared.repository.impl

import com.lemonkids.shared.model.AppLimit
import com.lemonkids.shared.model.AppUsageRecord
import com.lemonkids.shared.repository.AppUsageRepository
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseAppUsageRepository @Inject constructor(
    private val supabase: SupabaseClient
) : AppUsageRepository {

    private val postgrest get() = supabase.pluginManager.getPlugin(Postgrest)

    override suspend fun uploadUsageRecords(records: List<AppUsageRecord>): Result<Unit> =
        runCatching {
            if (records.isNotEmpty()) postgrest.from("app_usage").insert(records)
        }

    override suspend fun getTodayUsage(childId: String, date: String): List<AppUsageRecord> {
        return postgrest.from("app_usage").select {
            filter { eq("child_id", childId); eq("date", date) }
        }.decodeList()
    }

    override suspend fun getDateRangeUsage(childId: String, startDate: String, endDate: String): List<AppUsageRecord> {
        return postgrest.from("app_usage").select {
            filter {
                eq("child_id", childId)
                gte("date", startDate)
                lte("date", endDate)
            }
            order("date", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
        }.decodeList()
    }

    override fun observeAppLimits(childId: String): Flow<List<AppLimit>> = callbackFlow {
        suspend fun fetch() {
            try {
                val limits = postgrest.from("app_limits").select {
                    filter { eq("child_id", childId); eq("is_active", true) }
                }.decodeList<AppLimit>()
                trySend(limits)
                if (limits.isEmpty()) Log.w("AppUsageRepo", "app_limits 查询返回空列表 childId=$childId")
            } catch (e: Exception) {
                Log.e("AppUsageRepo", "app_limits 查询失败 childId=$childId", e)
            }
        }
        fetch()
        while (true) { delay(30000); fetch() }
    }

    override suspend fun setAppLimit(limit: AppLimit): Result<String> = runCatching {
        postgrest.from("app_limits").insert(limit) { select() }.decodeSingle<AppLimit>().id
    }

    override suspend fun updateAppLimit(limit: AppLimit): Result<Unit> = runCatching {
        postgrest.from("app_limits").update(limit) { filter { eq("id", limit.id) } }
    }

    override suspend fun removeAppLimit(limitId: String): Result<Unit> = runCatching {
        postgrest.from("app_limits").delete { filter { eq("id", limitId) } }
    }
}
