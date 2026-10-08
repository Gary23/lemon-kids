package com.lemonkids.kidtask.feature.profile

import com.lemonkids.shared.model.BadgeCatalog
import com.lemonkids.shared.model.GrowthEvent
import com.lemonkids.shared.model.GrowthSnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class GrowthLevel(
    val number: Int,
    val symbol: String,
    val name: String,
    val currentThreshold: Long,
    val nextThreshold: Long?
)

data class GrowthBadge(
    val key: String,
    val title: String,
    val group: String,
    val unit: String,
    val level: Int,
    val stageName: String,
    val rawValue: Int,
    val updatedAt: String?,
    val manual: Boolean
)

/** 一条勋章线内的一个阶段即为一枚独立勋章。 */
data class GrowthBadgeStage(
    val key: String,
    val title: String,
    val group: String,
    val unit: String,
    val level: Int,
    val stageName: String,
    val threshold: Int,
    val nextThreshold: Int?,
    val currentValue: Int,
    val isObtained: Boolean,
    val isCurrent: Boolean,
    val updatedAt: String?,
    val manual: Boolean
) {
    val displayTitle get() = "$title·$stageName"
    val progress: Float get() = when {
        !isObtained -> 0f
        nextThreshold == null -> 1f
        isCurrent -> ((currentValue - threshold).toFloat() / (nextThreshold - threshold)).coerceIn(0f, 1f)
        else -> 1f
    }
    val progressText get() = when {
        !isObtained -> "需要 $threshold $unit"
        nextThreshold == null -> "已达到 $threshold $unit"
        isCurrent -> "$currentValue / $nextThreshold $unit"
        else -> "已获得"
    }
}

object GrowthRules {
    val habitThresholds = listOf(1,25,60,105,160,225,300,385,480,580,680,780,890,1000,1100)
    private val habitStages = mapOf(
        "task_duty" to listOf("起步小队员","任务收集者","任务践行者","任务坚持者","成长担当者"),
        "checkin" to listOf("起步脚印","习惯探路者","成长记录者","岁月漫步者","足迹领航员")
    )
    private val moonNames = listOf("月光启程","月光同行","月光探索","月光领航")
    private val sunNames = listOf("旭日初升","晴空追光","星河守护","晴空领航")
    private val starNames = listOf("小小星芽","闪亮星伴","星河探索","星光领航")

    fun threshold(level: Int): Long {
        require(level in 1..85)
        return (2..level).sumOf { minOf(700, 250 + 25 * (it - 2)).toLong() }
    }

    fun level(exp: Long): GrowthLevel {
        val number = (85 downTo 2).firstOrNull { exp >= threshold(it) } ?: 1
        val next = if (number == 85) null else threshold(number + 1)
        if (number == 85) return GrowthLevel(85, "👑", "柠檬之冠·成长领航者", threshold(85), null)
        val suns = number / 21
        val rest = number % 21
        val moons = rest / 5
        val stars = rest % 5
        val symbol = "☀️".repeat(suns) + "🌙".repeat(moons) + "⭐".repeat(stars)
        val count = buildList {
            if (suns > 0) add("${chinese(suns)}轮太阳")
            if (moons > 0) add("${chinese(moons)}弯月")
            if (stars > 0) add("${chinese(stars)}星")
        }.joinToString("")
        val name = when {
            suns > 0 -> sunNames[suns - 1]
            moons > 0 -> moonNames[moons - 1]
            else -> starNames[stars - 1]
        }
        return GrowthLevel(number, symbol, "$count·$name", threshold(number), next)
    }

    private fun chinese(value: Int) = listOf("零","一","二","三","四")[value]
    fun habitLevel(days: Int) = habitThresholds.indexOfLast { days >= it } + 1
    fun habitName(key: String, level: Int): String {
        if (level == 0) return "尚未点亮"
        val group = (level - 1) / 3
        val ordinal = (level - 1) % 3
        return "${habitStages.getValue(key)[group]}·${listOf("一阶","二阶","三阶")[ordinal]}"
    }

    fun badges(snapshot: GrowthSnapshot): List<GrowthBadge> {
        val records = snapshot.badgeProgress
        val manual = BadgeCatalog.manual.map { spec ->
            val entries = records.filter { it.badgeKey == spec.key }
            if (spec.key == "english_reading") {
                val values = entries.associate { it.segment to it.value }
                val level = BadgeCatalog.englishLevel(values)
                val index = (level - 1).coerceAtLeast(0)
                GrowthBadge(spec.key, spec.title, spec.group, "篇", level,
                    if (level == 0) "尚未点亮" else BadgeCatalog.englishName(index),
                    BadgeCatalog.englishCountedTotal(values),
                    entries.maxByOrNull { it.updatedAt }?.updatedAt, true)
            } else {
                val record = entries.firstOrNull()
                val value = record?.value ?: 0
                GrowthBadge(spec.key, spec.title, spec.group, spec.unit, spec.level(value),
                    spec.stageName(value), value, record?.updatedAt, true)
            }
        }
        val habits = listOf("task_duty" to snapshot.dutyDays, "checkin" to snapshot.checkinDays).map { (key, days) ->
            GrowthBadge(key, if (key == "task_duty") "任务担当者" else "坚持足迹", "成长习惯", "天",
                habitLevel(days), habitName(key, habitLevel(days)), days, null, false)
        }
        return manual + habits
    }

    fun obtainedStages(snapshot: GrowthSnapshot): List<GrowthBadgeStage> = badges(snapshot)
        .flatMap { badge -> badgeStages(snapshot, badge).filter { it.isObtained } }

    fun badgeStages(snapshot: GrowthSnapshot, badge: GrowthBadge): List<GrowthBadgeStage> {
        if (badge.key == "english_reading") return englishStages(snapshot, badge)
        val stageDefinitions = when (badge.key) {
            "task_duty", "checkin" -> habitThresholds.mapIndexed { index, threshold -> threshold to habitName(badge.key, index + 1) }
            else -> BadgeCatalog.byKey.getValue(badge.key).stages.map { it.threshold to it.name }
        }
        return stageDefinitions.mapIndexed { index, (threshold, name) ->
            val current = index + 1 == badge.level
            GrowthBadgeStage(
                key = badge.key, title = badge.title, group = badge.group, unit = badge.unit,
                level = index + 1, stageName = name, threshold = threshold,
                nextThreshold = stageDefinitions.getOrNull(index + 1)?.first,
                currentValue = badge.rawValue,
                isObtained = index < badge.level, isCurrent = current,
                updatedAt = badge.updatedAt, manual = badge.manual
            )
        }
    }

    private fun englishStages(snapshot: GrowthSnapshot, badge: GrowthBadge): List<GrowthBadgeStage> {
        val values = snapshot.badgeProgress.filter { it.badgeKey == badge.key }.associate { it.segment to it.value }
        var accumulatedGoal = 0
        return BadgeCatalog.englishGoals.indices.map { index ->
            val goal = BadgeCatalog.englishGoals[index]
            val completedBefore = accumulatedGoal
            val threshold = completedBefore + if (index == 0) 1 else 0
            accumulatedGoal += goal
            val current = index + 1 == badge.level
            GrowthBadgeStage(
                key = badge.key, title = badge.title, group = badge.group, unit = "篇",
                level = index + 1, stageName = BadgeCatalog.englishName(index), threshold = threshold,
                nextThreshold = accumulatedGoal.takeUnless { index == BadgeCatalog.englishGoals.lastIndex && badge.level == 26 &&
                    (values[BadgeCatalog.englishLetter(index)] ?: 0) >= goal },
                currentValue = if (current) completedBefore + (values[BadgeCatalog.englishLetter(index)] ?: 0) else accumulatedGoal,
                isObtained = index < badge.level, isCurrent = current,
                updatedAt = badge.updatedAt, manual = true
            )
        }
    }

    /** 同等级时优先最近进度，最终按稳定 key 选择，避免界面刷新时跳动。 */
    fun currentBadge(snapshot: GrowthSnapshot): GrowthBadgeStage? = badges(snapshot)
        .filter { it.level > 0 }
        .sortedWith(compareByDescending<GrowthBadge> { it.level }
            .thenByDescending { it.updatedAt.orEmpty() }
            .thenBy { it.key })
        .firstOrNull()
        ?.let { badgeStages(snapshot, it).firstOrNull { stage -> stage.level == it.level } }

    fun eventText(event: GrowthEvent): Pair<String, String> = when (event.kind) {
        "task" -> "完成任务：${event.subject}" to if (event.value > 0) "实际到账 ${event.value} 星，计入 EXP" else "无可核实到账星星"
        "badge" -> {
            val spec = BadgeCatalog.byKey[event.badgeKey]
            "家长更新了${spec?.title ?: "专项"}进度" to "记录为 ${event.value} ${spec?.unit ?: ""}${event.segment?.takeIf { it.isNotEmpty() }?.let { " · $it 级" } ?: ""}"
        }
        "reward" -> "奖励已使用：${event.subject}" to "来自奖励使用记录"
        "level" -> "达到 Lv.${event.level} ${level(threshold(event.level ?: 1)).symbol}" to "累计有效任务 EXP 达到 ${event.value}"
        "habit" -> {
            val title = if (event.badgeKey == "task_duty") "任务担当者" else "坚持足迹"
            "$title 达到 Lv.${event.level}" to "累计 ${event.value} 个达标日"
        }
        else -> "成长记录" to "来自真实记录"
    }

    fun shanghaiTime(value: String?): String? = runCatching {
        value?.let { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("Asia/Shanghai")).format(Instant.parse(it)) }
    }.getOrNull()

    fun shanghaiDate(value: String?): String? = runCatching {
        value?.let { DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.of("Asia/Shanghai")).format(Instant.parse(it)) }
    }.getOrNull()
}
