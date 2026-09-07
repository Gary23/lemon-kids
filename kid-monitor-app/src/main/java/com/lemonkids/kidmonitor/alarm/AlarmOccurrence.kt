package com.lemonkids.kidmonitor.alarm

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 日期范围闹钟只向 AlarmManager 登记下一次发生时间，响铃结束后再登记下一天。 */
object AlarmOccurrence {
    fun nextInRange(
        rangeStartMillis: Long,
        rangeEndMillis: Long,
        timezone: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Long? {
        val zone = zoneOf(timezone)
        val start = Instant.ofEpochMilli(rangeStartMillis).atZone(zone)
        val endDate = Instant.ofEpochMilli(rangeEndMillis).atZone(zone).toLocalDate()
        var date = maxOf(start.toLocalDate(), Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate())
        var candidate = date.atTime(start.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
        if (candidate <= nowMillis) {
            date = date.plusDays(1)
            candidate = date.atTime(start.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
        }
        return candidate.takeIf { date <= endDate }
    }

    fun nextAfter(
        currentOccurrenceMillis: Long,
        rangeEndMillis: Long,
        timezone: String
    ): Long? {
        val zone = zoneOf(timezone)
        val current = Instant.ofEpochMilli(currentOccurrenceMillis).atZone(zone)
        val nextDate: LocalDate = current.toLocalDate().plusDays(1)
        val endDate = Instant.ofEpochMilli(rangeEndMillis).atZone(zone).toLocalDate()
        return nextDate.takeIf { it <= endDate }
            ?.atTime(current.toLocalTime())
            ?.atZone(zone)
            ?.toInstant()
            ?.toEpochMilli()
    }

    private fun zoneOf(timezone: String): ZoneId = runCatching { ZoneId.of(timezone) }
        .getOrDefault(ZoneId.systemDefault())
}
