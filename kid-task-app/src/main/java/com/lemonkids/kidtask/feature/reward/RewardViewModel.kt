package com.lemonkids.kidtask.feature.reward

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class DemoWish(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val exampleCost: Int
)

data class DemoRedemption(val emoji: String, val title: String, val note: String, val cost: Int, val date: String)

/** 仅用于页面演示；这里没有真实积分、兑换订单或服务端写入。 */
data class RewardDemoUiState(
    val wishes: List<DemoWish> = listOf(
        DemoWish("park", "🎡", "周末去一次游乐园", "包含旋转木马和摩天轮体验，周末全家一起出动！", 100),
        DemoWish("book", "📚", "挑选一本喜欢的漫画/故事书", "由你亲自挑选一本心仪的科普、探险或幽默绘本。", 50),
        DemoWish("icecream", "🍦", "吃一次冰淇淋或快乐儿童餐", "周末解锁一次甜美冰淇淋或自选快乐儿童餐！", 40),
        DemoWish("cartoon", "📺", "看一集 30 分钟动画片", "在做完晚间作业后，选播一集精彩趣味动画短剧。", 30),
        DemoWish("game", "🎮", "晚睡 30 分钟自由玩耍券", "周五/周六专用！随心安排乐高或拼图自由时间。", 20)
    ),
    val examples: List<DemoRedemption> = listOf(
        DemoRedemption("🐾", "动物园门票", "示例：快乐游览，观看了大熊猫！", 80, "2026-09-15"),
        DemoRedemption("🎨", "水彩笔一套", "示例：48 色水溶性画笔，用于美术创意课程。", 40, "2026-09-08")
    ),
    val appliedWishIds: Set<String> = emptySet(),
    val feedback: String? = null
) {
    fun requestWish(id: String): RewardDemoUiState {
        val wish = wishes.firstOrNull { it.id == id } ?: return this
        return copy(
            appliedWishIds = appliedWishIds + id,
            feedback = "“${wish.title}”演示申请已记录在本页；未通知家长，也未扣除星星。"
        )
    }

}

@HiltViewModel
class RewardViewModel @Inject constructor() : ViewModel() {
    private val _demoState = MutableStateFlow(RewardDemoUiState())
    val demoState = _demoState.asStateFlow()

    fun requestWish(id: String) { _demoState.value = _demoState.value.requestWish(id) }
    fun clearFeedback() { _demoState.value = _demoState.value.copy(feedback = null) }
}
