package com.lemonkids.shared.repository.impl

import android.util.Log
import com.lemonkids.shared.model.AlarmDelivery
import com.lemonkids.shared.model.AlarmEvent
import com.lemonkids.shared.model.MonitorDevice
import com.lemonkids.shared.model.ParentAlarmStatus
import com.lemonkids.shared.model.RemoteAlarm
import com.lemonkids.shared.repository.RemoteAlarmRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseRemoteAlarmRepository @Inject constructor(
    private val supabase: SupabaseClient
) : RemoteAlarmRepository {
    private val postgrest get() = supabase.pluginManager.getPlugin(Postgrest)
    private val alarmRefreshEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)

    override fun observeParentAlarms(childId: String): Flow<List<ParentAlarmStatus>> = callbackFlow {
        val fetchMutex = Mutex()
        suspend fun fetch() {
            fetchMutex.withLock { runCatching {
                val alarms = postgrest.from("alarms").select {
                    filter { eq("child_id", childId) }
                    order("trigger_at", Order.ASCENDING)
                }.decodeList<RemoteAlarm>()
                val deliveries = postgrest.from("alarm_deliveries").select {
                    filter { eq("child_id", childId) }
                }.decodeList<AlarmDelivery>()
                val byAlarm = deliveries.associateBy { it.alarmId }
                alarms.filter { it.deletedAt == null }.map { ParentAlarmStatus(it, byAlarm[it.id]) }
            }.onSuccess { trySend(it) }
                .onFailure { Log.e(TAG, "读取家长端闹钟失败 childId=$childId", it) }
            }
        }
        fetch()
        launch { alarmRefreshEvents.filter { it == childId }.collect { fetch() } }
        while (true) {
            delay(PARENT_REFRESH_MILLIS)
            fetch()
        }
        awaitClose()
    }

    override suspend fun getAlarmsForDevice(deviceId: String): Result<List<RemoteAlarm>> = runCatching {
        postgrest.from("alarms").select {
            filter { eq("target_device_id", deviceId) }
            order("trigger_at", Order.ASCENDING)
        }.decodeList()
    }

    override suspend fun getMonitorDevices(familyId: String, childId: String): Result<List<MonitorDevice>> = runCatching {
        postgrest.rpc(
            function = "get_monitor_devices",
            parameters = mapOf("p_family_id" to familyId, "p_child_id" to childId)
        ).decodeList()
    }

    override suspend fun createAlarm(alarm: RemoteAlarm): Result<Unit> = runCatching {
        postgrest.from("alarms").insert(alarm)
        Unit
    }.onSuccess { alarmRefreshEvents.tryEmit(alarm.childId) }

    override suspend fun updateAlarm(alarm: RemoteAlarm): Result<Unit> = runCatching {
        postgrest.from("alarms").update(alarm) { filter { eq("id", alarm.id) } }
        Unit
    }.onSuccess { alarmRefreshEvents.tryEmit(alarm.childId) }

    override suspend fun updateDelivery(delivery: AlarmDelivery): Result<Unit> = runCatching {
        postgrest.from("alarm_deliveries").update(delivery) {
            filter {
                eq("alarm_id", delivery.alarmId)
                eq("device_id", delivery.deviceId)
            }
        }
    }

    override suspend fun recordEvent(event: AlarmEvent): Result<Unit> = runCatching {
        postgrest.from("alarm_events").insert(event)
    }

    companion object {
        private const val TAG = "RemoteAlarmRepo"
        // 家长自己的写操作会立即更新 UI；此处只用于 Pad 端回执等跨端状态校准。
        private const val PARENT_REFRESH_MILLIS = 60_000L
    }
}
