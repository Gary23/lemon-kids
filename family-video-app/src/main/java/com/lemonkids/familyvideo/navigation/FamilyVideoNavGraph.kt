package com.lemonkids.familyvideo.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lemonkids.familyvideo.feature.auth.VideoLoginScreen
import com.lemonkids.familyvideo.feature.home.VideoHomeScreen
import com.lemonkids.familyvideo.feature.home.VideoHomeViewModel
import com.lemonkids.familyvideo.feature.library.CollectionDetailScreen
import com.lemonkids.familyvideo.feature.player.VideoPlayerScreen
import com.lemonkids.familyvideo.feature.profile.VideoCategoryScreen
import com.lemonkids.familyvideo.feature.profile.VideoProfileScreen
import com.lemonkids.shared.ui.auth.AuthViewModel

@Composable
fun FamilyVideoNavGraph(authViewModel: AuthViewModel = hiltViewModel()) {
    val auth by authViewModel.uiState.collectAsState()
    if (!auth.isFirstCheckComplete) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("正在打开家庭动画…") }; return }
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
            CollectionDetailScreen(entry.arguments?.getString("collectionId").orEmpty(), home.state.library, onBack = { root.popBackStack() }, onPlay = { root.navigate("player/$it") })
        }
        composable("player/{mediaId}") { entry ->
            val home: VideoHomeViewModel = hiltViewModel(root.getBackStackEntry("main"))
            VideoPlayerScreen(home.state.library?.media?.firstOrNull { it.id == entry.arguments?.getString("mediaId") }, onBack = { root.popBackStack() })
        }
        composable("categories") { VideoCategoryScreen(onBack = { root.popBackStack() }) }
    }
}

@Composable private fun VideoMainScreen(root: androidx.navigation.NavHostController, auth: AuthViewModel) {
    val tabs = rememberNavController()
    Scaffold(bottomBar = { NavigationBar { listOf("home" to "首页", "profile" to "我的").forEach { (route, label) -> NavigationBarItem(selected = false, onClick = { tabs.navigate(route) }, icon = { Icon(if (route == "home") Icons.Filled.Home else Icons.Filled.Person, label) }, label = { Text(label) }) } } }) { padding ->
        NavHost(tabs, "home", Modifier.padding(padding)) {
            composable("home") { VideoHomeScreen(onCollectionClick = { root.navigate("detail/$it") }, onSyncClick = { tabs.navigate("profile") }) }
            composable("profile") { VideoProfileScreen(onCategories = { root.navigate("categories") }, onSignedOut = { root.navigate("login") { popUpTo("main") { inclusive = true } } }, authViewModel = auth) }
        }
    }
}
