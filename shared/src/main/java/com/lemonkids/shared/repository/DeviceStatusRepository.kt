package com.lemonkids.shared.repository

import com.lemonkids.shared.model.DeviceStatusLog
import kotlinx.coroutines.flow.Flow

interface DeviceStatusRepository {
    suspend fun uploadStatusLog(log: DeviceStatusLog): Result<Unit>
    suspend fun getStatusLogs(childId: String, limit: Int = 100): Result<List<DeviceStatusLog>>
    fun observeStatusLogs(childId: String, limit: Int = 100): Flow<List<DeviceStatusLog>>
}
