package com.lemonkids.familyvideo.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.familyvideo.data.CloudDriveProvider
import com.lemonkids.familyvideo.data.DriveConnection
import com.lemonkids.shared.ui.auth.AuthViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VideoProfileUiState(val loading: Boolean = true, val working: Boolean = false, val connection: DriveConnection = DriveConnection(), val message: String? = null)

@HiltViewModel
class VideoProfileViewModel @Inject constructor(private val drive: CloudDriveProvider) : ViewModel() {
    var state by mutableStateOf(VideoProfileUiState()); private set
    fun load() = viewModelScope.launch {
        state = state.copy(loading = true)
        drive.connection().fold({ state = state.copy(loading = false, connection = it) }, { state = state.copy(loading = false, message = it.message ?: "无法读取云盘连接状态") })
    }
    fun connect() = viewModelScope.launch {
        state = state.copy(working = true, message = null)
        drive.connect().fold({ state = state.copy(working = false, connection = it, message = "123 云盘已连接") }, { state = state.copy(working = false, message = it.message ?: "连接失败，请检查服务端密钥配置") })
    }
}

@Composable
fun VideoProfileScreen(onManageLibrary: () -> Unit, onSignedOut: () -> Unit, authViewModel: AuthViewModel, viewModel: VideoProfileViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.load() }
    val state = viewModel.state
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("家长小站", style = MaterialTheme.typography.headlineLarge)
        Text("在这里整理动画和管理连接", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("☁️ 123 云盘", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(if (state.connection.status == "connected") state.connection.accountHint ?: "已连接" else "未连接")
            Text("云盘只用于浏览目录、刷新视频和临时播放，不会建立或修改云盘目录。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Button(onClick = viewModel::connect, enabled = !state.working) { Text(if (state.connection.status == "connected") "重新连接" else "连接 123 云盘") }
        } }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🍿 动画整理", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("创建剧集或电影，绑定一个云盘目录；需要分季时，在剧集下新建子剧集并分别绑定目录。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Button(onClick = onManageLibrary) { Text("整理动画库") }
        } }
        if (state.loading || state.working) CircularProgressIndicator()
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        OutlinedButton(onClick = { authViewModel.signOut(); onSignedOut() }, modifier = Modifier.fillMaxWidth()) { Text("退出登录") }
    }
}
