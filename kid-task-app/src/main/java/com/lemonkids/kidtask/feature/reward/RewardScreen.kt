package com.lemonkids.kidtask.feature.reward

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lemonkids.shared.model.Reward
import com.lemonkids.shared.model.PointRecord
import com.lemonkids.shared.model.RewardRedemption
import com.lemonkids.shared.model.RewardRedemptionStatus
import com.lemonkids.kidtask.ui.components.StatusBadge
import com.lemonkids.kidtask.ui.theme.FreshMint
import com.lemonkids.kidtask.ui.theme.FreshMintSoft
import com.lemonkids.kidtask.ui.theme.FreshMintShadow
import com.lemonkids.kidtask.ui.theme.Lemon
import com.lemonkids.kidtask.ui.theme.LemonBorder
import com.lemonkids.kidtask.ui.theme.LemonShadow
import com.lemonkids.kidtask.ui.theme.SkyBlueSoft
import com.lemonkids.kidtask.ui.theme.SlateInk
import com.lemonkids.kidtask.ui.theme.SlateMuted
import com.lemonkids.kidtask.ui.theme.Strawberry
import com.lemonkids.kidtask.ui.theme.StrawberrySoft
import kotlin.math.abs

@Composable
fun RewardScreen(
    onCalendarClick: () -> Unit,
    viewModel: RewardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    state.confirmation?.let { action ->
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirmation,
            title = { Text(when (action.action) {
                RewardAction.REDEEM -> "确认兑换"
                RewardAction.USE -> "确认标记已使用"
                RewardAction.CANCEL -> "确认取消兑换"
            }) },
            text = { Text(when (action.action) {
                RewardAction.REDEEM -> "兑换“${action.title}”将扣除 ${action.cost} 颗星星，预计剩余 ${(state.snapshot?.balance ?: 0) - action.cost} 颗。"
                RewardAction.USE -> "“${action.title}”将标记为已使用并保留在记录列表，使用后不能取消退星。"
                RewardAction.CANCEL -> "取消“${action.title}”后将退回兑换时扣除的 ${action.cost} 颗星星。"
            }) },
            confirmButton = {
                Button(onClick = viewModel::submit, enabled = !state.submitting) {
                    Text(if (state.submitting) "提交中…" else "确认")
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissConfirmation, enabled = !state.submitting) { Text("返回") } }
        )
    }
    state.feedback?.let { message ->
        AlertDialog(onDismissRequest = viewModel::clearFeedback,
            title = { Text("操作成功") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearFeedback) { Text("知道了") } })
    }
    val snapshot = state.snapshot
    val balance = snapshot?.balance
    Surface(Modifier.fillMaxSize(), color = Color(0xFFF8F9FF)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val contentWidth = maxWidth - 44.dp
            val columns = if (contentWidth >= 714.dp) 3 else if (contentWidth >= 472.dp) 2 else 1
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                BankHeader(balance, snapshot?.monthlyEarned, state.error != null,
                    state.refreshing, !state.refreshing && !state.submitting, viewModel::refresh)
                state.error?.let { error ->
                    RewardPanel {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(error, color = Strawberry)
                            TextButton(onClick = viewModel::refresh) { Text("重试") }
                        }
                    }
                }
                if (state.loading) {
                    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (snapshot != null) {
                    val featured = snapshot.rewards.firstOrNull { it.isFeatured }
                    if (featured != null) {
                        SectionHeading("正在攒星星的心愿", "特别心愿 · 冲刺中")
                        BigWishCard(featured, state.imageUrls[featured.id], balance ?: 0,
                            featured.id in snapshot.unavailableOneTimeIds,
                            !state.submitting && state.error == null && (state.pendingRequestId == null || state.pendingRewardId == featured.id),
                            onCalendarClick, { viewModel.confirmRedeem(featured.id) })
                    }
                    val ordinary = snapshot.rewards.filter { it.id != featured?.id }
                    val redeemableCount = ordinary.count { reward ->
                        reward.redemptionBlockReason(balance, snapshot.unavailableOneTimeIds) == null
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(width = 5.dp, height = 30.dp).background(FreshMint, CircleShape))
                        Column(Modifier.weight(1f)) {
                            Text("随时可兑换的小心愿", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = SlateInk)
                            Text("家长准备的 ${ordinary.size} 件奖励，当前可兑换 $redeemableCount 件", fontSize = 13.sp, color = SlateMuted)
                        }
                        StatusBadge("$redeemableCount 件可兑换", FreshMintSoft, FreshMintShadow)
                    }
                    if (ordinary.isEmpty()) {
                        RewardPanel { Text("暂时没有可展示的小心愿", Modifier.padding(22.dp), color = SlateMuted) }
                    }
                    ordinary.chunked(columns).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { reward ->
                                SmallWishCard(reward, state.imageUrls[reward.id], balance ?: 0,
                                    reward.id in snapshot.unavailableOneTimeIds,
                                    !state.submitting && state.error == null && (state.pendingRequestId == null || state.pendingRewardId == reward.id),
                                    { viewModel.confirmRedeem(reward.id) }, Modifier.weight(1f))
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(width = 5.dp, height = 24.dp).background(SlateMuted, CircleShape))
                        Text("兑换与奖励记录 📜", Modifier.weight(1f), fontSize = 21.sp,
                            fontWeight = FontWeight.ExtraBold, color = SlateInk)
                        Text("诚实守信，努力看得见", fontSize = 12.sp, color = SlateMuted)
                    }
                    val records = (snapshot.redemptions
                        .filter { it.status != RewardRedemptionStatus.CANCELLED }
                        .map { DisplayRecord(it.redeemedAt, it, null) } +
                        snapshot.legacyRedemptions.map { DisplayRecord(it.timestamp, null, it) })
                        .sortedByDescending { it.timestamp }
                    RewardPanel {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (records.isEmpty()) RecordEmptyState("还没有兑换记录")
                            records.forEach { item ->
                                item.redemption?.let { redemption ->
                                    RedemptionCard(redemption, !state.submitting && state.error == null && state.pendingRequestId == null,
                                        { viewModel.confirmUse(redemption.id) }, { viewModel.confirmCancel(redemption.id) })
                                }
                                item.legacy?.let { record ->
                                    LegacyRedemptionCard(record)
                                }
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

private data class DisplayRecord(
    val timestamp: String,
    val redemption: RewardRedemption?,
    val legacy: PointRecord?
)

@Composable
private fun RedemptionCard(redemption: RewardRedemption, actionsEnabled: Boolean, onUse: () -> Unit, onCancel: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 620.dp && redemption.status == RewardRedemptionStatus.HELD) {
            Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFF1F7FC)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecordSummary("🎁", redemption.title, redemption.cost,
                        "待使用", redemption.redeemedAt.take(10), Modifier.fillMaxWidth())
                    RedemptionActions(actionsEnabled, onUse, onCancel, Modifier.align(Alignment.End))
                }
            }
        } else {
            RecordRow {
                RecordSummary("🎁", redemption.title, redemption.cost,
                    if (redemption.status == RewardRedemptionStatus.HELD) "待使用" else "已使用",
                    redemption.redeemedAt.take(10), Modifier.weight(1f))
                if (redemption.status == RewardRedemptionStatus.HELD) {
                    RedemptionActions(actionsEnabled, onUse, onCancel)
                }
            }
        }
    }
}

@Composable
private fun LegacyRedemptionCard(record: PointRecord) {
    RecordRow {
        RecordSummary("📜", record.reason, abs(record.amount).takeIf { it > 0 },
            "旧版兑换 · 状态未知 · 不可取消退星", record.timestamp.take(10), Modifier.weight(1f))
    }
}

@Composable
private fun RedemptionActions(actionsEnabled: Boolean, onUse: () -> Unit, onCancel: () -> Unit,
                              modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onUse, enabled = actionsEnabled, shape = CircleShape,
            modifier = Modifier.height(44.dp), contentPadding = PaddingValues(horizontal = 16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006E2F), contentColor = Color.White)) {
            Text("使用", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Button(onClick = onCancel, enabled = actionsEnabled, shape = CircleShape,
            modifier = Modifier.height(44.dp), contentPadding = PaddingValues(horizontal = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBE5A34), contentColor = Color.White)) {
            Text("取消兑换", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RecordRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFF1F7FC)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun RecordSummary(icon: String, title: String, cost: Int?, status: String, date: String,
                          modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(44.dp).background(FreshMintSoft, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 23.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, Modifier.weight(1f, fill = false), fontWeight = FontWeight.Bold, color = SlateInk,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (cost != null) StatusBadge("-$cost ⭐", Color(0xFFE4EDF4), Strawberry)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(status, Modifier.weight(1f, fill = false), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = if (status == "待使用") Color(0xFF006E2F) else SlateMuted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(date, fontSize = 12.sp, color = SlateMuted)
            }
        }
    }
}

@Composable
private fun RecordEmptyState(message: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFF1F7FC)) {
        Text(message, Modifier.fillMaxWidth().padding(20.dp), color = SlateMuted)
    }
}

@Composable
private fun BankHeader(realPoints: Int?, monthlyEarned: Int?, unavailable: Boolean,
                       refreshing: Boolean, refreshEnabled: Boolean, onRefresh: () -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = Lemon,
        border = BorderStroke(1.dp, LemonBorder), shadowElevation = 5.dp) {
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
                Box(Modifier.padding(bottom = 3.dp).background(Color(0xFF005321), CircleShape)) {
                    Button(onClick = onRefresh, enabled = refreshEnabled, shape = CircleShape,
                        modifier = Modifier.height(44.dp), contentPadding = PaddingValues(horizontal = 14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF006E2F), contentColor = Color.White,
                            disabledContainerColor = Color(0xFF006E2F), disabledContentColor = Color.White)) {
                        Text(if (refreshing) "刷新中…" else "刷新奖励", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BankBalance("当前可用零花星", realPoints?.toString() ?: if (unavailable) "暂不可用" else "读取中", "真实积分", false, Modifier.weight(1f))
                BankBalance("本月累计赚取", monthlyEarned?.toString() ?: if (unavailable) "暂不可用" else "读取中", "完成任务所得", true, Modifier.weight(1f))
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
private fun BigWishCard(reward: Reward, imageUrl: String?, balance: Int, unavailable: Boolean, actionEnabled: Boolean,
                        onCalendarClick: () -> Unit, onRedeem: () -> Unit) {
    val progress = (balance.toFloat() / reward.cost.coerceAtLeast(1)).coerceIn(0f, 1f)
    val blockReason = reward.redemptionBlockReason(balance, if (unavailable) setOf(reward.id) else emptySet())
    Surface(shape = RoundedCornerShape(25.dp), color = Color.White,
        shadowElevation = 10.dp) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(20.dp)) {
            if (maxWidth < 620.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    RewardCover(reward, imageUrl, Modifier.fillMaxWidth().height(170.dp))
                    BigWishDetails(reward, balance, progress, blockReason, actionEnabled, onCalendarClick, onRedeem)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    RewardCover(reward, imageUrl, Modifier.size(width = 224.dp, height = 184.dp))
                    Box(Modifier.weight(1f)) {
                        BigWishDetails(reward, balance, progress, blockReason, actionEnabled, onCalendarClick, onRedeem)
                    }
                }
            }
        }
    }
}

@Composable
private fun RewardCover(reward: Reward, imageUrl: String?, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
        RewardImage(reward, imageUrl)
        Box(Modifier.fillMaxSize().padding(9.dp), contentAlignment = Alignment.TopStart) {
            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.92f)) {
                Text("特别心愿 🌟", Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    fontSize = 11.sp, color = Strawberry, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BigWishDetails(reward: Reward, balance: Int, progress: Float, blockReason: String?,
                           actionEnabled: Boolean, onCalendarClick: () -> Unit, onRedeem: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(reward.title, Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold,
                color = SlateInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("⭐ ${reward.cost} 颗", Modifier.padding(start = 8.dp), color = LemonShadow, fontWeight = FontWeight.Bold)
        }
        Text(reward.description.orEmpty(), fontSize = 13.sp, color = SlateMuted)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("收集进度：${balance.coerceAtMost(reward.cost)} / ${reward.cost} 颗",
                Modifier.weight(1f), color = SlateInk, fontWeight = FontWeight.Bold)
            Text("${(progress * 100).toInt()}%", color = Strawberry, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(24.dp).clip(CircleShape),
            color = FreshMint, trackColor = SkyBlueSoft)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val status = blockReason ?: "星星已攒够，可以兑换啦！"
            if (maxWidth < 430.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(status, color = if (blockReason == null) FreshMintShadow else Strawberry)
                    TextButton(onClick = onCalendarClick) { Text("查看任务日历 ›") }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(status, Modifier.weight(1f), color = if (blockReason == null) FreshMintShadow else Strawberry)
                    TextButton(onClick = onCalendarClick) { Text("查看任务日历 ›") }
                }
            }
        }
        if (blockReason == null) Button(onClick = onRedeem, enabled = actionEnabled) { Text("兑换奖励") }
    }
}

@Composable
private fun SmallWishCard(reward: Reward, imageUrl: String?, balance: Int, unavailable: Boolean, actionEnabled: Boolean,
                          onRedeem: () -> Unit, modifier: Modifier) {
    val blockReason = reward.redemptionBlockReason(balance, if (unavailable) setOf(reward.id) else emptySet())
    val status = when {
        blockReason == null -> "可兑换"
        unavailable -> "已兑完"
        balance < reward.cost -> "星星不足"
        else -> "暂不可兑"
    }
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = Color.White, shadowElevation = 5.dp) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(128.dp)) {
                RewardImage(reward, imageUrl, Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)))
                Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.TopEnd) {
                    StatusBadge(status, if (blockReason == null) FreshMintSoft else StrawberrySoft,
                        if (blockReason == null) FreshMintShadow else Strawberry)
                }
            }
            Text(reward.title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = SlateInk,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(reward.description.orEmpty(), fontSize = 12.sp, color = SlateMuted, minLines = 2, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("⭐ ${reward.cost} 星", Modifier.weight(1f), fontSize = 14.sp,
                    color = LemonShadow, fontWeight = FontWeight.ExtraBold)
                Box(Modifier.padding(bottom = 3.dp).background(Color(0xFF005321), CircleShape)) {
                    Button(onClick = onRedeem, enabled = actionEnabled && blockReason == null,
                        modifier = Modifier.padding(bottom = 3.dp).height(40.dp), shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF006E2F), contentColor = Color.White,
                            disabledContainerColor = Color(0xFF899B90), disabledContentColor = Color.White)) {
                        Text("立即兑换", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RewardImage(reward: Reward, imageUrl: String?, modifier: Modifier = Modifier.fillMaxSize()) {
    var displayedPainter by remember(reward.id, reward.imagePath, imageUrl == null) {
        mutableStateOf<Painter?>(null)
    }
    Box(modifier.background(Color(0xFFF2F2F2)), contentAlignment = Alignment.Center) {
        displayedPainter?.let { painter ->
            Image(painter, contentDescription = "${reward.title}的奖励图片",
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { displayedPainter = it.painter },
                modifier = Modifier.fillMaxSize().alpha(0f)
            )
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
