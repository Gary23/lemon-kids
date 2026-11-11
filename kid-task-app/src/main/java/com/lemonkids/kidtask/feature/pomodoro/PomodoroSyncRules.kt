package com.lemonkids.kidtask.feature.pomodoro

import com.lemonkids.shared.model.PomodoroDailyCount
import java.time.LocalDate

/** 聚合与本机队列一并替换；无法确认上传结果的日期保留原缓存，避免远端+本机重复显示。 */
object PomodoroSyncRules {
    fun reconcile(
        data: PomodoroStoredData,
        daily: List<PomodoroDailyCount>,
        accepted: Set<String>,
        from: LocalDate,
        to: LocalDate,
        fetchedAtMillis: Long
    ): PomodoroStoredData {
        val protectedDates = data.completions.filter { !it.synced && it.sessionId !in accepted }
            .map { it.completedDate }.toSet()
        val updatedDates = mutableSetOf<String>()
        val counts = data.cachedCounts.toMutableMap()
        var day = from
        while (!day.isAfter(to)) {
            val key = day.toString()
            if (key !in protectedDates) {
                counts[key] = 0
                updatedDates += key
            }
            day = day.plusDays(1)
        }
        daily.filter { it.date in updatedDates }.forEach { counts[it.date] = it.count }
        return data.copy(
            cachedCounts = counts,
            completions = data.completions.map {
                if (it.sessionId in accepted && it.completedDate in updatedDates) it.copy(synced = true) else it
            },
            countsFetchedAtMillis = if (updatedDates.isNotEmpty()) fetchedAtMillis else data.countsFetchedAtMillis
        )
    }
}
