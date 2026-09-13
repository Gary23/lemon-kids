package com.lemonkids.kidmonitor.alarm

/**
 * 闹钟的唯一展示模型。锁屏 Activity、普通悬浮层和无障碍悬浮层都只能消费它，
 * 不能各自读取 Room 或直接停止服务。
 */
data class AlarmPresentation(
    val alarmId: String,
    val revision: Long,
    val title: String = DEFAULT_TITLE,
    val message: String = DEFAULT_MESSAGE,
    val requiresConfirmation: Boolean = true
) {
    val session: AlarmSession get() = AlarmSession(alarmId, revision)

    companion object {
        const val DEFAULT_TITLE = "闹钟时间到了"
        const val DEFAULT_MESSAGE = "请完成家长设置的提醒"
    }
}

data class AlarmSession(val alarmId: String, val revision: Long)

/** Activity 与原生 View 共用的展示文案。 */
object AlarmPresentationUi {
    const val ICON = "🌸"
    const val DISMISS_LABEL = "我知道了，关闭闹钟"
}
