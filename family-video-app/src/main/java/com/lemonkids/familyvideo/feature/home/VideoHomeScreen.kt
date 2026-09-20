package com.lemonkids.familyvideo.feature.home

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.lemonkids.familyvideo.data.FamilyVideoLibrary
import com.lemonkids.familyvideo.data.FamilyVideoRepository
import com.lemonkids.familyvideo.data.VideoCollection
import com.lemonkids.shared.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VideoHomeUiState(val loading: Boolean = true, val library: FamilyVideoLibrary? = null, val error: String? = null)

@HiltViewModel
class VideoHomeViewModel @Inject constructor(private val repository: FamilyVideoRepository, private val auth: AuthRepository) : ViewModel() {
    var state by mutableStateOf(VideoHomeUiState()); private set
    fun refresh() = viewModelScope.launch {
        val user = auth.observeCurrentUser().firstOrNull()
        val familyId = user?.familyId ?: run {
            state = VideoHomeUiState(loading = false, error = "此账号尚未创建家庭，请先在家长端完成家庭设置。")
            return@launch
        }
        state = VideoHomeUiState(loading = true)
        repository.loadLibrary(familyId).fold(
            { state = VideoHomeUiState(loading = false, library = it) },
            { state = VideoHomeUiState(loading = false, error = "媒体库暂不可用：${it.message ?: "请检查数据库迁移"}") },
        )
    }
}

@Composable
fun VideoHomeScreen(onCollectionClick: (String) -> Unit, onManageClick: () -> Unit, viewModel: VideoHomeViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val state = viewModel.state
    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        state.error != null -> EmptyLibrary(state.error, onManageClick)
        else -> HomeContent(state.library!!, onCollectionClick, onManageClick)
    }
}

@Composable
private fun EmptyLibrary(message: String, onManageClick: () -> Unit) = Column(
    Modifier.fillMaxSize().padding(28.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
) {
    Text("🍿", fontSize = 52.sp, modifier = Modifier.size(64.dp))
    Text("这里很快会热闹起来", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(10.dp)); Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(20.dp)); Button(onClick = onManageClick) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("添加喜欢的动画") }
}

@Composable
private fun HomeContent(library: FamilyVideoLibrary, onCollectionClick: (String) -> Unit, onManageClick: () -> Unit) {
    var keyword by mutableStateOf("")
    val topLevel = library.topLevel()
    // 只按顶层剧集/电影名称检索；子剧集和视频文件名都不参与搜索。
    val results = topLevel.filter { it.name.contains(keyword.trim(), ignoreCase = true) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp)) {
        item {
            Card(
                Modifier.fillMaxWidth().padding(20.dp, 18.dp, 20.dp, 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("今天想看什么？", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("打开一段开心的动画时光吧", color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("🎈", style = MaterialTheme.typography.headlineLarge)
                }
            }
            OutlinedTextField(
                value = keyword, onValueChange = { keyword = it }, singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, "搜索动画") },
                placeholder = { Text("找找想看的动画") },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
        }
        if (keyword.isNotBlank()) {
            item { SectionTitle("搜索结果 · ${results.size}") }
            if (results.isEmpty()) item { Text("没有找到对应的剧集或电影", Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(results, key = { it.id }) { item -> SearchResult(item, library, onCollectionClick) }
        } else {
            val unfinished = library.playback.firstOrNull { !it.isCompleted && it.progressSeconds > 0 }?.let { record -> library.media.firstOrNull { it.id == record.mediaId } }
            if (unfinished != null) item { ContinueCard(unfinished.name) { onCollectionClick(unfinished.collectionId) } }
            item { SectionTitle("我的动画") }
            if (topLevel.isEmpty()) item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("还没有动画片", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onManageClick, modifier = Modifier.padding(top = 12.dp)) { Text("添加动画") }
                }
            }
            else item { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp)) { items(topLevel, key = { it.id }) { collection -> CollectionCard(collection, library) { onCollectionClick(collection.id) } } } }
        }
    }
}

@Composable private fun SectionTitle(title: String) = Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 12.dp))
@Composable private fun ContinueCard(name: String, onClick: () -> Unit) = Card(
    Modifier.fillMaxWidth().padding(20.dp, 16.dp, 20.dp, 0.dp).clickable(onClick = onClick),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
    Text("▶", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium)
    Column(Modifier.padding(start = 12.dp)) { Text("继续观看", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Text(name, style = MaterialTheme.typography.titleLarge); Text("从上次看到的地方继续", style = MaterialTheme.typography.bodyMedium) }
} }

@Composable
private fun CollectionCard(item: VideoCollection, library: FamilyVideoLibrary, onClick: () -> Unit) = Card(Modifier.width(172.dp).height(250.dp).clickable(onClick = onClick), shape = MaterialTheme.shapes.medium) {
    Box(Modifier.fillMaxSize()) { Poster(item, Modifier.fillMaxSize()); Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDE35212A)))).padding(14.dp)) { Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, color = Color.White); Text(collectionMeta(item, library), style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFDCE8)) } }
}

@Composable private fun SearchResult(item: VideoCollection, library: FamilyVideoLibrary, onClick: (String) -> Unit) = Row(Modifier.fillMaxWidth().clickable { onClick(item.id) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Poster(item, Modifier.width(58.dp).height(78.dp).clip(MaterialTheme.shapes.small)); Column(Modifier.padding(start = 12.dp).weight(1f)) { Text(item.name, fontWeight = FontWeight.SemiBold); Text(collectionMeta(item, library), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) } }

@Composable
fun Poster(item: VideoCollection, modifier: Modifier = Modifier) {
    if (!item.coverUrl.isNullOrBlank()) AsyncImage(model = item.coverUrl, contentDescription = "${item.name} 封面", contentScale = ContentScale.Crop, modifier = modifier)
    else Box(modifier.background(Brush.linearGradient(listOf(Color(0xFFFFC9D8), Color(0xFFC9D4FF)))), contentAlignment = Alignment.Center) { Text(if (item.mediaType == "movie") "🎬\n电影" else "🌈\n剧集", color = Color(0xFF4A2540), style = MaterialTheme.typography.titleLarge) }
}

private fun collectionMeta(item: VideoCollection, library: FamilyVideoLibrary): String {
    val childCount = library.childrenFor(item.id).size; val videoCount = library.mediaFor(item.id).size; val kind = if (item.mediaType == "movie") "电影" else "剧集"
    return if (childCount > 0) "$kind · $childCount 个子剧集" else "$kind · $videoCount 个视频"
}
