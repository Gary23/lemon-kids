package com.lemonkids.familyvideo.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lemonkids.familyvideo.data.FamilyVideoLibrary

@Composable
fun CollectionDetailScreen(collectionId: String, library: FamilyVideoLibrary?, onBack: () -> Unit, onPlay: (String) -> Unit) {
    val collection = library?.collections?.firstOrNull { it.id == collectionId }
    val media = library?.mediaFor(collectionId).orEmpty()
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("‹ 返回", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onBack))
        Spacer(Modifier.size(16.dp))
        Text(collection?.name ?: "动画详情", style = MaterialTheme.typography.headlineLarge)
        Text("${media.size} 个视频", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(18.dp))
        if (media.isEmpty()) Text("这个目录还没有同步到可播放的视频。")
        media.forEachIndexed { index, item ->
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onPlay(item.id) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("${index + 1}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Column(Modifier.weight(1f)) { Text(item.name, fontWeight = FontWeight.Medium); Text(if (item.durationSeconds == null) "等待云盘提供时长" else "${item.durationSeconds / 60} 分钟", style = MaterialTheme.typography.bodyMedium) }
                    Text("播放")
                }
            }
        }
    }
}
