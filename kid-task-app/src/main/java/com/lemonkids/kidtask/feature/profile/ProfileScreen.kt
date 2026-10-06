package com.lemonkids.kidtask.feature.profile

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.lemonkids.kidtask.ui.components.StatusBadge
import com.lemonkids.kidtask.ui.components.childNameLabel
import com.lemonkids.kidtask.ui.theme.Butter
import com.lemonkids.kidtask.ui.theme.Lemon
import com.lemonkids.kidtask.ui.theme.LemonBorder
import com.lemonkids.kidtask.ui.theme.LemonShadow
import com.lemonkids.kidtask.ui.theme.SkyBlueSoft
import com.lemonkids.kidtask.ui.theme.SlateInk
import com.lemonkids.kidtask.ui.theme.SlateMuted
import com.lemonkids.kidtask.ui.theme.Strawberry
import com.lemonkids.kidtask.ui.theme.StrawberrySoft
import com.lemonkids.shared.model.GrowthEvent
import com.lemonkids.shared.model.GrowthSnapshot
import com.lemonkids.shared.ui.auth.AuthViewModel

/** 临时本地矢量图标；逐等级图片将在后续接入 Supabase Storage。 */
private fun badgeIcon(key: String): ImageVector = when (key) {
    "recognition" -> Icons.Filled.Spellcheck
    "dictation" -> Icons.Filled.Spellcheck
    "reading", "english_reading" -> Icons.Filled.AutoStories
    "idioms" -> Icons.Filled.Book
    "poems" -> Icons.Filled.MenuBook
    "writing", "dino_english" -> Icons.Filled.School
    "english_words" -> Icons.Filled.Translate
    "calculation", "math_explaining" -> Icons.Filled.Calculate
    "thinking_seed" -> Icons.Filled.Lightbulb
    "life_skills" -> Icons.Filled.Home
    "task_duty" -> Icons.Filled.CheckCircle
    "checkin" -> Icons.Filled.DirectionsWalk
    else -> Icons.Filled.Star
}

private enum class MedalCategory(val label: String) {
    ALL("全部"), CHINESE("语文阅读"), ENGLISH("英语"), MATH("数学"), LIFE("生活实践"), HABIT("成长习惯");
    fun matches(group: String) = this == ALL || label == group
}

private val GrowthInk = Color(0xFF0D1C2E)
private val GrowthMuted = Color(0xFF4D4632)
private val GrowthOutline = Color(0xFFD1C6AB)
private val GrowthBlue = Color(0xFFEFF4FF)
private val GrowthBlueTrack = Color(0xFFD5E3FC)
private val GrowthYellowSoft = Color(0xFFFFE083)
private val GrowthGreenSoft = Color(0xFF6BFF8F)
private val GrowthGreen = Color(0xFF006E2F)

private fun levelIcon(symbol: String): String = when {
    symbol.contains("👑") -> "👑"
    symbol.contains("☀") -> "☀️"
    symbol.contains("🌙") -> "🌙"
    else -> "⭐"
}

@Composable
fun ProfileScreen(
    realPoints: Int?,
    pointsUnavailable: Boolean,
    onPlanClick: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val profile by viewModel.uiState.collectAsStateWithLifecycle()
    var category by remember { mutableStateOf(MedalCategory.ALL) }
    var selectedBadge by remember { mutableStateOf<String?>(null) }
    var showSwitchDialog by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(profile.growth?.childId) { selectedBadge = null }
    LaunchedEffect(viewModel) { viewModel.refreshGrowth() }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshGrowth()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showSwitchDialog) {
        AlertDialog(
            onDismissRequest = { showSwitchDialog = false },
            title = { Text("切换账号") },
            text = { Text("确定要切换账号吗？需要重新输入绑定码。") },
            confirmButton = { TextButton(onClick = { showSwitchDialog = false; viewModel.clearGrowthForSwitch(); authViewModel.signOut() }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { showSwitchDialog = false }) { Text("取消") } },
            shape = RoundedCornerShape(28.dp)
        )
    }
    profile.errorMessage?.let { error ->
        AlertDialog(onDismissRequest = viewModel::clearError, title = { Text("操作未完成") }, text = { Text(error) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("知道了") } })
    }

    val growth = profile.growth
    selectedBadge?.let { badgeKey ->
        growth?.let { snapshot ->
            GrowthRules.badges(snapshot).firstOrNull { it.key == badgeKey }?.let { badge ->
                BadgeDetailDialog(badge, GrowthRules.badgeStages(snapshot, badge)) { selectedBadge = null }
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFFF8F9FF)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val medalColumns = if (maxWidth >= 780.dp) 4 else 2
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GrowthHeader(profile, realPoints, pointsUnavailable)
                if (profile.isGrowthLoading || profile.growthError) {
                    GrowthPanel(Color.White) {
                        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (profile.growthError) "成长记录读取失败" else "正在读取成长记录…", Modifier.weight(1f), color = SlateMuted)
                            TextButton(onClick = viewModel::refreshGrowth) { Text("重试") }
                        }
                    }
                }
                growth?.let { snapshot ->
                    MedalSection(
                        snapshot = snapshot, selected = category, onSelect = { category = it },
                        columns = medalColumns, onBadgeClick = { selectedBadge = it.key }
                    )
                    key(snapshot.childId) { MilestoneSection(snapshot.events) }
                }
                SettingsSection(profile, onPlanClick) { showSwitchDialog = true }
            }
        }
    }
}

@Composable
private fun GrowthHeader(profile: KidProfileUiState, realPoints: Int?, pointsUnavailable: Boolean) {
    val growth = profile.growth
    val level = growth?.let { GrowthRules.level(it.totalExp) }
    val currentBadge = growth?.let { GrowthRules.currentBadge(it) }
    GrowthPanel(Color.White) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(20.dp)) {
            val compact = maxWidth < 680.dp
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (compact) {
                    GrowthIdentity(profile, level, currentBadge?.displayTitle)
                    GrowthStats(profile, realPoints, pointsUnavailable)
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Box(Modifier.weight(1f)) { GrowthIdentity(profile, level, currentBadge?.displayTitle) }
                        Box(Modifier.width(378.dp)) { GrowthStats(profile, realPoints, pointsUnavailable) }
                    }
                }
                growth?.let { LevelCard(it.totalExp) }
            }
        }
    }
}

@Composable
private fun GrowthIdentity(profile: KidProfileUiState, level: GrowthLevel?, badgeTitle: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.size(84.dp)) {
            Surface(Modifier.size(76.dp), shape = RoundedCornerShape(18.dp), color = GrowthYellowSoft,
                border = BorderStroke(2.dp, Lemon.copy(alpha = 0.6f)), shadowElevation = 4.dp) {
                Box(contentAlignment = Alignment.Center) {
                    if (!profile.avatarUrl.isNullOrBlank()) {
                        AsyncImage(profile.avatarUrl, "孩子头像", Modifier.size(64.dp).clip(CircleShape))
                    } else Surface(Modifier.size(64.dp), shape = CircleShape, color = Color.White) {
                        Box(contentAlignment = Alignment.Center) { Text("👧", fontSize = 38.sp) }
                    }
                }
            }
            level?.let {
                Surface(Modifier.align(Alignment.BottomEnd).offset(x = 5.dp, y = 3.dp), shape = CircleShape,
                    color = Color.White, border = BorderStroke(1.dp, GrowthOutline.copy(alpha = 0.55f)), shadowElevation = 2.dp) {
                    Text("👑 Lv.${it.number}", Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        fontSize = 10.sp, color = Color(0xFF735C00), fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(childNameLabel(profile.userName, profile.hasUser), fontSize = 25.sp, fontWeight = FontWeight.ExtraBold,
                    color = GrowthInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                level?.let { GrowthTag("${levelIcon(it.symbol)} ${it.name}", Lemon, Color(0xFF6C5700), 12, pill = true) }
            }
            GrowthTag("🚀 ${badgeTitle ?: "第一枚勋章待点亮"}", GrowthGreenSoft, Color(0xFF005321), 11, pill = true)
            Text(if (profile.todayTaskTotal == 0) "今日暂无任务" else "● 今日打卡：${profile.todayTaskCompleted} / ${profile.todayTaskTotal}",
                fontSize = 11.sp, color = GrowthGreen, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun GrowthStats(profile: KidProfileUiState, realPoints: Int?, pointsUnavailable: Boolean) {
    val growth = profile.growth
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GrowthStat("累计完成", growth?.completedTasks?.toString() ?: "--", GrowthBlue,
            GrowthInk, Modifier.weight(1f))
        GrowthStat("自律打卡", growth?.checkinDays?.toString() ?: "--", Color(0xFFFFF2F3),
            Color(0xFFA93349), Modifier.weight(1f))
        GrowthStat("荣誉勋章", growth?.let { GrowthRules.obtainedStages(it).size.toString() } ?: "--", Color(0xFFE8FFEE),
            GrowthGreen, Modifier.weight(1f))
        GrowthStat("点亮星星", realPoints?.toString() ?: if (pointsUnavailable) "--" else "…", Color(0xFFFFF4CE),
            Color(0xFF574500), Modifier.weight(1f))
    }
}

@Composable
private fun GrowthStat(title: String, value: String, color: Color, ink: Color, modifier: Modifier) {
    Surface(modifier.height(90.dp), shape = RoundedCornerShape(18.dp), color = color,
        border = BorderStroke(1.dp, GrowthOutline.copy(alpha = 0.2f)), shadowElevation = 2.dp) {
        Column(Modifier.padding(horizontal = 3.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text(title, fontSize = 10.sp, color = ink, fontWeight = FontWeight.Medium, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text(value, fontSize = 25.sp, color = ink, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

@Composable
private fun LevelCard(exp: Long) {
    val level = GrowthRules.level(exp)
    val next = level.nextThreshold
    val progress = if (next == null) 1f else ((exp - level.currentThreshold).toFloat() / (next - level.currentThreshold)).coerceIn(0f, 1f)
    Surface(shape = RoundedCornerShape(18.dp), color = GrowthBlue,
        border = BorderStroke(1.dp, GrowthOutline.copy(alpha = 0.3f))) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                GrowthTag("Lv.${level.number}", Lemon, Color(0xFF6C5700), 13)
                Text(levelIcon(level.symbol), fontSize = 18.sp)
                Text(level.name, fontSize = 16.sp, color = GrowthInk, fontWeight = FontWeight.ExtraBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                Text(if (next == null) "当前成长值 $exp EXP" else "当前成长值 $exp / $next EXP",
                    fontSize = 13.sp, color = GrowthMuted, maxLines = 1)
            }
            Box(Modifier.fillMaxWidth().height(13.dp).clip(CircleShape).background(GrowthBlueTrack).padding(2.dp)) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(GrowthYellowSoft, Lemon, GrowthGreenSoft))))
            }
            if (next != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("下一级：", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = GrowthMuted, maxLines = 1)
                    GrowthTag("Lv.${level.number + 1} ${GrowthRules.level(next).name} ✨", GrowthYellowSoft.copy(alpha = 0.45f),
                        Color(0xFF735C00), 14, pill = true)
                    Text("（还差 ${next - exp} 经验升级）", fontSize = 12.sp, color = GrowthMuted, maxLines = 1)
                }
            }
            Text("🌱 踏实成长中 · 完成有效任务即可获得成长值", fontSize = 10.sp, color = GrowthMuted)
        }
    }
}

@Composable
private fun GrowthTag(label: String, background: Color, color: Color, fontSize: Int = 11, pill: Boolean = false) {
    Surface(shape = if (pill) CircleShape else RoundedCornerShape(8.dp), color = background) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = color,
            fontSize = fontSize.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MedalSection(
    snapshot: GrowthSnapshot, selected: MedalCategory, onSelect: (MedalCategory) -> Unit,
    columns: Int, onBadgeClick: (GrowthBadge) -> Unit
) {
    val badges = GrowthRules.badges(snapshot)
    val visibleBadges = badges.filter { it.rawValue > 0 && selected.matches(it.group) }
    val obtainedCount = GrowthRules.obtainedStages(snapshot).size
    GrowthPanel(Color.White) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = Lemon, shadowElevation = 2.dp) {
                    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) { Text("🏅", fontSize = 18.sp) }
                }
                Column(Modifier.weight(1f)) {
                    Text("成长勋章与荣誉成就馆", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = GrowthInk)
                    Text("点亮属于你的每一次自律与突破，做闪闪发光的自己！", fontSize = 11.sp, color = GrowthMuted)
                }
                StatusBadge("$obtainedCount 枚勋章", GrowthYellowSoft, Color(0xFF735C00))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                MedalCategory.entries.forEach { item ->
                    Surface(Modifier.clickable { onSelect(item) }, shape = CircleShape,
                        color = if (selected == item) Lemon else GrowthBlue) {
                        Text(item.label, Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = if (selected == item) Color(0xFF6C5700) else GrowthMuted)
                    }
                }
            }
            if (visibleBadges.isEmpty()) Text("这个分类还没有勋章", color = SlateMuted)
            visibleBadges.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { badge ->
                        MedalCard(badge, GrowthRules.badgeStages(snapshot, badge), Modifier.weight(1f), onBadgeClick)
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun MedalCard(badge: GrowthBadge, stages: List<GrowthBadgeStage>, modifier: Modifier, onClick: (GrowthBadge) -> Unit) {
    val current = stages.firstOrNull { it.isCurrent }
    val next = stages.firstOrNull { !it.isObtained }
    val progress = when {
        current != null -> current.progress
        next != null && next.threshold > 0 -> (badge.rawValue.toFloat() / next.threshold).coerceIn(0f, 1f)
        else -> 1f
    }
    val target = current?.nextThreshold ?: next?.threshold
    val progressText = if (target == null) "已达到最高等级"
        else "${current?.currentValue ?: badge.rawValue} / $target ${badge.unit}"
    Surface(modifier.height(174.dp).clickable { onClick(badge) }, shape = RoundedCornerShape(19.dp),
        color = GrowthBlue, border = BorderStroke(1.dp, GrowthOutline.copy(alpha = 0.3f)), shadowElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = GrowthYellowSoft.copy(alpha = 0.65f)) {
                Box(Modifier.size(57.dp), contentAlignment = Alignment.Center) {
                    Icon(badgeIcon(badge.key), badge.title, tint = Color(0xFF735C00), modifier = Modifier.size(34.dp))
                }
            }
            Text(badge.title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = GrowthInk,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (badge.level == 0) "尚未获得" else "已获得 ${badge.level} 枚", fontSize = 10.sp, color = GrowthMuted)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = Lemon, trackColor = GrowthBlueTrack)
            Text(progressText, fontSize = 10.sp, color = GrowthGreen, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BadgeDetailDialog(badge: GrowthBadge, stages: List<GrowthBadgeStage>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(badge.title) },
        text = {
            Column(Modifier.fillMaxWidth().height(390.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                stages.forEach { stage ->
                    Surface(shape = RoundedCornerShape(14.dp), color = if (stage.isCurrent) Butter else SkyBlueSoft.copy(alpha = 0.45f)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(badgeIcon(stage.key), stage.stageName, tint = LemonShadow, modifier = Modifier.size(28.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Lv.${stage.level} · ${stage.stageName}", fontWeight = FontWeight.Bold, color = SlateInk)
                                Text("需要累计 ${stage.threshold} ${stage.unit}", fontSize = 12.sp, color = SlateMuted)
                            }
                            Text(if (stage.isObtained) "已获得" else "未获得", fontSize = 11.sp,
                                color = if (stage.isObtained) GrowthGreen else GrowthMuted, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun MilestoneSection(events: List<GrowthEvent>) {
    var expanded by remember { mutableStateOf(false) }
    val recent = events.take(10)
    val visible = if (expanded) recent else recent.take(3)
    GrowthPanel(Color.White) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text("👣 最近的成长足迹", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = GrowthInk)
            if (visible.isEmpty()) Text("暂时还没有成长足迹", color = SlateMuted)
            visible.forEach { event ->
                val text = GrowthRules.eventText(event)
                Surface(shape = RoundedCornerShape(16.dp), color = GrowthBlue,
                    border = BorderStroke(1.dp, GrowthOutline.copy(alpha = 0.2f))) {
                    Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(Modifier.size(36.dp), shape = RoundedCornerShape(11.dp), color = Color(0xFFFFDADC)) {
                            Box(contentAlignment = Alignment.Center) { Text("✦", color = Color(0xFFA93349), fontSize = 19.sp) }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(text.first, fontWeight = FontWeight.Bold, color = GrowthInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(text.second, fontSize = 11.sp, color = GrowthMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        GrowthRules.shanghaiDate(event.occurredAt)?.let { Text(it, fontSize = 10.sp, color = GrowthMuted) }
                    }
                }
            }
            if (recent.size > 3) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(if (expanded) "收起" else "展开剩余 ${recent.size - 3} 条")
                    Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(profile: KidProfileUiState, onPlanClick: () -> Unit, onSwitchClick: () -> Unit) {
    GrowthPanel(Color.White) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("设置与账户", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = SlateInk)
            SettingsAction(Icons.Filled.CalendarMonth, "计划", "查看未来任务安排", onPlanClick)
            SettingsAction(Icons.Filled.Lock, "使用限制", if (profile.appLimits.isEmpty()) "当前没有已配置的使用限制" else profile.appLimits.joinToString(" · ") { "${it.appName} ${it.dailyLimitMinutes}分钟/天" }, null)
            SettingsAction(Icons.AutoMirrored.Filled.Logout, "切换账号", "重新输入绑定码", onSwitchClick)
        }
    }
}

@Composable
private fun SettingsAction(icon: ImageVector, title: String, detail: String, onClick: (() -> Unit)?) {
    val modifier = if (onClick == null) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().clickable(onClick = onClick)
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = Butter) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
    Surface(shape = RoundedCornerShape(24.dp), color = color,
        border = BorderStroke(1.dp, GrowthOutline.copy(alpha = 0.3f)), shadowElevation = 2.dp) {
        Column { content() }
    }
}
