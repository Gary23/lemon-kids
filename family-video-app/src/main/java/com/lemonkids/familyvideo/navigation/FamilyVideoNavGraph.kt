package com.lemonkids.familyvideo.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lemonkids.familyvideo.feature.auth.VideoLoginScreen
import com.lemonkids.familyvideo.feature.home.VideoHomeScreen
import com.lemonkids.familyvideo.feature.home.VideoHomeViewModel
import com.lemonkids.familyvideo.feature.library.CollectionDetailScreen
import com.lemonkids.familyvideo.feature.player.VideoPlayerScreen
import com.lemonkids.familyvideo.feature.profile.MediaLibraryManageScreen
import com.lemonkids.familyvideo.feature.profile.VideoProfileScreen
import com.lemonkids.shared.ui.auth.AuthViewModel

@Composable
fun FamilyVideoNavGraph(authViewModel: AuthViewModel = hiltViewModel()) {
    val auth by authViewModel.uiState.collectAsState()
    if (!auth.isFirstCheckComplete) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("正在打开柠檬视频…") }; return }
    val root = rememberNavController()
    NavHost(root, startDestination = if (auth.isLoggedIn) "main" else "login") {
        composable("login") {
            VideoLoginScreen(onSuccess = {
                root.navigate("main") { popUpTo("login") { inclusive = true } }
            })
        }
        composable("main") { VideoMainScreen(root, authViewModel) }
        composable("detail/{collectionId}") { entry ->
            val home: VideoHomeViewModel = hiltViewModel(root.getBackStackEntry("main"))
            val collectionId = entry.arguments?.getString("collectionId").orEmpty()
            CollectionDetailScreen(
                collectionId = collectionId,
                library = home.state.library,
                onBack = { root.popBackStack() },
                onCollectionClick = { root.navigate("detail/$it") },
                onManage = { root.navigate("manage/edit/$it") },
                onCreateChild = { root.navigate("manage/new/$it") },
                onPlay = { root.navigate("player/$it") },
            )
        }
        composable("manage") {
            val home: VideoHomeViewModel = hiltViewModel(root.getBackStackEntry("main"))
            MediaLibraryManageScreen(onBack = { home.refresh(); root.popBackStack() })
        }
        composable("manage/edit/{collectionId}") { entry ->
            val home: VideoHomeViewModel = hiltViewModel(root.getBackStackEntry("main"))
            MediaLibraryManageScreen(onBack = { home.refresh(); root.popBackStack() }, openCollectionId = entry.arguments?.getString("collectionId"))
        }
        composable("manage/new/{parentId}") { entry ->
            val home: VideoHomeViewModel = hiltViewModel(root.getBackStackEntry("main"))
            MediaLibraryManageScreen(onBack = { home.refresh(); root.popBackStack() }, initialParentId = entry.arguments?.getString("parentId"))
        }
        composable("player/{mediaId}") { entry ->
            val home: VideoHomeViewModel = hiltViewModel(root.getBackStackEntry("main"))
            VideoPlayerScreen(home.state.library?.media?.firstOrNull { it.id == entry.arguments?.getString("mediaId") }, onBack = { root.popBackStack() })
        }
    }
}

@Composable private fun VideoMainScreen(root: androidx.navigation.NavHostController, auth: AuthViewModel) {
    // 详情页会从 main 返回栈项获取该 ViewModel。主页也必须使用同一作用域，
    // 否则详情页会得到一个尚未加载媒体库的新实例，进而错误显示“0 个视频”。
    val homeViewModel: VideoHomeViewModel = hiltViewModel()
    val tabs = rememberNavController()
    val tabEntry by tabs.currentBackStackEntryAsState()
    val selectedRoute = tabEntry?.destination?.route ?: "home"
    Scaffold(bottomBar = {
        NavigationBar(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface) {
            listOf("home" to "动画", "profile" to "我的").forEach { (route, label) ->
                NavigationBarItem(
                    selected = selectedRoute == route,
                    onClick = { if (selectedRoute != route) tabs.navigate(route) { launchSingleTop = true } },
                    icon = { Icon(if (route == "home") Icons.Filled.Home else Icons.Filled.Person, label) },
                    label = { Text(label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                        indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            }
        }
    }) { padding ->
        NavHost(tabs, "home", Modifier.padding(padding)) {
            composable("home") { VideoHomeScreen(onCollectionClick = { root.navigate("detail/$it") }, onManageClick = { root.navigate("manage") }, viewModel = homeViewModel) }
            composable("profile") { VideoProfileScreen(onManageLibrary = { root.navigate("manage") }, onSignedOut = { root.navigate("login") { popUpTo("main") { inclusive = true } } }, authViewModel = auth) }
        }
    }
}
