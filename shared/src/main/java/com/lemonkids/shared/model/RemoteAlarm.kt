package com.lemonkids.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 家长创建的逻辑闹钟；时间以 UTC ISO-8601 保存，展示时再按设备时区转换。 */
@Serializable
data class RemoteAlarm(
    @SerialName("id") val id: String = "",
    @SerialName("family_id") val familyId: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("target_device_id") val targetDeviceId: String = "",
    @SerialName("revision") val revision: Long = 1,
    @SerialName("trigger_at") val triggerAt: String = "",
    @SerialName("timezone") val timezone: String = "Asia/Shanghai",
    @SerialName("title") val title: String = "",
    @SerialName("message") val message: String = "",
    @SerialName("enabled") val enabled: Boolean = true,
    @SerialName("requires_confirmation") val requiresConfirmation: Boolean = true,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = ""
)

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

/** 已通过 monitor 绑定的目标 Pad。设备 ID 只用于路由，不在界面暴露。 */
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
    RINGING("ringing"),
    DISMISSED("dismissed"),
    MISSED("missed")
}

enum class AlarmEventType(val value: String) {
    DEPLOYED("deployed"),
    PERMISSION_DENIED("permission_denied"),
    RINGING("ringing"),
    DISMISSED("dismissed"),
    MISSED("missed")
}
