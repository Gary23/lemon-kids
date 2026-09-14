package com.lemonkids.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 家长创建的日期范围闹钟；时间以 UTC ISO-8601 保存，展示时按 [timezone] 转回本地日期。
 * triggerAt 和 endAt 分别表示生效首日、末日的同一每日提醒时刻，范围两端均包含。
 */
@Serializable
data class RemoteAlarm(
    @SerialName("id") val id: String = "",
    @SerialName("family_id") val familyId: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("target_device_id") val targetDeviceId: String = "",
    @SerialName("revision") val revision: Long = 1,
    @SerialName("trigger_at") val triggerAt: String = "",
    /** 生效末日的每日提醒时刻，和 triggerAt 共同组成日期范围。 */
    @SerialName("end_at") val endAt: String = "",
    @SerialName("timezone") val timezone: String = "Asia/Shanghai",
    @SerialName("title") val title: String = "",
    @SerialName("message") val message: String = "",
    /** 仅接受客户端内置白名单；未知值由 Pad 回退为默认音乐。 */
    @SerialName("background_music_id") val backgroundMusicId: String = AlarmBackgroundMusic.DEFAULT_ID,
    @SerialName("voice_enabled") val voiceEnabled: Boolean = true,
    /** 保存时冻结的播报文本，避免标题/备注更新竞态下朗读到旧组合。 */
    @SerialName("voice_text") val voiceText: String = "",
    @SerialName("enabled") val enabled: Boolean = true,
    @SerialName("requires_confirmation") val requiresConfirmation: Boolean = true,
    /** 非空表示已从家长端删除；Pad 仍会接收该版本以撤销离线时已登记的系统闹钟。 */
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = ""
)

/** 仅提供应用内置的离线背景音乐，服务端迁移会以同一白名单约束写入值。 */
object AlarmBackgroundMusic {
    const val GENTLE_BELL_V1 = "gentle_bell_v1"
    const val SEASIDE_SUNRISE_V1 = "seaside_sunrise_v1"
    const val DEFAULT_ID = GENTLE_BELL_V1
    val supportedIds = listOf(GENTLE_BELL_V1, SEASIDE_SUNRISE_V1)

    fun normalized(id: String): String = id.takeIf { it in supportedIds } ?: DEFAULT_ID
    fun displayName(id: String): String = when (normalized(id)) {
        GENTLE_BELL_V1 -> "轻柔钟声"
        SEASIDE_SUNRISE_V1 -> "海边晨光"
        else -> "轻柔钟声"
    }
}

/** 腾讯语音尚未就绪时，Pad 会使用同一份规范化文本走 Android 本地 TTS 兜底。 */
object AlarmVoiceText {
    const val MAX_LENGTH = 200

    fun build(title: String, message: String): String = listOf(title.trim(), message.trim())
        .filter { it.isNotEmpty() }
        .joinToString("。")
        .replace(Regex("\\s+"), " ")

    /** 仅用于历史数据或客户端异常数据的安全降级；新建/编辑必须先由 UI 明确校验。 */
    fun limited(title: String, message: String): String = build(title, message).take(MAX_LENGTH)
}

@Serializable
data class AlarmDelivery(
    @SerialName("alarm_id") val alarmId: String = "",
    @SerialName("device_id") val deviceId: String = "",
    @SerialName("revision") val revision: Long = 1,
    @SerialName("status") val status: String = AlarmDeliveryStatus.PENDING.value,
    @SerialName("error_code") val errorCode: String? = null,
    @SerialName("ack_at") val ackAt: String? = null,
    @SerialName("updated_at") val updatedAt: String = ""
)

@Serializable
data class AlarmEvent(
    @SerialName("alarm_id") val alarmId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("revision") val revision: Long,
    @SerialName("event_type") val eventType: String,
    @SerialName("detail") val detail: String = ""
)

/** 家长端将闹钟和目标 Pad 的最后一次回执组合展示。 */
data class ParentAlarmStatus(
    val alarm: RemoteAlarm,
    val delivery: AlarmDelivery?
)

/** 已通过 monitor 绑定的目标 Pad。设备 ID 用于路由，界面仅可显示脱敏尾号以供家长区分多台 Pad。 */
@Serializable
data class MonitorDevice(
    @SerialName("device_id") val deviceId: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("child_name") val childName: String = ""
)

enum class AlarmDeliveryStatus(val value: String) {
    PENDING("pending"),
    DEPLOYED("deployed"),
    EXACT_ALARM_DENIED("exact_alarm_denied"),
    NOTIFICATION_DENIED("notification_denied"),
    FULL_SCREEN_DENIED("full_screen_denied"),
    REMOVED("removed"),
    RINGING("ringing"),
    DISMISSED("dismissed"),
    MISSED("missed")
}

enum class AlarmEventType(val value: String) {
    DEPLOYED("deployed"),
    REMOVED("removed"),
    PERMISSION_DENIED("permission_denied"),
    RINGING("ringing"),
    DISMISSED("dismissed"),
    MISSED("missed"),
    OVERLAY_UNAVAILABLE("overlay_unavailable")
}
