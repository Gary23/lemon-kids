package com.lemonkids.shared.model

/** 任务的单一主要成长领域；null 表示旧数据未设置，other 表示明确选择综合。 */
object GrowthDomain {
    const val OTHER = "other"
    val choices = listOf(
        "reading" to "阅读", "calculation" to "计算", "dictation" to "默写",
        "english" to "英语", "writing" to "写作表达", "math_thinking" to "数学思维",
        "life" to "劳动与自理", OTHER to "综合/不计专项"
    )

    fun label(value: String?): String = when (value) {
        null -> "未设置（不计专项）"
        else -> choices.firstOrNull { it.first == value }?.second ?: "未设置（不计专项）"
    }

    fun isValid(value: String): Boolean = choices.any { it.first == value }
}
