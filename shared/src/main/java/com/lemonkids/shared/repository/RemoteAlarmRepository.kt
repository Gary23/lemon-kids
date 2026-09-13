package com.lemonkids.shared.repository

import com.lemonkids.shared.model.AlarmDelivery
import com.lemonkids.shared.model.AlarmEvent
import com.lemonkids.shared.model.MonitorDevice
import com.lemonkids.shared.model.ParentAlarmStatus
import com.lemonkids.shared.model.RemoteAlarm
import kotlinx.coroutines.flow.Flow

interface RemoteAlarmRepository {
    fun observeParentAlarms(childId: String): Flow<List<ParentAlarmStatus>>
    suspend fun getAlarmsForDevice(deviceId: String): Result<List<RemoteAlarm>>
    suspend fun getMonitorDevices(familyId: String, childId: String): Result<List<MonitorDevice>>
    suspend fun createAlarm(alarm: RemoteAlarm): Result<Unit>
    suspend fun updateAlarm(alarm: RemoteAlarm): Result<Unit>
    suspend fun updateDelivery(delivery: AlarmDelivery): Result<Unit>
    suspend fun recordEvent(event: AlarmEvent): Result<Unit>
}
