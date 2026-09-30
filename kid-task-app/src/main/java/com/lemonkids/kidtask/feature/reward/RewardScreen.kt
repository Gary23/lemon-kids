package com.lemonkids.kidtask.feature.reward

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lemonkids.kidtask.R
import com.lemonkids.kidtask.ui.components.StatusBadge
import com.lemonkids.kidtask.ui.theme.Butter
import com.lemonkids.kidtask.ui.theme.Canvas
import com.lemonkids.kidtask.ui.theme.FreshMint
import com.lemonkids.kidtask.ui.theme.FreshMintSoft
import com.lemonkids.kidtask.ui.theme.Lemon
import com.lemonkids.kidtask.ui.theme.LemonBorder
import com.lemonkids.kidtask.ui.theme.LemonShadow
import com.lemonkids.kidtask.ui.theme.SkyBlueSoft
import com.lemonkids.kidtask.ui.theme.SlateInk
import com.lemonkids.kidtask.ui.theme.SlateMuted
import com.lemonkids.kidtask.ui.theme.Strawberry
import com.lemonkids.kidtask.ui.theme.StrawberrySoft

@Composable
fun RewardScreen(
    realPoints: Int?,
    pointsUnavailable: Boolean,
    streakDays: Int,
    onCalendarClick: () -> Unit,
    viewModel: RewardViewModel = hiltViewModel()
) {
    val demo by viewModel.demoState.collectAsStateWithLifecycle()
    demo.feedback?.let { message ->
        Dialog(onDismissRequest = viewModel::clearFeedback) {
            Surface(shape = RoundedCornerShape(32.dp), color = Color.White, shadowElevation = 12.dp) {
                Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("🎉", fontSize = 52.sp)
                    Text("演示申请已记录！", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = SlateInk)
                    Text(message, fontSize = 14.sp, color = SlateMuted)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DialogNumber("扣除星星", "0 颗 ⭐", Modifier.weight(1f))
                        DialogNumber("真实余额", realPoints?.let { "$it 颗 ⭐" } ?: if (pointsUnavailable) "暂不可用" else "读取中", Modifier.weight(1f))
                    }
                    Button(onClick = viewModel::clearFeedback, modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Lemon)) {
                        Text("太棒啦，我知道了 🥳", fontWeight = FontWeight.Bold, color = SlateInk)
                    }
                }
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFFF8F9FF)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = if (maxWidth >= 760.dp) 3 else 2
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                RewardGreeting(realPoints, pointsUnavailable, streakDays)
                BankHeader(realPoints, pointsUnavailable)
                SectionHeading("正在攒星星的心愿 (进行中大目标)", "特别心愿 · 冲刺中 · 演示")
                BigWishCard(onCalendarClick)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("随时可兑换的小心愿", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = SlateInk)
                        Text("演示心愿：可体验向爸爸妈妈申请兑换的页面效果", fontSize = 13.sp, color = SlateMuted)
                    }
                    StatusBadge("${demo.wishes.size} 件现货可兑换 · 演示", FreshMintSoft, FreshMint)
                }
                demo.wishes.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { wish ->
                            SmallWishCard(
                                wish, demo.appliedWishIds.contains(wish.id),
                                { viewModel.requestWish(wish.id) }, Modifier.weight(1f)
                            )
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                SectionHeading("兑换与奖励记录 📜", "诚实守信，努力看得见 · 演示")
                RewardPanel {
                    demo.examples.forEachIndexed { index, example ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)
                                .background(if (index == 0) SkyBlueSoft.copy(alpha = 0.55f) else Butter, RoundedCornerShape(16.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(shape = RoundedCornerShape(16.dp), color = if (index == 0) SkyBlueSoft else Butter) {
                                Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) { Text(example.emoji, fontSize = 25.sp) }
                            }
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${example.title} ${example.emoji}", fontWeight = FontWeight.Bold, color = SlateInk)
                                    Spacer(Modifier.width(8.dp))
                                    StatusBadge("示例 -${example.cost} 颗 ⭐", SkyBlueSoft, Strawberry)
                                }
                                Text(example.note, fontSize = 12.sp, color = SlateMuted)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("示例记录", fontSize = 12.sp, color = FreshMint, fontWeight = FontWeight.Bold)
                                Text(example.date, fontSize = 11.sp, color = SlateMuted)
                            }
                        }
                    }
                }
                Text("🍋 柠檬任务 · 每一颗星星都见证努力与成长", Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    fontSize = 11.sp, color = SlateMuted)
            }
        }
    }
}

@Composable
private fun RewardGreeting(realPoints: Int?, pointsUnavailable: Boolean, streakDays: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("晚上好，小当家！今天也要加油攒星星～ ☀️", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = SlateInk)
            Text("积少成多，离愿望又近一步！", fontSize = 13.sp, color = SlateMuted)
        }
        StatusBadge(if (streakDays > 0) "🔥 连续打卡 $streakDays 天" else "🔥 打卡天数待同步", StrawberrySoft, Strawberry)
        Spacer(Modifier.width(7.dp))
        StatusBadge("⭐ ${realPoints?.let { "$it 积分" } ?: if (pointsUnavailable) "积分暂不可用" else "读取积分中"}", Butter, LemonShadow)
    }
}

@Composable
private fun DialogNumber(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = Butter) {
        Column(Modifier.padding(12.dp)) {
            Text(label, fontSize = 12.sp, color = SlateMuted)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = SlateInk)
        }
    }
}

@Composable
private fun BankHeader(realPoints: Int?, pointsUnavailable: Boolean) {
    Surface(
        shape = RoundedCornerShape(28.dp), color = Lemon,
        border = BorderStroke(1.dp, LemonBorder), shadowElevation = 5.dp
    ) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFFFFE083), Lemon, Color(0xFFDCE9FF))))
            .padding(22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.85f)) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.AccountBalance, null, tint = LemonShadow, modifier = Modifier.size(26.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("我的星星银行 🏦", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = SlateInk)
                    Text("用认真完成的任务，兑换属于你的心愿礼物吧！", fontSize = 13.sp, color = SlateInk)
                }
                StatusBadge("爱心愿望池 · 演示", Color.White, Strawberry)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BankBalance("当前可用零花星", realPoints?.toString() ?: if (pointsUnavailable) "暂不可用" else "读取中", "真实积分", false, Modifier.weight(1f))
                BankBalance("本月累计赚取", "186", "演示数据", true, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BankBalance(title: String, amount: String, tag: String, savings: Boolean, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 2.dp) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = if (savings) StrawberrySoft else Lemon) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(if (savings) Icons.Filled.Savings else Icons.Filled.Star, null,
                        tint = if (savings) Strawberry else LemonShadow, modifier = Modifier.size(27.dp))
                }
            }
            Column {
                Text(title, fontSize = 12.sp, color = SlateMuted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(amount, fontSize = 31.sp, fontWeight = FontWeight.ExtraBold, color = SlateInk)
                    if (amount.all { it.isDigit() }) Text(" 颗 ⭐", fontSize = 13.sp, color = LemonShadow)
                }
                Text(tag, fontSize = 10.sp, color = if (savings) Strawberry else FreshMint)
            }
        }
    }
}

@Composable
private fun BigWishCard(onCalendarClick: () -> Unit) {
    RewardPanel {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Box {
                Image(
                    painter = painterResource(R.drawable.reward_castle), contentDescription = "演示心愿：城堡积木",
                    modifier = Modifier.size(width = 224.dp, height = 184.dp).clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop
                )
                Surface(Modifier.padding(9.dp), shape = CircleShape, color = Color.White.copy(alpha = 0.92f)) {
                    Text("终极心愿 🏰", Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        fontSize = 11.sp, color = Strawberry, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("乐高迪士尼城堡 / 机械组积木套装", Modifier.weight(1f), fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold, color = SlateInk)
                    Text("⭐ 200 颗", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LemonShadow)
                }
                Text("和爸爸一起拼搭的梦幻城堡，完成每日背诵与早睡任务可以获得额外星星奖励！",
                    fontSize = 13.sp, color = SlateMuted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("演示收集进度: 133 / 200 颗", fontWeight = FontWeight.Bold, color = SlateInk, fontSize = 13.sp)
                    Text("66%", fontWeight = FontWeight.Bold, color = Strawberry, fontSize = 14.sp)
                }
                LinearProgressIndicator(
                    progress = { 0.665f }, modifier = Modifier.fillMaxWidth().height(17.dp).clip(CircleShape),
                    color = FreshMint, trackColor = SkyBlueSoft
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("🚀 还差 67 颗演示星星就能兑换啦！", fontSize = 12.sp, color = FreshMint, fontWeight = FontWeight.Bold)
                    Row(Modifier.background(SkyBlueSoft, CircleShape).clickable(onClick = onCalendarClick)
                        .padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("查看任务日历", color = SlateInk, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Icon(Icons.Filled.ChevronRight, null, tint = SlateInk, modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallWishCard(wish: DemoWish, applied: Boolean, onRequest: () -> Unit, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = Color.White,
        border = BorderStroke(1.dp, LemonBorder), shadowElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            val image = when (wish.id) {
                "park" -> R.drawable.reward_park
                "book" -> R.drawable.reward_book
                "icecream" -> R.drawable.reward_icecream
                else -> null
            }
            Box {
                if (image != null) {
                    Image(painterResource(image), contentDescription = wish.title,
                        modifier = Modifier.fillMaxWidth().height(116.dp).clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop)
                } else {
                    Box(Modifier.fillMaxWidth().height(116.dp)
                        .background(if (wish.id == "cartoon") StrawberrySoft else SkyBlueSoft, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center) {
                        Surface(shape = CircleShape, color = if (wish.id == "cartoon") Strawberry else Lemon) {
                            Box(Modifier.size(62.dp), contentAlignment = Alignment.Center) { Text(wish.emoji, fontSize = 38.sp) }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().padding(7.dp), contentAlignment = Alignment.TopEnd) {
                    StatusBadge(if (applied) "已体验" else "✓ 可兑换 · 演示", FreshMintSoft, FreshMint)
                }
            }
            Text("${wish.title} ${wish.emoji}", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = SlateInk,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(wish.description, fontSize = 12.sp, color = SlateMuted, minLines = 2, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("⭐ ${wish.exampleCost} 星", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LemonShadow)
                Button(onClick = onRequest, shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = FreshMint)) {
                    Text("立即申请兑换", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, tag: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(width = 5.dp, height = 24.dp).background(Strawberry, CircleShape))
        Text(title, modifier = Modifier.weight(1f), fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = SlateInk)
        StatusBadge(tag, StrawberrySoft, Strawberry)
    }
}

@Composable
private fun RewardPanel(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(25.dp), color = Color.White,
        border = BorderStroke(1.dp, LemonBorder.copy(alpha = 0.55f)), shadowElevation = 2.dp) {
        Column { content() }
    }
}
