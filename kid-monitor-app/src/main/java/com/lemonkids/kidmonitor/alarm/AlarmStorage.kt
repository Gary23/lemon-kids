package com.lemonkids.kidmonitor.alarm

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.migration.Migration
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import javax.inject.Singleton

/** Pad 端已确认下发的闹钟。云端版本号用于让更新、删除和重放保持幂等。 */
@Entity(tableName = "device_alarms")
data class DeviceAlarmEntity(
    @PrimaryKey val alarmId: String,
    val revision: Long,
    /** 当前已登记的下一次响铃时间；响铃结束后推进到日期范围内的下一天。 */
    val triggerAtMillis: Long,
    /** 日期范围的末日（保留其每日提醒时刻），不是单次响铃的停止时间。 */
    val endAtMillis: Long,
    val timezone: String,
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

    /** 只暴露 Pad 当前仍会执行或正在响铃的记录；禁用/删除版本作为本地墓碑保留。 */
    @Query("""
        SELECT * FROM device_alarms
        WHERE enabled = 1
          AND endAtMillis >= :nowMillis
          AND (triggerAtMillis >= :nowMillis OR state = 'ringing')
          AND state IN ('scheduled', 'ringing')
        ORDER BY triggerAtMillis ASC
    """)
    fun observeEffectiveAlarms(nowMillis: Long = System.currentTimeMillis()): Flow<List<DeviceAlarmEntity>>
}

@Database(entities = [DeviceAlarmEntity::class], version = 3, exportSchema = false)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
}

@Module
@InstallIn(SingletonComponent::class)
object AlarmStorageModule {
    @Provides
    @Singleton
    fun provideAlarmDatabase(@ApplicationContext context: Context): AlarmDatabase =
        Room.databaseBuilder(context, AlarmDatabase::class.java, "lemon_alarm.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides
    fun provideAlarmDao(database: AlarmDatabase): AlarmDao = database.alarmDao()

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 历史闹钟没有结束时间，沿用既有的最长响铃 10 分钟作为结束边界。
            database.execSQL("ALTER TABLE device_alarms ADD COLUMN endAtMillis INTEGER NOT NULL DEFAULT 0")
            database.execSQL("UPDATE device_alarms SET endAtMillis = triggerAtMillis + 600000 WHERE endAtMillis = 0")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 旧记录没有时区；远程闹钟此前已按中国时区创建，使用该值可保持原有触发时刻。
            database.execSQL("ALTER TABLE device_alarms ADD COLUMN timezone TEXT NOT NULL DEFAULT 'Asia/Shanghai'")
        }
    }
}
