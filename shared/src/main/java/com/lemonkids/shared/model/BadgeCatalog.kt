package com.lemonkids.shared.model

/** 勋章的数值只代表家长录入的累计数量；任务习惯勋章由任务事实单独计算。 */
data class BadgeStage(val threshold: Int, val name: String)

data class BadgeSpec(
    val key: String,
    val title: String,
    val group: String,
    val unit: String,
    val stages: List<BadgeStage>,
    val limit: Int? = null,
    val graduationGoal: Int? = null
) {
    fun level(value: Int): Int = stages.indexOfLast { value >= it.threshold } + 1
    fun stageName(value: Int): String = stages.getOrNull(level(value) - 1)?.name ?: "尚未点亮"
    fun nextThreshold(value: Int): Int? = stages.firstOrNull { it.threshold > value }?.threshold
    fun isComplete(value: Int): Boolean = value >= (limit ?: graduationGoal ?: Int.MAX_VALUE)
}

object BadgeCatalog {
    private fun fixed(key: String, title: String, group: String, unit: String, thresholds: List<Int>, names: List<String>) =
        BadgeSpec(key, title, group, unit, thresholds.zip(names).map { BadgeStage(it.first, it.second) }, limit = thresholds.last())

    private val semesters = listOf("一年级上学期", "一年级下学期", "二年级上学期", "二年级下学期",
        "三年级上学期", "三年级下学期", "四年级上学期", "四年级下学期",
        "五年级上学期", "五年级下学期", "六年级上学期", "六年级下学期")
    private fun semester(key: String, title: String, group: String, thresholds: List<Int>, names: List<String>, goal: Int) =
        BadgeSpec(key, title, group, "次", thresholds.indices.map { BadgeStage(thresholds[it], "${semesters[it]}·${names[it / 2]}") }, graduationGoal = goal)

    val manual: List<BadgeSpec> = listOf(
        fixed("recognition", "认字小行家", "语文阅读", "字", listOf(1,300,750,1350,2100,3000), listOf("字形初识","识字拾光","字海探路","汉字积木","文字远航","识字领航")),
        fixed("dictation", "默写小能手", "语文阅读", "字", listOf(1,300,750,1350,2100,3000), listOf("笔尖起步","字词收集","默写探路","笔下积累","书写远航","默写领航")),
        semester("reading", "阅读足迹", "语文阅读", listOf(1,110,220,330,440,550,660,770,880,990,1100,1210), listOf("翻页起步","故事寻路","书海漫游","主题探索","深读思考","阅读领航"), 1320),
        fixed("idioms", "成语寻宝家", "语文阅读", "个", listOf(1,26,64,115,179,256), listOf("成语初见","四字拾趣","典故寻宝","成语探路","妙语积累","成语博览")),
        fixed("poems", "古诗词收藏家", "语文阅读", "首", listOf(1,16,39,70,109,155), listOf("诗句初见","诗韵拾光","诗篇探寻","诗意漫游","古韵积累","诗词领航")),
        semester("writing", "写作成长记", "语文阅读", listOf(1,40,80,120,160,180,200,220,240,260,280,300), listOf("写话小苗","写话达人","习作启航","习作成长","习作拓展","习作领航"), 320),
        fixed("english_words", "单词探险家", "英语", "个", listOf(1,180,450,810,1260,1800), listOf("单词初见","词汇拾光","词语搭桥","词海漫游","表达积累","单词领航")),
        BadgeSpec("english_reading", "英语阅读", "英语", "篇", emptyList(), graduationGoal = 2049),
        fixed("dino_english", "迪诺英语", "英语", "课", listOf(1,44,109,197,306,437), listOf("迪诺启程","跟读探路","课程进阶","乐学冒险","迪诺达人","迪诺毕业")),
        semester("calculation", "计算进阶家", "数学", listOf(1,100,200,300,400,500,600,700,800,900,1000,1100), listOf("算式起步","运算稳步","计算探路","算法熟练","运算进阶","计算领航"), 1200),
        fixed("thinking_seed", "思维启萌", "数学", "课", listOf(1,21,53,95,148,211), listOf("思维点火","规律初探","图形寻路","逻辑探险","巧思解谜","启萌毕业")),
        semester("math_explaining", "数学讲题家", "数学", listOf(1,20,40,60,80,100,120,140,160,180,200,220), listOf("敢讲第一题","讲题小向导","思路讲述者","解法分享者","数学小讲师","讲题领航员"), 240),
        semester("life_skills", "劳动与自理", "生活实践", listOf(1,100,200,300,400,500,600,700,800,900,1000,1100), listOf("动手起步","自理小能手","家务好帮手","生活实践者","自理进阶者","生活领航员"), 1200)
    )
    val byKey = manual.associateBy { it.key }
    val englishGoals = listOf(50,50,52,59,55,63,77,78,91,101,107,128,134,144,117,99,98,89,54,52,55,58,58,59,59,62)
    private val englishGroupNames = listOf("阅读小芽","翻页寻宝者","故事同行者","绘本漫游者","篇章探路者",
        "书海航行者","文字发现者","阅读领航员","星海阅读家")
    private val ordinals = listOf("一阶","二阶","三阶")
    fun englishName(index: Int): String = "${englishGroupNames[index / 3]}·${ordinals[index % 3]}"
    fun englishLetter(index: Int): String = ('A' + index).toString()
    fun englishIndex(segment: String): Int? = segment.singleOrNull()?.let { it - 'A' }?.takeIf { it in englishGoals.indices }

    /** 当前可计级的课程：前级未完成时，后级录入仍保留但不提前点亮。 */
    fun englishLevel(values: Map<String, Int>): Int {
        for (index in englishGoals.indices) {
            val value = (values[englishLetter(index)] ?: 0).coerceIn(0, englishGoals[index])
            if (value < englishGoals[index]) return if (index == 0 && value == 0) 0 else index + 1
        }
        return englishGoals.size
    }
    fun englishCountedTotal(values: Map<String, Int>): Int {
        val level = englishLevel(values)
        if (level == 0) return 0
        return (0 until level).sumOf { (values[englishLetter(it)] ?: 0).coerceIn(0, englishGoals[it]) }
    }
    fun validate(key: String, segment: String, value: Int): Boolean {
        if (value < 0) return false
        val spec = byKey[key] ?: return false
        if (key == "english_reading") {
            val index = englishIndex(segment) ?: return false
            return value <= englishGoals[index]
        }
        return segment.isEmpty() && (spec.limit == null || value <= spec.limit)
    }
}
