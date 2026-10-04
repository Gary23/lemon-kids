package com.lemonkids.kidtask.feature.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.lemonkids.kidtask.ui.components.StatusBadge
import com.lemonkids.kidtask.ui.components.childNameLabel
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
import com.lemonkids.shared.ui.auth.AuthViewModel

@Composable
fun ProfileScreen(
    realPoints: Int?,
    pointsUnavailable: Boolean,
    onPlanClick: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val profile by viewModel.uiState.collectAsStateWithLifecycle()
    val demo = remember { GrowthDemoUiState() }
    var category by remember { mutableStateOf(MedalCategory.ALL) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showSwitchDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                val bytes = stream.readBytes()
                val ext = context.contentResolver.getType(it)?.split("/")?.lastOrNull() ?: "jpg"
                viewModel.uploadAvatar(bytes, "avatar_${System.currentTimeMillis()}.$ext")
            }
        }
    }

    if (showEditDialog) {
        var name by remember { mutableStateOf(profile.userName) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("修改昵称") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("昵称") }) },
            confirmButton = {
                TextButton(onClick = {
                    if (name.trim().isNotEmpty()) viewModel.updateName(name.trim())
                    showEditDialog = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showEditDialog = false }) { Text("取消") } },
            shape = RoundedCornerShape(28.dp)
        )
    }
    if (showSwitchDialog) {
        AlertDialog(
            onDismissRequest = { showSwitchDialog = false },
            title = { Text("切换账号") },
            text = { Text("确定要切换账号吗？需要重新输入绑定码。") },
            confirmButton = {
                TextButton(onClick = { showSwitchDialog = false; authViewModel.signOut() }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showSwitchDialog = false }) { Text("取消") } },
            shape = RoundedCornerShape(28.dp)
        )
    }
    profile.errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("操作未完成") }, text = { Text(error) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("知道了") } }
        )
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFFF8F9FF)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val medalColumns = if (maxWidth >= 780.dp) 4 else 2
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GrowthHeader(
                    profile = profile, demo = demo, realPoints = realPoints, pointsUnavailable = pointsUnavailable,
                    onAvatarClick = { imagePicker.launch("image/*") },
                    onNameClick = { showEditDialog = true }
                )
                MedalSection(demo, category, { category = it }, medalColumns)
                MilestoneSection(demo)
                SettingsSection(
                    profile = profile,
                    onPlanClick = onPlanClick,
                    onNameClick = { showEditDialog = true },
                    onAvatarClick = { imagePicker.launch("image/*") },
                    onSwitchClick = { showSwitchDialog = true }
                )
            }
        }
    }
}

@Composable
private fun GrowthHeader(
    profile: KidProfileUiState,
    demo: GrowthDemoUiState,
    realPoints: Int?,
    pointsUnavailable: Boolean,
    onAvatarClick: () -> Unit,
    onNameClick: () -> Unit
) {
    GrowthPanel(color = Color.White) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Butter, Color.White, FreshMintSoft.copy(alpha = 0.4f))), RoundedCornerShape(21.dp))
                .padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(78.dp).clickable(onClick = onAvatarClick), contentAlignment = Alignment.Center) {
                    if (profile.avatarUrl != null) {
                        AsyncImage(profile.avatarUrl, "头像，点击更换", Modifier.fillMaxSize().clip(RoundedCornerShape(23.dp)))
                    } else {
                        Box(Modifier.fillMaxSize().background(Lemon, RoundedCornerShape(23.dp)), contentAlignment = Alignment.Center) {
                            Text("👧", fontSize = 44.sp)
                        }
                    }
                    if (profile.isUploading) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(23.dp)), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(30.dp), color = Color.White)
                        }
                    }
                }
                Column(Modifier.weight(0.95f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.clickable(onClick = onNameClick), verticalAlignment = Alignment.CenterVertically) {
                        Text(childNameLabel(profile.userName, profile.hasUser), modifier = Modifier.weight(1f, fill = false),
                            fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = SlateInk,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(7.dp))
                        Icon(Icons.Filled.Edit, "修改昵称", tint = SlateMuted, modifier = Modifier.size(18.dp))
                    }
                    StatusBadge("✏️ 认真小学霸 · 演示", Lemon, SlateInk)
                    Text("🚀 自律小先锋 · 演示", fontSize = 11.sp, color = FreshMint)
                    Text("成长称号与班级资料为演示", fontSize = 10.sp, color = SlateMuted)
                }
                Row(Modifier.weight(1.9f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GrowthStat("累计完成", "48", "个 · 演示", SkyBlueSoft, Modifier.weight(1f))
                    GrowthStat("自律打卡", "7", "天 · 演示", StrawberrySoft, Modifier.weight(1f))
                    GrowthStat("荣誉勋章", "12", "枚 · 演示", FreshMintSoft, Modifier.weight(1f))
                    GrowthStat("点亮星星", realPoints?.toString() ?: if (pointsUnavailable) "--" else "…", "颗 · 真实", Butter, Modifier.weight(1f))
                }
            }
            LevelCard(demo)
        }
    }
}

@Composable
private fun GrowthStat(title: String, value: String, tag: String, color: Color, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(26.dp), color = color) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 10.sp, color = SlateMuted, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(value, fontSize = 23.sp, color = SlateInk, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(tag, fontSize = 9.sp, color = if (tag.contains("真实")) LemonShadow else Strawberry, maxLines = 1)
        }
    }
}

@Composable
private fun LevelCard(demo: GrowthDemoUiState) {
    Surface(shape = RoundedCornerShape(19.dp), color = SkyBlueSoft.copy(alpha = 0.55f)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Lv.${demo.level} 探索小学士 · 演示", modifier = Modifier.weight(1f), fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold, color = SlateInk)
                StatusBadge("下一级：Lv.5 智慧小达人 ✨", Butter, LemonShadow)
            }
            Text("当前成长值 ${demo.experience} / ${demo.nextLevelExperience} EXP · 还差 220 经验升级",
                fontSize = 11.sp, color = SlateMuted)
            LinearProgressIndicator(progress = { demo.experience.toFloat() / demo.nextLevelExperience },
                modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape), color = Lemon, trackColor = Color.White)
            Text("🌱 踏实成长中 · 等级、成长值及升级奖励均为演示，不会发放星星。",
                fontSize = 11.sp, color = FreshMint)
        }
    }
}

@Composable
private fun MedalSection(
    demo: GrowthDemoUiState,
    selected: MedalCategory,
    onSelect: (MedalCategory) -> Unit,
    columns: Int
) {
    GrowthPanel(color = Color.White) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("🏅 成长勋章与荣誉成就馆", Modifier.weight(1f), fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold, color = SlateInk)
                StatusBadge("演示勋章", Butter, LemonShadow)
            }
            Text("点亮属于你的每一次自律与突破，做闪闪发光的自己！", fontSize = 13.sp, color = SlateMuted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                MedalCategory.entries.forEach { category ->
                    Surface(
                        modifier = Modifier.clickable { onSelect(category) }, shape = CircleShape,
                        color = if (selected == category) Lemon else SkyBlueSoft
                    ) {
                        Text(category.label, Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SlateInk)
                    }
                }
            }
            demo.visibleMedals(selected).chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { medal -> MedalCard(medal, Modifier.weight(1f)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun MedalCard(medal: DemoMedal, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(23.dp),
        color = if (medal.unlocked) SkyBlueSoft.copy(alpha = 0.55f) else Color(0xFFF3F4F6),
        border = BorderStroke(1.dp, if (medal.unlocked) LemonBorder else Color(0xFFE5E7EB))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = when (medal.emoji) {
                "🐦" -> Butter; "🧮" -> FreshMintSoft; "🎒" -> StrawberrySoft; "🏆" -> Color(0xFFFFE8B5)
                else -> SkyBlueSoft
            }) {
                Box(Modifier.size(57.dp), contentAlignment = Alignment.Center) {
                    Text(medal.emoji, fontSize = 32.sp)
                }
            }
            Text(medal.title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                color = SlateInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(medal.detail, fontSize = 11.sp, color = SlateMuted, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
            if (medal.unlocked) Text("⭐ ⭐ ⭐", fontSize = 12.sp, color = LemonShadow)
            else if (medal.emoji == "📜") LinearProgressIndicator(progress = { 0.8f },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape), color = Lemon, trackColor = Color.White)
            else Text("🔒", fontSize = 12.sp, color = SlateMuted)
            StatusBadge(if (medal.unlocked) "已点亮 · 演示" else if (medal.emoji == "📜") "即将解锁 80% · 演示" else "待解锁 · 演示",
                if (medal.unlocked) FreshMintSoft else Butter,
                if (medal.unlocked) FreshMint else SlateMuted)
        }
    }
}

@Composable
private fun MilestoneSection(demo: GrowthDemoUiState) {
    GrowthPanel(color = Color.White) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("👣 成长里程碑与闪光足迹", Modifier.weight(1f), fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold, color = SlateInk)
                StatusBadge("演示记录", StrawberrySoft, Strawberry)
            }
            Text("一步一个脚印，记录每一个发光瞬间！", fontSize = 12.sp, color = SlateMuted)
            demo.milestones.forEachIndexed { index, milestone ->
                Row(Modifier.fillMaxWidth().background(if (index == 0) SkyBlueSoft else Butter, RoundedCornerShape(18.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = CircleShape, color = if (index == 0) FreshMintSoft else StrawberrySoft) {
                        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) { Text(milestone.emoji, fontSize = 26.sp) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(milestone.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SlateInk)
                        Text(milestone.detail, fontSize = 12.sp, color = SlateMuted)
                        Text(if (index == 2) "🏅 示例徽章" else "✚ 示例经验与星星", fontSize = 10.sp, color = FreshMint)
                    }
                    Text("演示记录", fontSize = 11.sp, color = Strawberry)
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    profile: KidProfileUiState,
    onPlanClick: () -> Unit,
    onNameClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onSwitchClick: () -> Unit
) {
    GrowthPanel(color = Color.White) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("设置与账户", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = SlateInk)
            SettingsAction(Icons.Filled.CalendarMonth, "计划", "查看未来任务安排", onPlanClick)
            SettingsAction(Icons.Filled.PhotoCamera, "更换头像", "从相册选择新头像", onAvatarClick)
            SettingsAction(Icons.Filled.Edit, "修改昵称", "更新我的名字", onNameClick)
            SettingsAction(Icons.Filled.Lock, "使用限制",
                if (profile.appLimits.isEmpty()) "当前没有已配置的使用限制" else
                    profile.appLimits.joinToString(" · ") { "${it.appName} ${it.dailyLimitMinutes}分钟/天" }, null)
            SettingsAction(Icons.AutoMirrored.Filled.Logout, "切换账号", "重新输入绑定码", onSwitchClick)
        }
    }
}

@Composable
private fun SettingsAction(icon: ImageVector, title: String, detail: String, onClick: (() -> Unit)?) {
    val modifier = if (onClick == null) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().clickable(onClick = onClick)
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = Butter) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = LemonShadow, modifier = Modifier.size(23.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = SlateInk)
                Text(detail, fontSize = 12.sp, color = SlateMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onClick != null) Icon(Icons.Filled.ChevronRight, null, tint = SlateMuted, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun GrowthPanel(color: Color, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(27.dp), color = color,
        border = BorderStroke(1.dp, LemonBorder.copy(alpha = 0.55f)), shadowElevation = 2.dp) {
        Column { content() }
    }
}
