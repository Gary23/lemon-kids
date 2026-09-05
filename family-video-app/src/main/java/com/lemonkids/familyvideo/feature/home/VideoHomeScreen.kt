package com.lemonkids.familyvideo.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.familyvideo.data.FamilyVideoLibrary
import com.lemonkids.familyvideo.data.FamilyVideoRepository
import com.lemonkids.familyvideo.data.VideoCollection
import com.lemonkids.shared.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

data class VideoHomeUiState(val loading: Boolean = true, val library: FamilyVideoLibrary? = null, val error: String? = null)
@HiltViewModel class VideoHomeViewModel @Inject constructor(private val repository: FamilyVideoRepository, private val auth: AuthRepository) : ViewModel() {
    var state by mutableStateOf(VideoHomeUiState()); private set
    fun refresh() = viewModelScope.launch {
        val user = auth.observeCurrentUser().firstOrNull()
        val familyId = user?.familyId ?: run { state = VideoHomeUiState(loading = false, error = "此账号尚未创建家庭，请先在家长端完成家庭设置。"); return@launch }
        state = VideoHomeUiState(loading = true)
        repository.loadLibrary(familyId).fold({ state = VideoHomeUiState(loading = false, library = it) }, { state = VideoHomeUiState(loading = false, error = "媒体库暂不可用：${it.message ?: "请检查数据库迁移"}") })
    }
}

@Composable fun VideoHomeScreen(onCollectionClick: (String) -> Unit, onSyncClick: () -> Unit, viewModel: VideoHomeViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val state = viewModel.state
    when {
        state.loading -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) { CircularProgressIndicator(Modifier.padding(start = 180.dp)) }
        state.error != null -> EmptyLibrary(state.error, onSyncClick)
        else -> HomeContent(state.library!!, onCollectionClick, onSyncClick)
    }
}

@Composable private fun EmptyLibrary(message: String, onSyncClick: () -> Unit) = Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
    Text("动画小天地还没有内容", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(10.dp)); Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(20.dp)); Button(onSyncClick) { Text("去连接并同步 123 云盘") }
}

@Composable private fun HomeContent(library: FamilyVideoLibrary, onCollectionClick: (String) -> Unit, onSyncClick: () -> Unit) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().padding(top = 18.dp)) {
        Text("今天想看什么？", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(horizontal = 20.dp))
        Text("你的家庭动画小天地", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        val unfinished = library.playback.firstOrNull { !it.isCompleted && it.progressSeconds > 0 }?.let { record -> library.media.firstOrNull { it.id == record.mediaId } }
        if (unfinished != null) ContinueCard(unfinished.name, onClick = { onCollectionClick(unfinished.collectionId) })
        val grouped = library.categories.map { category -> category.name to library.collections.filter { it.categoryId == category.id } } + listOf("未分类" to library.collections.filter { it.categoryId == null })
        grouped.filter { it.second.isNotEmpty() }.forEach { (title, collections) ->
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 10.dp))
            Row(
                Modifier.horizontalScroll(scroll).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                collections.forEach { collection ->
                    CollectionCard(collection, library.mediaFor(collection.id).size) {
                        onCollectionClick(collection.id)
                    }
                }
            }
        }
        if (library.collections.isEmpty()) EmptyLibrary("同步根目录下的子文件夹会显示在这里。", onSyncClick)
    }
}
@Composable private fun ContinueCard(name: String, onClick: () -> Unit) = Card(Modifier.fillMaxWidth().padding(20.dp, 16.dp, 20.dp, 0.dp).clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(18.dp)) { Text("继续观看", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Text(name, style = MaterialTheme.typography.titleLarge); Text("从上次看到的地方继续", style = MaterialTheme.typography.bodyMedium) } }
@Composable private fun CollectionCard(item: VideoCollection, count: Int, onClick: () -> Unit) = Card(Modifier.width(156.dp).height(174.dp).clickable(onClick = onClick), shape = RoundedCornerShape(20.dp)) { Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFFD9E7), Color(0xFFE7DEFF)))).padding(14.dp), verticalArrangement = Arrangement.Bottom) { Text("✦", fontSize = 34.sp); Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold); Text("$count 集", style = MaterialTheme.typography.bodyMedium) } }
