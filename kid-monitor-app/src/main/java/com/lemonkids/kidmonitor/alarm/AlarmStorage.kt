package com.lemonkids.kidmonitor.alarm

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Pad 端已确认下发的闹钟。云端版本号用于让更新、删除和重放保持幂等。 */
@Entity(tableName = "device_alarms")
data class DeviceAlarmEntity(
    @PrimaryKey val alarmId: String,
    val revision: Long,
    val triggerAtMillis: Long,
    val title: String,
    val message: String,
    val enabled: Boolean,
    val requiresConfirmation: Boolean,
    val state: String = STATE_SCHEDULED,
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATE_SCHEDULED = "scheduled"
        const val STATE_RINGING = "ringing"
        const val STATE_DISMISSED = "dismissed"
    }
}

@Dao
interface AlarmDao {
    @Upsert
    suspend fun upsert(alarm: DeviceAlarmEntity)

    @Query("SELECT * FROM device_alarms WHERE alarmId = :alarmId LIMIT 1")
    suspend fun get(alarmId: String): DeviceAlarmEntity?

    @Query("UPDATE device_alarms SET state = :state WHERE alarmId = :alarmId")
    suspend fun updateState(alarmId: String, state: String)
}

@Database(entities = [DeviceAlarmEntity::class], version = 1, exportSchema = false)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
}

@Module
@InstallIn(SingletonComponent::class)
object AlarmStorageModule {
    @Provides
    @Singleton
    fun provideAlarmDatabase(@ApplicationContext context: Context): AlarmDatabase =
        Room.databaseBuilder(context, AlarmDatabase::class.java, "lemon_alarm.db").build()

    @Provides
    fun provideAlarmDao(database: AlarmDatabase): AlarmDao = database.alarmDao()
}
