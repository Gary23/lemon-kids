package com.lemonkids.kidtask.feature.pomodoro

import android.content.Context
import com.lemonkids.shared.di.SupabaseEnvironment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 番茄钟本机持久层。
 *
 * 每个“孩子 + 环境”使用同一个 JSON 文档保存设置、已完成记录和远端数量缓存。
 * 进行中的计时快照只存在于内存，绝不落盘：App 关闭后重新打开即视为退出番茄钟，
 * 不恢复、不补算、不补响。未同步记录不会提交到其他账号或环境。
 */
@Singleton
class PomodoroLocalStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun read(childId: String): PomodoroStoredData {
        val raw = preferences.getString(key(childId), null) ?: return PomodoroStoredData()
        return runCatching { json.decodeFromString<PomodoroStoredData>(raw) }
            .getOrElse { PomodoroStoredData() }
    }

    fun write(childId: String, data: PomodoroStoredData): Boolean {
        // 防御性剥离：任何调用方都无法把进行中的计时快照写入磁盘。
        val encoded = runCatching {
            json.encodeToString(PomodoroStoredData.serializer(), data.copy(session = null))
        }.getOrElse { return false }
        return preferences.edit().putString(key(childId), encoded).commit()
    }

    private fun key(childId: String): String {
        val environment = if (SupabaseEnvironment.isLocal) "local:${SupabaseEnvironment.url}" else "production"
        return "${environment.hashCode()}_$childId"
    }

    private companion object {
        const val FILE = "pomodoro_state"
    }
}
