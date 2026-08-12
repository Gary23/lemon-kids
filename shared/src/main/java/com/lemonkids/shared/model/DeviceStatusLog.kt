package com.lemonkids.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceStatusLog(
    @SerialName("id") val id: String = "",
    @SerialName("family_id") val familyId: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("event_type") val eventType: String = DeviceStatusEventType.HEARTBEAT.value,
    @SerialName("accessibility_enabled") val accessibilityEnabled: Boolean = false,
    @SerialName("limit_service_running") val limitServiceRunning: Boolean = false,
    @SerialName("app_process_alive") val appProcessAlive: Boolean = true,
    @SerialName("battery_ignoring_optimizations") val batteryIgnoringOptimizations: Boolean = false,
    @SerialName("message") val message: String = "",
    @SerialName("created_at") val createdAt: String = ""
)

enum class DeviceStatusEventType(val value: String) {
    HEARTBEAT("heartbeat"),
    APP_START("app_start"),
    BOOT("boot"),
    USER_PRESENT("user_present"),
    SERVICE_RECOVERED("service_recovered"),
    ACCESSIBILITY_DISABLED("accessibility_disabled")
}
