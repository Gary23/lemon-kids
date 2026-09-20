package com.lemonkids.familyvideo.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lemonkids.familyvideo.data.FamilyVideoLibrary
import com.lemonkids.familyvideo.data.VideoCollection
import com.lemonkids.familyvideo.data.VideoMedia
import com.lemonkids.familyvideo.feature.home.Poster

/** 详情完全依据数据库 parent_id 展开，不再从云盘路径猜测季/部目录。 */
@Composable
fun CollectionDetailScreen(
    collectionId: String,
    library: FamilyVideoLibrary?,
    onBack: () -> Unit,
    onCollectionClick: (String) -> Unit,
    onManage: (String) -> Unit,
    onCreateChild: (String) -> Unit,
    onPlay: (String) -> Unit,
) {
    val collection = library?.collections?.firstOrNull { it.id == collectionId }
    val children = library?.childrenFor(collectionId).orEmpty()
    val media = library?.mediaFor(collectionId).orEmpty()
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text("‹ 回到动画库", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onBack).padding(20.dp, 18.dp, 20.dp, 0.dp))
            if (collection == null) {
                Text("条目不可用", Modifier.padding(20.dp), style = MaterialTheme.typography.headlineMedium)
                return@item
            }
            DetailHero(collection, children.size, media.size, onManage, onCreateChild)
        }
        if (children.isNotEmpty()) {
            item { Text("子剧集", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(20.dp, 20.dp, 20.dp, 8.dp)) }
            items(children, key = { it.id }) { child -> library?.let { ChildCard(child, it, onCollectionClick) } }
        }
        item { Text(if (children.isEmpty()) "视频" else "本剧集视频", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(20.dp, 20.dp, 20.dp, 8.dp)) }
        if (media.isEmpty()) item { Text("还没有视频。请在“编辑条目”中刷新绑定目录。", Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else items(media, key = { it.id }) { mediaItem -> VideoRow(mediaItem, onPlay) }
    }
}

@Composable
private fun DetailHero(collection: VideoCollection, childCount: Int, videoCount: Int, onManage: (String) -> Unit, onCreateChild: (String) -> Unit) = Card(
    Modifier.padding(horizontal = 20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
) { Column(Modifier.padding(16.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Poster(collection, Modifier.width(104.dp).height(144.dp))
        Column(Modifier.padding(start = 16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(collection.name, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(if (collection.mediaType == "movie") "🎬 电影" else "🌈 剧集", color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text("$childCount 个子剧集 · $videoCount 个视频", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { onManage(collection.id) }) { Icon(Icons.Filled.Edit, null); Spacer(Modifier.width(5.dp)); Text("编辑条目") }
        if (collection.mediaType == "series") FilledTonalButton(onClick = { onCreateChild(collection.id) }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(5.dp)); Text("新建子剧集") }
    }
} }

@Composable
private fun ChildCard(item: VideoCollection, library: FamilyVideoLibrary, onClick: (String) -> Unit) = Card(
    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).clickable { onClick(item.id) },
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
) {
    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Poster(item, Modifier.width(54.dp).height(72.dp))
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${library.childrenFor(item.id).size} 个子剧集 · ${library.mediaFor(item.id).size} 个视频", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("进入", color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun VideoRow(item: VideoMedia, onPlay: (String) -> Unit) = Card(
    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp).clickable { onPlay(item.id) },
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
) { Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.width(44.dp).height(34.dp).background(Brush.linearGradient(listOf(Color(0xFFFFB8CF), Color(0xFFC9D4FF))), MaterialTheme.shapes.small), contentAlignment = Alignment.Center) { Text("▶", color = Color(0xFF752243)) }
    Column(Modifier.padding(start = 12.dp).weight(1f)) { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium); Text(item.durationSeconds?.let { "${it / 60} 分钟" } ?: "来自绑定目录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    Text("播放", color = MaterialTheme.colorScheme.primary)
} }
