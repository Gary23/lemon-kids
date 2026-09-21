package com.lemonkids.familyvideo.feature.player

import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.lemonkids.familyvideo.data.CloudDriveProvider
import com.lemonkids.familyvideo.data.VideoMedia
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VideoPlayerViewModel @Inject constructor(private val drive: CloudDriveProvider) : ViewModel() {
    var playbackUrl by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    private var automaticRecoveryUsed = false

    fun load(fileId: String) = viewModelScope.launch {
        automaticRecoveryUsed = false
        requestPlaybackUrl(fileId)
    }

    fun recoverPlayback(fileId: String) {
        if (loading) return
        if (automaticRecoveryUsed) {
            playbackUrl = null
            error = "播放连接已自动更新一次仍不可用，请手动重试"
            return
        }
        automaticRecoveryUsed = true
        viewModelScope.launch { requestPlaybackUrl(fileId) }
    }

    private suspend fun requestPlaybackUrl(fileId: String) {
        loading = true; error = null; playbackUrl = null
        drive.freshPlaybackUrl(fileId).fold(
            { playbackUrl = it; loading = false },
            { error = it.message ?: "无法获取播放地址"; loading = false }
        )
    }
}

@Composable
fun VideoPlayerScreen(media: VideoMedia?, onBack: () -> Unit, viewModel: VideoPlayerViewModel = hiltViewModel()) {
    LaunchedEffect(media?.driveFileId) { media?.driveFileId?.takeIf { it.isNotBlank() }?.let(viewModel::load) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(20.dp)) {
        Text("‹ 回到选集", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onBack).padding(bottom = 18.dp))
        Text(media?.name ?: "视频不可用", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        when {
            media == null -> Button(onClick = onBack) { Text("返回") }
            viewModel.loading -> CircularProgressIndicator()
            viewModel.playbackUrl != null -> OnlinePlayer(viewModel.playbackUrl!!) { viewModel.recoverPlayback(media.driveFileId) }
            else -> {
                Text(viewModel.error ?: "播放地址会在点击视频时由云盘服务临时签发。")
                Spacer(Modifier.height(16.dp))
                Button(onClick = { viewModel.load(media.driveFileId) }) { Text("重试") }
            }
        }
    }
}

@Composable
private fun OnlinePlayer(url: String, onPlaybackError: () -> Unit) {
    val context = LocalContext.current
    val player = remember(context, url) { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(url)); prepare() } }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = onPlaybackError()
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    AndroidView(
        factory = { viewContext -> PlayerView(viewContext).apply { this.player = player; layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) } },
        modifier = Modifier.fillMaxWidth().height(230.dp)
    )
    Text("播放链接失效时可返回后再次打开视频获取新链接。", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
}
