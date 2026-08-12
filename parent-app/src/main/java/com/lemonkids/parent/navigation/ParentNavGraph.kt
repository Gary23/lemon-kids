package com.lemonkids.parent.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.lemonkids.parent.feature.auth.ParentCreateFamilyScreen
import com.lemonkids.parent.feature.auth.ParentLoginScreen
import com.lemonkids.parent.feature.auth.ParentRegisterScreen
import com.lemonkids.parent.feature.monitor.MonitorScreen
import com.lemonkids.parent.feature.profile.CategoryManageScreen
import com.lemonkids.parent.feature.profile.DeviceStatusLogScreen
import com.lemonkids.parent.feature.profile.FamilyManageScreen
import com.lemonkids.parent.feature.profile.RecycleBinScreen
import com.lemonkids.parent.feature.profile.ProfileScreen as ParentProfileScreen
import com.lemonkids.parent.feature.tasks.TaskEditScreen
import com.lemonkids.parent.feature.tasks.TasksScreen
import com.lemonkids.shared.ui.auth.AuthViewModel

sealed class ParentTab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Tasks : ParentTab("tasks", "任务", Icons.Filled.DateRange)
    data object Monitor : ParentTab("monitor", "监控", Icons.Filled.Insights)
    data object Profile : ParentTab("profile", "我的", Icons.Filled.Person)
}

object ParentRoutes {
    const val LOGIN = "parent_login"
    const val REGISTER = "parent_register"
    const val CREATE_FAMILY = "parent_create_family"
    const val MAIN = "parent_main"
}

@Composable
fun ParentNavGraph(authViewModel: AuthViewModel = hiltViewModel()) {
    val uiState by authViewModel.uiState.collectAsState()

    if (!uiState.isFirstCheckComplete) {
        ParentWelcomeScreen()
        return
    }

    val navController = rememberNavController()
    val startDest = when {
        !uiState.isLoggedIn -> ParentRoutes.LOGIN
        uiState.needsFamilySetup -> ParentRoutes.CREATE_FAMILY
        else -> ParentRoutes.MAIN
    }

    NavHost(navController = navController, startDestination = startDest) {
        composable(ParentRoutes.LOGIN) {
            ParentLoginScreen(
                onRegisterClick = { navController.navigate(ParentRoutes.REGISTER) },
                onLoginSuccess = {
                    val dest = if (uiState.needsFamilySetup) ParentRoutes.CREATE_FAMILY else ParentRoutes.MAIN
                    navController.navigate(dest) { popUpTo(ParentRoutes.LOGIN) { inclusive = true } }
                }
            )
        }

        composable(ParentRoutes.REGISTER) {
            ParentRegisterScreen(
                onBack = { navController.popBackStack() },
                onRegisterSuccess = { navController.navigate(ParentRoutes.CREATE_FAMILY) { popUpTo(ParentRoutes.LOGIN) { inclusive = true } } }
            )
        }

        composable(ParentRoutes.CREATE_FAMILY) {
            ParentCreateFamilyScreen(
                onComplete = { navController.navigate(ParentRoutes.MAIN) { popUpTo(0) { inclusive = true } } }
            )
        }

        composable(ParentRoutes.MAIN) {
            ParentMainScreen()
        }
    }
}

@Composable
private fun ParentMainScreen() {
    val navController = rememberNavController()
        val tabs = listOf(ParentTab.Tasks, ParentTab.Monitor, ParentTab.Profile)

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                    tabs.forEach { tab ->
                        NavigationBarItem(
                            icon = {
                                Icon(tab.icon, contentDescription = tab.label)
                            },
                            label = { Text(tab.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                // "我的"tab 不恢复子页面状态，始终回到主页
                                restoreState = tab != ParentTab.Profile
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ParentTab.Tasks.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ParentTab.Tasks.route) {
                TasksScreen(onCreateTask = {
                    navController.navigate("tasks/edit/new")
                }, onEditTask = { taskId ->
                    navController.navigate("tasks/edit/$taskId")
                })
            }
            composable(
                route = "tasks/edit/{taskId}",
                arguments = listOf(navArgument("taskId") {
                    type = NavType.StringType
                })
            ) { backStackEntry ->
                val taskId = backStackEntry.arguments?.getString("taskId")
                // 与 TasksScreen 共享同一个 ViewModel，使日历数据同步
                val tasksViewModel: com.lemonkids.parent.feature.tasks.TasksViewModel =
                    hiltViewModel(navController.getBackStackEntry(ParentTab.Tasks.route))
                TaskEditScreen(taskId = taskId, onBack = { navController.popBackStack() }, viewModel = tasksViewModel)
            }
            composable(ParentTab.Monitor.route) { MonitorScreen() }
            composable(ParentTab.Profile.route) {
                ParentProfileScreen(onFamilyManageClick = {
                    navController.navigate("family_manage")
                }, onCategoryManageClick = {
                    navController.navigate("category_manage")
                }, onRecycleBinClick = {
                    navController.navigate("recycle_bin")
                }, onDeviceStatusLogClick = {
                    navController.navigate("device_status_logs")
                })
            }
            composable("family_manage") {
                FamilyManageScreen(onBack = { navController.popBackStack() })
            }
            composable("category_manage") {
                CategoryManageScreen(onBack = { navController.popBackStack() })
            }
            composable("recycle_bin") {
                RecycleBinScreen(onBack = { navController.popBackStack() })
            }
            composable("device_status_logs") {
                DeviceStatusLogScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun ParentWelcomeScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "\uD83C\uDF4B", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text("柠檬小管家", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("正在加载...", fontSize = 14.sp, color = androidx.compose.ui.graphics.Color.Gray)
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator(Modifier.size(28.dp))
        }
    }
}
