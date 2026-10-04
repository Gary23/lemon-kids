package com.lemonkids.kidtask.navigation

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavHostController
import com.lemonkids.kidtask.feature.calendar.CalendarScreen
import com.lemonkids.kidtask.feature.home.HomeScreen
import com.lemonkids.kidtask.feature.home.HomeViewModel
import com.lemonkids.kidtask.feature.plan.PlanScreen
import com.lemonkids.kidtask.feature.profile.ProfileScreen
import com.lemonkids.kidtask.feature.reward.RewardScreen
import com.lemonkids.kidtask.ui.theme.Pink
import com.lemonkids.kidtask.ui.components.ChildSummary
import com.lemonkids.kidtask.ui.theme.Canvas
import com.lemonkids.kidtask.ui.theme.Lemon
import com.lemonkids.kidtask.ui.theme.LemonBorder
import com.lemonkids.kidtask.ui.theme.LemonShadow
import com.lemonkids.kidtask.ui.theme.SlateInk
import com.lemonkids.kidtask.ui.theme.SlateMuted
import com.lemonkids.shared.ui.auth.AuthViewModel
import com.lemonkids.shared.ui.auth.BindingCodeScreen

sealed class KidTaskTab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Home : KidTaskTab("home", "今日任务", Icons.Filled.Checklist)
    data object Calendar : KidTaskTab("calendar", "任务日历", Icons.Filled.CalendarMonth)
    data object Reward : KidTaskTab("reward", "奖励兑换", Icons.Filled.CardGiftcard)
    data object Profile : KidTaskTab("profile", "我的成长", Icons.Filled.ChildCare)
}

object KidTaskRoutes {
    const val BINDING_CODE = "task_binding_code"
    const val MAIN = "task_main"
}

@Composable
fun KidTaskNavGraph(authViewModel: AuthViewModel = hiltViewModel()) {
    val uiState by authViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

    if (!uiState.isFirstCheckComplete) {
        KidTaskWelcomeScreen()
        if (uiState.requiresSessionRecovery) {
            TaskSessionRecoveryDialog(
                isRecovering = uiState.isRecoveringSession,
                message = uiState.sessionRecoveryMessage,
                onRetryRefresh = authViewModel::retrySessionRefresh,
                onRestoreWithBinding = authViewModel::restoreSavedBindingCodeFromDialog
            )
        }
        return
    }

    val navController = rememberNavController()
    val startDest = if (uiState.isLoggedIn) KidTaskRoutes.MAIN else KidTaskRoutes.BINDING_CODE

    NavHost(navController = navController, startDestination = startDest) {
        composable(KidTaskRoutes.BINDING_CODE) {
            BindingCodeScreen(
                type = "task",
                deviceId = deviceId,
                onSuccess = {
                    navController.navigate(KidTaskRoutes.MAIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(KidTaskRoutes.MAIN) {
            KidTaskMainScreen(authViewModel)
        }
    }

    // 放在任务端根层且位于内容之后，确保会话失效时阻止继续提交任务、奖励等请求。
    if (uiState.requiresSessionRecovery) {
        TaskSessionRecoveryDialog(
            isRecovering = uiState.isRecoveringSession,
            message = uiState.sessionRecoveryMessage,
            onRetryRefresh = authViewModel::retrySessionRefresh,
            onRestoreWithBinding = authViewModel::restoreSavedBindingCodeFromDialog
        )
    }
}

@Composable
private fun TaskSessionRecoveryDialog(
    isRecovering: Boolean,
    message: String?,
    onRetryRefresh: () -> Unit,
    onRestoreWithBinding: () -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 20.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "登录连接需要恢复",
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    color = Color(0xFF303030),
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "当前登录凭证未能刷新。请先重试；如果仍无法恢复，可使用本机已保存的绑定码静默重新登录。",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF666666)
                )
                message?.let {
                    Text(it, color = Color(0xFFE53935), fontSize = 13.sp)
                }
                if (isRecovering) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Pink
                        )
                        Text("正在恢复登录…", color = Color(0xFF303030), fontSize = 14.sp)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onRetryRefresh,
                        enabled = !isRecovering,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("重试刷新")
                    }
                    Button(
                        onClick = onRestoreWithBinding,
                        enabled = !isRecovering,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Pink)
                    ) {
                        Text("使用绑定码登录")
                    }
                }
            }
        }
    }
}

@Composable
fun KidTaskMainScreen(authViewModel: AuthViewModel) {
    val navController = rememberNavController()
    val tabs = listOf(KidTaskTab.Home, KidTaskTab.Calendar, KidTaskTab.Reward, KidTaskTab.Profile)
    val authState by authViewModel.uiState.collectAsState()
    val homeViewModel: HomeViewModel = hiltViewModel()
    val homeState by homeViewModel.uiState.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val selectedTab = if (currentRoute == "plan") KidTaskTab.Profile.route else currentRoute
    val onSelect: (KidTaskTab) -> Unit = { tab ->
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = tab != KidTaskTab.Profile
        }
    }

    Row(modifier = Modifier.fillMaxSize().background(Canvas)) {
        KidTaskSidebar(
            tabs = tabs,
            selectedRoute = selectedTab,
            points = homeState.points.takeIf { homeState.isPointsLoaded },
            pointsUnavailable = homeState.isPointsLoadTimedOut,
            streakDays = homeState.streakDays,
            userName = authState.currentUser?.name.orEmpty(),
            hasUser = authState.currentUser != null,
            onSelect = onSelect
        )
        KidTaskContent(navController, homeViewModel, Modifier.weight(1f))
    }
}

@Composable
private fun KidTaskSidebar(
    tabs: List<KidTaskTab>,
    selectedRoute: String?,
    points: Int?,
    pointsUnavailable: Boolean,
    streakDays: Int,
    userName: String,
    hasUser: Boolean,
    onSelect: (KidTaskTab) -> Unit
) {
    Surface(
        modifier = Modifier.width(184.dp).fillMaxHeight(),
        color = Color.White,
        border = BorderStroke(1.dp, LemonBorder.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.fillMaxHeight().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(16.dp), color = Lemon, modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text("🍋", fontSize = 27.sp) }
                    }
                    Column {
                        Text("柠檬任务", color = SlateInk, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                        Text("LEMON TASKS", color = SlateMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tabs.forEach { tab ->
                        val selected = selectedRoute == tab.route
                        val shape = RoundedCornerShape(22.dp)
                        Box(modifier = Modifier.fillMaxWidth().height(60.dp)) {
                            if (selected) Box(
                                modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 4.dp)
                                    .background(LemonShadow, shape)
                            )
                            Surface(
                                modifier = Modifier.fillMaxWidth().height(56.dp).clickable { onSelect(tab) },
                                shape = shape,
                                color = if (selected) Lemon else Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(tab.icon, contentDescription = null, tint = if (selected) SlateInk else SlateMuted, modifier = Modifier.size(23.dp))
                                    Text(tab.label, color = SlateInk, fontSize = 15.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
            Column {
                HorizontalDivider(color = LemonBorder)
                ChildSummary(userName, hasUser, points, pointsUnavailable, streakDays, Modifier.padding(top = 16.dp))
            }
        }
    }
}

@Composable
private fun KidTaskContent(navController: NavHostController, homeViewModel: HomeViewModel, modifier: Modifier) {
    NavHost(navController = navController, startDestination = KidTaskTab.Home.route, modifier = modifier) {
        composable(KidTaskTab.Home.route) { HomeScreen(viewModel = homeViewModel) }
        composable(KidTaskTab.Calendar.route) { CalendarScreen() }
        composable(KidTaskTab.Reward.route) {
            RewardScreen(
                onCalendarClick = { navController.navigate(KidTaskTab.Calendar.route) }
            )
        }
        composable(KidTaskTab.Profile.route) {
            val homeState by homeViewModel.uiState.collectAsState()
            ProfileScreen(
                realPoints = homeState.points.takeIf { homeState.isPointsLoaded },
                pointsUnavailable = homeState.isPointsLoadTimedOut,
                onPlanClick = { navController.navigate("plan") }
            )
        }
        composable("plan") { PlanScreen(onBack = { navController.popBackStack() }) }
    }
}

@Composable
private fun KidTaskWelcomeScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "\uD83C\uDF4B", fontSize = 56.sp)
            Spacer(Modifier.height(16.dp))
            Text("柠檬任务", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("正在准备你的小世界...", fontSize = 14.sp, color = Color.Gray)
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(Modifier.size(32.dp))
        }
    }
}
