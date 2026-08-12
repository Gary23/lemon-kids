package com.lemonkids.kidtask.feature.reward

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lemonkids.kidtask.ui.theme.Cream
import com.lemonkids.kidtask.ui.theme.InkBrown
import com.lemonkids.kidtask.ui.theme.Lavender
import com.lemonkids.kidtask.ui.theme.LavenderSoft
import com.lemonkids.kidtask.ui.theme.Mint
import com.lemonkids.kidtask.ui.theme.MintSoft
import com.lemonkids.kidtask.ui.theme.MutedGray
import com.lemonkids.kidtask.ui.theme.Pink
import com.lemonkids.kidtask.ui.theme.PinkSoft
import com.lemonkids.kidtask.ui.theme.Sunny

@Composable
fun RewardScreen(
    viewModel: RewardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = Cream) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部积分 Header
            RewardHeader(points = uiState.points)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(20.dp))

                // 奖励商城标题
                Text(
                    "兑换奖励 🎁",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = InkBrown,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(Modifier.height(12.dp))

                if (uiState.isLoading && uiState.rewards.isEmpty()) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Pink, modifier = Modifier.size(36.dp))
                    }
                } else if (uiState.rewards.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFF5F0EB))
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "妈妈还没放奖励进来哦\n问问她吧～",
                            fontSize = 15.sp,
                            color = MutedGray,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // 2 列网格
                    uiState.rewards.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            for (reward in row) {
                                RewardCard(
                                    reward = reward,
                                    onRedeem = { viewModel.redeemReward(it) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            // 奇数个补齐
                            if (row.size == 1) {
                                Spacer(Modifier.width(0.dp).weight(1f))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // 积分记录
                Text(
                    "积分记录 📒",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = InkBrown,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(Modifier.height(12.dp))

                if (uiState.pointRecords.isEmpty()) {
                    Text("还没有积分记录", fontSize = 14.sp, color = MutedGray)
                } else {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White,
                        shadowElevation = 4.dp
                    ) {
                        Column {
                            uiState.pointRecords.forEachIndexed { index, record ->
                                LedgerRow(
                                    record = record,
                                    isLast = index == uiState.pointRecords.lastIndex
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ==================== 顶部积分 Header ====================

@Composable
private fun RewardHeader(points: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(
                Brush.linearGradient(listOf(Lavender, LavenderSoft))
            )
            .padding(top = 20.dp, bottom = 28.dp, start = 24.dp, end = 24.dp)
    ) {
        Column {
            Text(
                "我的星星银行 🏦",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White.copy(alpha = 0.9f)
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.95f),
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = Sunny, modifier = Modifier.size(36.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "$points",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        "当前积分",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "继续完成任务赚取更多星星吧！✨",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

// ==================== 奖励卡片 ====================

@Composable
private fun RewardCard(
    reward: RewardUiItem,
    onRedeem: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // emoji 或图标
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Sunny.copy(alpha = 0.2f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("🎁", fontSize = 32.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                reward.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = InkBrown,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = Sunny, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(2.dp))
                Text(
                    "${reward.cost}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Pink
                )
            }
            Spacer(Modifier.height(12.dp))

            when {
                reward.redeemed -> {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MintSoft.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Mint, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("已兑换", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Mint)
                        }
                    }
                }
                reward.affordable -> {
                    Button(
                        onClick = { onRedeem(reward.id) },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Pink),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("兑换", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }
                else -> {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFF5F0EB),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "还差 ${reward.cost} 个 ⭐",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MutedGray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==================== 积分记录行 ====================

@Composable
private fun LedgerRow(record: PointRecordUiItem, isLast: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标
        val (iconColor, iconBg, icon) = when {
            record.amount > 0 -> Triple(Mint, MintSoft.copy(alpha = 0.5f), Icons.AutoMirrored.Filled.TrendingUp)
            record.amount < 0 -> Triple(Pink, PinkSoft.copy(alpha = 0.5f), Icons.AutoMirrored.Filled.TrendingDown)
            else -> Triple(MutedGray, Color(0xFFF5F0EB), Icons.Filled.Schedule)
        }
        Surface(
            shape = CircleShape,
            color = iconBg,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        // 记录信息
        Column(modifier = Modifier.weight(1f)) {
            Text(
                record.reason,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = InkBrown
            )
            Text(
                "${record.time} · 余额 ⭐${record.balance}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MutedGray
            )
        }
        // 积分变动
        val amountColor = when {
            record.amount > 0 -> Mint
            record.amount < 0 -> Pink
            else -> MutedGray
        }
        val amountText = when {
            record.amount > 0 -> "+${record.amount}"
            record.amount < 0 -> "${record.amount}"
            else -> "—"
        }
        Text(
            amountText,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = amountColor
        )
    }

    if (!isLast) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFFE8E0E0))
        )
    }
}