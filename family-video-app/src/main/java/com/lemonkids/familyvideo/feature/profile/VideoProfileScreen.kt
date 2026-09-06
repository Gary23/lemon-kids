package com.lemonkids.familyvideo.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.familyvideo.data.CloudDriveProvider
import com.lemonkids.familyvideo.data.CloudFolder
import com.lemonkids.familyvideo.data.DriveConnection
import com.lemonkids.shared.ui.auth.AuthViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VideoProfileUiState(
    val loading: Boolean = true,
    val working: Boolean = false,
    val connection: DriveConnection = DriveConnection(),
    val folders: List<CloudFolder> = emptyList(),
    val browsingPath: String = "123 云盘",
    val browsingFolderId: String = "0",
    val showPicker: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class VideoProfileViewModel @Inject constructor(private val drive: CloudDriveProvider) : ViewModel() {
    var state by mutableStateOf(VideoProfileUiState()); private set

    fun load() = viewModelScope.launch {
        state = state.copy(loading = true)
        drive.connection().fold(
            { state = state.copy(loading = false, connection = it) },
            { state = state.copy(loading = false, message = it.message ?: "无法读取云盘连接状态") }
        )
    }

    fun connect() = viewModelScope.launch {
        state = state.copy(working = true, message = null)
        drive.connect().fold(
            { state = state.copy(working = false, connection = it, message = "123 云盘已连接") },
            { state = state.copy(working = false, message = it.message ?: "连接失败，请检查服务端密钥配置") }
        )
    }

    fun openPicker() = browse("0", "123 云盘", true)
    fun enter(folder: CloudFolder) = browse(folder.id, folder.breadcrumb, false)
    fun closePicker() { state = state.copy(showPicker = false) }

    fun selectCurrentFolder() = viewModelScope.launch {
        if (state.browsingFolderId == "0") { state = state.copy(message = "请进入并选择一个具体目录"); return@launch }
        state = state.copy(working = true)
        val folder = CloudFolder(state.browsingFolderId, state.browsingPath.substringAfterLast(" / "), state.browsingPath)
        drive.selectSyncRoot(folder).fold(
            { state = state.copy(working = false, showPicker = false, connection = it, message = "已选择同步目录") },
            { state = state.copy(working = false, message = it.message ?: "保存同步目录失败") }
        )
    }

    fun sync() = viewModelScope.launch {
        val rootId = state.connection.rootFolderId ?: run { state = state.copy(message = "请先选择同步目录"); return@launch }
        state = state.copy(working = true, message = null)
        drive.sync(rootId).fold(
            { summary -> state = state.copy(working = false, message = "同步完成：新增 ${summary.added} 个，更新 ${summary.updated} 个，不可用 ${summary.unavailable} 个") },
            { state = state.copy(working = false, message = it.message ?: "同步失败，请稍后重试") }
        )
    }

    private fun browse(folderId: String, path: String, showPicker: Boolean) = viewModelScope.launch {
        state = state.copy(working = true, showPicker = showPicker, message = null)
        drive.browse(folderId, path).fold(
            { state = state.copy(working = false, folders = it, browsingFolderId = folderId, browsingPath = path) },
            { state = state.copy(working = false, message = it.message ?: "读取云盘目录失败") }
        )
    }
}

@Composable
fun VideoProfileScreen(
    onCategories: () -> Unit,
    onSignedOut: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: VideoProfileViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { viewModel.load() }
    val state = viewModel.state
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("我的", style = MaterialTheme.typography.headlineLarge)
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) {
            Text("123 云盘同步", style = MaterialTheme.typography.titleLarge)
            Text(if (state.connection.status == "connected") state.connection.accountHint ?: "已连接" else "未连接")
            if (state.connection.status == "connected") Text("连接由服务端安全管理", style = MaterialTheme.typography.bodySmall)
            Button(onClick = viewModel::connect, enabled = !state.working) { Text(if (state.connection.status == "connected") "重新连接" else "连接 123 云盘") }
        } }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) {
            Text("同步目录", style = MaterialTheme.typography.titleLarge)
            Text(state.connection.rootPath ?: "选择一个根目录；第一层子文件夹会成为剧集或电影。", maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = viewModel::openPicker, enabled = !state.working && state.connection.status == "connected") { Text(if (state.connection.rootPath == null) "选择同步目录" else "更换目录") }
                Button(onClick = viewModel::sync, enabled = !state.working && state.connection.rootFolderId != null) { Text("立即同步") }
            }
        } }
        if (state.working) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { CircularProgressIndicator(); Text("正在处理云盘数据…") }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        OutlinedButton(onClick = onCategories, modifier = Modifier.fillMaxWidth()) { Text("分类管理") }
        OutlinedButton(onClick = { authViewModel.signOut(); onSignedOut() }, modifier = Modifier.fillMaxWidth()) { Text("退出登录") }
    }
    if (state.showPicker) FolderPicker(state.browsingPath, state.folders, state.browsingFolderId != "0", viewModel::enter, viewModel::selectCurrentFolder, viewModel::closePicker)
}

@Composable
private fun FolderPicker(path: String, folders: List<CloudFolder>, canSelectCurrent: Boolean, onFolder: (CloudFolder) -> Unit, onSelect: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择同步目录") },
        text = { Column {
            Text(path, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            LazyColumn { items(folders, key = { it.id }) { folder -> Text("📁 ${folder.name}", modifier = Modifier.fillMaxWidth().clickable { onFolder(folder) }.padding(vertical = 12.dp)) } }
            if (folders.isEmpty()) Text("这个目录下没有子文件夹")
        } },
        confirmButton = { TextButton(onClick = onSelect, enabled = canSelectCurrent) { Text("选择当前目录") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun VideoCategoryScreen(onBack: () -> Unit) = Column(Modifier.fillMaxSize().padding(20.dp)) {
    Text("‹ 返回", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onBack))
    Text("分类管理", style = MaterialTheme.typography.headlineLarge)
    Text("分类仅保存于 App 数据库，不会移动或更名云盘文件。同步完成后，可在此管理未分类、添加、改名和排序。", modifier = Modifier.padding(top = 12.dp))
}
