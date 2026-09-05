package com.lemonkids.familyvideo.feature.player

import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.lemonkids.familyvideo.data.VideoMedia

@Composable
fun VideoPlayerScreen(media: VideoMedia?, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("‹ 返回选集", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 14.dp))
        Text(media?.name ?: "视频不可用", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        if (media?.playbackUrl.isNullOrBlank()) {
            Text("播放地址会在点击播放时由 123 云盘授权服务临时签发。请先完成云盘连接与同步。")
            Spacer(Modifier.height(16.dp)); Button(onBack) { Text("返回") }
        } else {
            val context = LocalContext.current
            val player = remember(context, media.playbackUrl) { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(media.playbackUrl!!)); prepare() } }
            DisposableEffect(player) { onDispose { player.release() } }
            AndroidView(factory = { context -> PlayerView(context).apply { this.player = player; layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) } }, modifier = Modifier.fillMaxWidth().height(230.dp))
            Text("临时地址失效时，播放器应通过 CloudDriveProvider 刷新并恢复当前位置。", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
        }
    }
}
