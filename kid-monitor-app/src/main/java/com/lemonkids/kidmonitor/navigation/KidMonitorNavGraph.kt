package com.lemonkids.kidmonitor.navigation

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
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Insights
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lemonkids.kidmonitor.feature.profile.ProfileScreen
import com.lemonkids.kidmonitor.feature.usage.AppHourlyDetailScreen
import com.lemonkids.kidmonitor.feature.usage.AppUsageDetailScreen
import com.lemonkids.kidmonitor.feature.usage.UsageDetailScreen
import com.lemonkids.shared.ui.auth.AuthViewModel
import com.lemonkids.shared.ui.auth.BindingCodeScreen

sealed class KidMonitorTab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Usage : KidMonitorTab("usage", "使用", Icons.Filled.Insights)
    data object Profile : KidMonitorTab("profile", "我的", Icons.Filled.ChildCare)
}

object KidMonitorRoutes {
    const val BINDING_CODE = "monitor_binding_code"
    const val MAIN = "monitor_main"
}

@Composable
fun KidMonitorNavGraph(authViewModel: AuthViewModel = hiltViewModel()) {
    val uiState by authViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

    if (!uiState.isFirstCheckComplete) {
        KidMonitorWelcomeScreen()
        return
    }

    val navController = rememberNavController()
    val startDest = if (uiState.isLoggedIn) KidMonitorRoutes.MAIN else KidMonitorRoutes.BINDING_CODE

    NavHost(navController = navController, startDestination = startDest) {
        composable(KidMonitorRoutes.BINDING_CODE) {
            BindingCodeScreen(
                type = "monitor",
                deviceId = deviceId,
                onSuccess = {
                    navController.navigate(KidMonitorRoutes.MAIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(KidMonitorRoutes.MAIN) {
            KidMonitorMainScreen()
        }
    }
}

@Composable
fun KidMonitorMainScreen() {
    val navController = rememberNavController()
    val tabs = listOf(KidMonitorTab.Usage, KidMonitorTab.Profile)
    val primaryColor = Color(0xFF2196F3)
    val surfaceColor = Color(0xFFF8FBF8)

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .shadow(12.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
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
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        selected = isSelected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = tab != KidMonitorTab.Profile
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = primaryColor,
                            selectedTextColor = primaryColor,
                            unselectedIconColor = Color(0xFF9E9E9E),
                            unselectedTextColor = Color(0xFF9E9E9E),
                            indicatorColor = primaryColor.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = KidMonitorTab.Usage.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(KidMonitorTab.Usage.route) {
                UsageDetailScreen(
                    onBack = { },
                    onAppClick = { pkg, start, end, name ->
                        navController.navigate("app_usage_detail/$pkg/$start/$end/$name")
                    },
                    onDayAppClick = { pkg, name ->
                        navController.navigate("app_hourly_detail/$pkg/$name")
                    }
                )
            }
            composable(KidMonitorTab.Profile.route) {
                ProfileScreen()
            }
            composable(
                route = "app_usage_detail/{packageName}/{startDate}/{endDate}/{appName}",
                arguments = listOf(
                    navArgument("packageName") { type = NavType.StringType },
                    navArgument("startDate") { type = NavType.StringType },
                    navArgument("endDate") { type = NavType.StringType },
                    navArgument("appName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                AppUsageDetailScreen(
                    packageName = backStackEntry.arguments?.getString("packageName") ?: "",
                    startDate = backStackEntry.arguments?.getString("startDate") ?: "",
                    endDate = backStackEntry.arguments?.getString("endDate") ?: "",
                    appName = backStackEntry.arguments?.getString("appName") ?: "",
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "app_hourly_detail/{packageName}/{appName}",
                arguments = listOf(
                    navArgument("packageName") { type = NavType.StringType },
                    navArgument("appName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                AppHourlyDetailScreen(
                    packageName = backStackEntry.arguments?.getString("packageName") ?: "",
                    appName = backStackEntry.arguments?.getString("appName") ?: "",
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun KidMonitorWelcomeScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "\uD83D\uDD12", fontSize = 56.sp)
            Spacer(Modifier.height(16.dp))
            Text("柠檬监控", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("正在准备监控服务...", fontSize = 14.sp, color = Color.Gray)
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(Modifier.size(32.dp))
        }
    }
}
