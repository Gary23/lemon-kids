package com.lemonkids.kidtask.navigation

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lemonkids.kidtask.feature.calendar.CalendarScreen
import com.lemonkids.kidtask.feature.home.HomeScreen
import com.lemonkids.kidtask.feature.plan.PlanScreen
import com.lemonkids.kidtask.feature.profile.ProfileScreen
import com.lemonkids.kidtask.feature.reward.RewardScreen
import com.lemonkids.kidtask.ui.theme.Pink
import com.lemonkids.kidtask.ui.theme.PinkSoft
import com.lemonkids.shared.ui.auth.AuthViewModel
import com.lemonkids.shared.ui.auth.BindingCodeScreen

sealed class KidTaskTab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Home : KidTaskTab("home", "任务", Icons.Filled.Checklist)
    data object Calendar : KidTaskTab("calendar", "日历", Icons.Filled.CalendarMonth)
    data object Reward : KidTaskTab("reward", "奖励", Icons.Filled.CardGiftcard)
    data object Profile : KidTaskTab("profile", "我的", Icons.Filled.ChildCare)
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
            KidTaskMainScreen()
        }
    }
}

@Composable
fun KidTaskMainScreen() {
    val navController = rememberNavController()
    val tabs = listOf(KidTaskTab.Home, KidTaskTab.Calendar, KidTaskTab.Reward, KidTaskTab.Profile)
    val surfaceColor = Color.White

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = Pink.copy(alpha = 0.3f),
                        spotColor = Pink.copy(alpha = 0.3f)
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .background(surfaceColor),
                containerColor = surfaceColor,
                tonalElevation = 0.dp
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                tabs.forEach { tab ->
                    val isSelected = currentDestination?.hierarchy?.any { it.route == tab.route } == true

                    NavigationBarItem(
                        icon = {
                            Icon(
                                tab.icon,
                                contentDescription = tab.label,
                                modifier = Modifier.size(26.dp)
                            )
                        },
                        label = {
                            Text(
                                tab.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal
                            )
                        },
                        selected = isSelected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = tab != KidTaskTab.Profile
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Pink,
                            selectedTextColor = Pink,
                            unselectedIconColor = Color(0xFFAAAAAA),
                            unselectedTextColor = Color(0xFFAAAAAA),
                            indicatorColor = PinkSoft.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = KidTaskTab.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(KidTaskTab.Home.route) { HomeScreen() }
            composable(KidTaskTab.Calendar.route) { CalendarScreen() }
            composable(KidTaskTab.Reward.route) { RewardScreen() }
            composable(KidTaskTab.Profile.route) {
                ProfileScreen(onPlanClick = { navController.navigate("plan") })
            }
            composable("plan") {
                PlanScreen(onBack = { navController.popBackStack() })
            }
        }
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