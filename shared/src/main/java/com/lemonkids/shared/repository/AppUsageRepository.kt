package com.lemonkids.shared.repository

import com.lemonkids.shared.model.AppLimit
import com.lemonkids.shared.model.AppUsageRecord
import kotlinx.coroutines.flow.Flow

interface AppUsageRepository {
    suspend fun uploadUsageRecords(records: List<AppUsageRecord>): Result<Unit>
    suspend fun getTodayUsage(childId: String, date: String): List<AppUsageRecord>
    suspend fun getDateRangeUsage(childId: String, startDate: String, endDate: String): List<AppUsageRecord>
    fun observeAppLimits(childId: String): Flow<List<AppLimit>>
    suspend fun setAppLimit(limit: AppLimit): Result<String>
    suspend fun updateAppLimit(limit: AppLimit): Result<Unit>
    suspend fun removeAppLimit(limitId: String): Result<Unit>
}
