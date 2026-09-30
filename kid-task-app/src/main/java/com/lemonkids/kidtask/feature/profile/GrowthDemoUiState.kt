package com.lemonkids.kidtask.feature.profile

enum class MedalCategory(val label: String) {
    ALL("全部勋章"), DISCIPLINE("自律打卡"), HABIT("习惯培养"), STUDY("学习进步"), WISH("心愿达成")
}

data class DemoMedal(
    val emoji: String,
    val title: String,
    val detail: String,
    val category: MedalCategory,
    val unlocked: Boolean
)

data class DemoMilestone(val emoji: String, val title: String, val detail: String)

/** 成长数据尚未落库，所有项目只展示设计效果。 */
data class GrowthDemoUiState(
    val level: Int = 4,
    val experience: Int = 780,
    val nextLevelExperience: Int = 1000,
    val medals: List<DemoMedal> = listOf(
        DemoMedal("🐦", "晨读百灵鸟", "连续晨读打卡 7 天", MedalCategory.DISCIPLINE, true),
        DemoMedal("🧮", "算术小神童", "口算速算 100% 正确率", MedalCategory.STUDY, true),
        DemoMedal("🎒", "整理小达人", "自主整理书包与红领巾", MedalCategory.HABIT, true),
        DemoMedal("🏆", "全勤小能手", "单月任务全满分达成", MedalCategory.DISCIPLINE, true),
        DemoMedal("🍅", "专注小番茄", "限时专注 25 分钟无打扰", MedalCategory.HABIT, true),
        DemoMedal("📜", "诗词小状元", "背诵打卡 30 天", MedalCategory.STUDY, false),
        DemoMedal("🏃", "运动小健将", "坚持每日跳绳 200 个", MedalCategory.DISCIPLINE, false),
        DemoMedal("⭐", "百星收藏家", "累计点亮 100 颗星星", MedalCategory.WISH, true)
    ),
    val milestones: List<DemoMilestone> = listOf(
        DemoMilestone("📖", "自主完成晚安绘本整理与书包准备", "独立收纳好学习用品，爸爸妈妈免督促点赞！"),
        DemoMilestone("🎯", "突破口算速算 100 题全对满分", "限时专注完成练习并认真检查，专注力有进步！"),
        DemoMilestone("🏅", "成功点亮「晨读百灵鸟」荣誉徽章", "达成连续 7 天清晨朗读古诗词打卡。")
    )
) {
    fun visibleMedals(category: MedalCategory): List<DemoMedal> =
        if (category == MedalCategory.ALL) medals else medals.filter { it.category == category }
}
