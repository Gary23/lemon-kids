package com.lemonkids.familyvideo.feature.profile

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.lemonkids.familyvideo.data.CloudDriveProvider
import com.lemonkids.familyvideo.data.CloudFolder
import com.lemonkids.familyvideo.data.FamilyVideoLibrary
import com.lemonkids.familyvideo.data.FamilyVideoRepository
import com.lemonkids.familyvideo.data.VideoCollection
import com.lemonkids.familyvideo.feature.home.Poster
import com.lemonkids.shared.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CollectionDraft(
    val id: String = "", val parentId: String? = null, val name: String = "", val type: String = "series",
    val coverUrl: String? = null, val folderId: String = "", val folderPath: String = "",
)

data class MediaManageUiState(
    val loading: Boolean = true, val working: Boolean = false, val familyId: String? = null,
    val library: FamilyVideoLibrary? = null, val connectionReady: Boolean = false,
    val editor: CollectionDraft? = null, val pickerOpen: Boolean = false,
    val folders: List<CloudFolder> = emptyList(), val path: String = "123 云盘", val folderId: String = "0",
    val nextFolderCursor: String? = null, val hasMoreFolders: Boolean = false,
    val deleteCandidate: VideoCollection? = null, val message: String? = null,
)

@HiltViewModel
class MediaLibraryManageViewModel @Inject constructor(
    private val repository: FamilyVideoRepository,
    private val drive: CloudDriveProvider,
    private val auth: AuthRepository,
) : ViewModel() {
    var state by mutableStateOf(MediaManageUiState()); private set
    private var folderCoverJob: Job? = null
    private var folderCoverSelectionId = 0L

    fun load(openCollectionId: String? = null, initialParentId: String? = null) = viewModelScope.launch {
        val familyId = auth.observeCurrentUser().firstOrNull()?.familyId ?: run { state = state.copy(loading = false, message = "当前账号还没有家庭") ; return@launch }
        state = state.copy(loading = true, message = null)
        val library = repository.loadLibrary(familyId).getOrElse { state = state.copy(loading = false, message = "读取媒体库失败：${it.message}"); return@launch }
        val connected = drive.connection().getOrNull()?.status == "connected"
        state = state.copy(loading = false, familyId = familyId, library = library, connectionReady = connected)
        when {
            openCollectionId != null -> library.collections.firstOrNull { it.id == openCollectionId }?.let(::openEditor)
            initialParentId != null -> openNew(initialParentId)
        }
    }

    fun openNew(parentId: String? = null) {
        invalidateFolderCoverLoad()
        state = state.copy(editor = CollectionDraft(parentId = parentId, type = "series"), message = null)
    }
    fun openEditor(collection: VideoCollection) = state.let {
        invalidateFolderCoverLoad()
        state = it.copy(editor = CollectionDraft(collection.id, collection.parentId, collection.name, collection.mediaType, collection.coverUrl, collection.driveFolderId, collection.driveFolderPath.orEmpty()), message = null)
    }
    fun editDraft(transform: (CollectionDraft) -> CollectionDraft) { state.editor?.let { state = state.copy(editor = transform(it)) } }
    fun closeEditor() { invalidateFolderCoverLoad(); state = state.copy(editor = null, pickerOpen = false) }

    fun openPicker() {
        if (!state.connectionReady) { state = state.copy(message = "请先在设置中连接 123 云盘"); return }
        browse("0", "123 云盘", true)
    }
    fun enter(folder: CloudFolder) = browse(folder.id, folder.breadcrumb, true)
    fun loadMoreFolders() {
        val cursor = state.nextFolderCursor ?: return
        browse(state.folderId, state.path, true, cursor = cursor, append = true)
    }
    fun refreshCurrentFolder() = browse(state.folderId, state.path, true, forceRefresh = true)
    fun chooseCurrentFolder() {
        if (state.folderId == "0") { state = state.copy(message = "请进入并选择一个具体目录"); return }
        val selectedFolderId = state.folderId
        val isNewCollection = state.editor?.id.isNullOrBlank()
        invalidateFolderCoverLoad()
        val selectionId = folderCoverSelectionId
        editDraft { draft ->
            draft.copy(
                folderId = selectedFolderId,
                folderPath = state.path,
                // 新建页的封面必须属于当前选择的目录，换目录时不可沿用旧预览。
                coverUrl = if (isNewCollection) null else draft.coverUrl,
            )
        }
        state = state.copy(pickerOpen = false)
        if (isNewCollection) loadFolderCover(selectedFolderId, selectionId)
    }
    fun closePicker() { state = state.copy(pickerOpen = false) }
    private fun browse(folderId: String, path: String, show: Boolean, cursor: String = "0", append: Boolean = false, forceRefresh: Boolean = false) = viewModelScope.launch {
        state = state.copy(working = true, pickerOpen = show, message = null)
        drive.browse(folderId, path, cursor, forceRefresh).fold(
            { page ->
                val folders = if (append) (state.folders + page.folders).distinctBy { it.id } else page.folders
                state = state.copy(
                    working = false,
                    folders = folders,
                    folderId = folderId,
                    path = path,
                    nextFolderCursor = page.nextCursor,
                    hasMoreFolders = page.hasMore,
                )
            },
            { state = state.copy(working = false, message = it.message ?: "读取云盘目录失败") },
        )
    }

    fun uploadCover(bytes: ByteArray) = viewModelScope.launch {
        invalidateFolderCoverLoad()
        val familyId = state.familyId ?: return@launch
        state = state.copy(working = true, message = null)
        repository.uploadCover(familyId, bytes).fold(
            { url -> editDraft { it.copy(coverUrl = url) }; state = state.copy(working = false) },
            { state = state.copy(working = false, message = "上传封面失败：${it.message}") },
        )
    }

    private fun loadFolderCover(folderId: String, selectionId: Long) {
        val familyId = state.familyId ?: return
        folderCoverJob = viewModelScope.launch {
            state = state.copy(working = true, message = null)
            val cover = drive.folderCover(folderId).getOrElse {
                if (isCurrentFolderCoverLoad(folderId, selectionId)) {
                    state = state.copy(working = false, message = it.message ?: "读取目录封面失败")
                }
                return@launch
            }
            if (!isCurrentFolderCoverLoad(folderId, selectionId)) return@launch
            val bytes = runCatching { downloadFolderCover(cover.url) }.getOrElse {
                if (isCurrentFolderCoverLoad(folderId, selectionId)) {
                    state = state.copy(working = false, message = "下载目录封面失败：${it.message}")
                }
                return@launch
            }
            if (!isCurrentFolderCoverLoad(folderId, selectionId)) return@launch
            repository.uploadCover(familyId, bytes).fold(
                { url ->
                    if (isCurrentFolderCoverLoad(folderId, selectionId)) {
                        editDraft { it.copy(coverUrl = url) }
                        state = state.copy(working = false, message = "已读取 ${cover.name} 作为封面")
                    }
                },
                {
                    if (isCurrentFolderCoverLoad(folderId, selectionId)) {
                        state = state.copy(working = false, message = "保存目录封面失败：${it.message}")
                    }
                },
            )
        }
    }

    private fun invalidateFolderCoverLoad() {
        folderCoverSelectionId += 1
        folderCoverJob?.cancel()
        folderCoverJob = null
    }

    private fun isCurrentFolderCoverLoad(folderId: String, selectionId: Long): Boolean {
        val draft = state.editor
        return selectionId == folderCoverSelectionId && draft?.id.isNullOrBlank() && draft?.folderId == folderId
    }

    fun save() = viewModelScope.launch {
        invalidateFolderCoverLoad()
        val draft = state.editor ?: return@launch
        val familyId = state.familyId ?: return@launch
        when {
            draft.name.trim().isEmpty() -> { state = state.copy(message = "请输入名称"); return@launch }
            draft.coverUrl.isNullOrBlank() -> { state = state.copy(message = "请先选择封面"); return@launch }
            draft.folderId.isBlank() -> { state = state.copy(message = "请选择云盘目录"); return@launch }
        }
        state = state.copy(working = true, message = null)
        val saved = repository.saveCollection(VideoCollection(draft.id, familyId, draft.parentId, draft.folderId, draft.folderPath, draft.name.trim(), draft.type, draft.coverUrl)).getOrElse {
            state = state.copy(working = false, message = "保存失败：${it.message}"); return@launch
        }
        // 新建或编辑后立即同步一次；后续每个条目仍可独立“刷新视频”。
        val syncMessage = drive.syncCollection(saved.id).fold(
            { "已保存并刷新 ${it.media} 个直接视频" },
            { "已保存；刷新视频失败：${it.message}。可稍后点“刷新”重试" },
        )
        reload("$syncMessage")
        state = state.copy(editor = null)
    }

    fun refreshCollection(collectionId: String) = viewModelScope.launch {
        state = state.copy(working = true, message = null)
        drive.syncCollection(collectionId).fold(
            { reload("已刷新 ${it.media} 个直接视频") },
            { state = state.copy(working = false, message = "刷新失败：${it.message}") },
        )
    }
    fun askDelete(collection: VideoCollection) { state = state.copy(deleteCandidate = collection) }
    fun deleteCollection() = viewModelScope.launch {
        val collection = state.deleteCandidate ?: return@launch
        state = state.copy(working = true, deleteCandidate = null)
        repository.deleteCollection(collection.id).fold({ reload("已删除“${collection.name}”") }, { state = state.copy(working = false, message = "删除失败：${it.message}") })
    }
    fun dismissDelete() { state = state.copy(deleteCandidate = null) }
    private suspend fun reload(message: String) {
        val familyId = state.familyId ?: return
        repository.loadLibrary(familyId).fold(
            { state = state.copy(working = false, library = it, message = message) },
            { state = state.copy(working = false, message = "$message；重新读取媒体库失败") },
        )
    }
}

@Composable
fun MediaLibraryManageScreen(onBack: () -> Unit, openCollectionId: String? = null, initialParentId: String? = null, viewModel: MediaLibraryManageViewModel = hiltViewModel()) {
    LaunchedEffect(openCollectionId, initialParentId) { viewModel.load(openCollectionId, initialParentId) }
    val state = viewModel.state
    if (state.editor != null) CollectionEditor(state, viewModel, onBack) else LibraryList(state, viewModel, onBack)
    if (state.pickerOpen) FolderPicker(state, viewModel)
    state.deleteCandidate?.let { item -> AlertDialog(onDismissRequest = viewModel::dismissDelete, title = { Text("删除媒体条目？") }, text = { Text("“${item.name}”及其所有子剧集和视频记录都会被删除，云盘目录和文件不会受影响。") }, confirmButton = { TextButton(onClick = viewModel::deleteCollection) { Text("删除") } }, dismissButton = { TextButton(onClick = viewModel::dismissDelete) { Text("取消") } }) }
}

@Composable
private fun LibraryList(state: MediaManageUiState, viewModel: MediaLibraryManageViewModel, onBack: () -> Unit) = Column(Modifier.fillMaxSize()) {
    Text("‹ 返回", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onBack).padding(20.dp, 18.dp, 20.dp, 0.dp))
    Row(Modifier.fillMaxWidth().padding(20.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("整理媒体库", style = MaterialTheme.typography.headlineMedium); Text("条目和云盘目录一一绑定", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }; FilledTonalButton(onClick = { viewModel.openNew() }) { androidx.compose.material3.Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("新建") } }
    if (state.loading) CircularProgressIndicator(Modifier.padding(20.dp))
    else LazyColumn(Modifier.fillMaxSize()) {
        val top = state.library?.topLevel().orEmpty()
        if (top.isEmpty()) item { Text("还没有剧集或电影。", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(top, key = { it.id }) { collection -> ManageRow(collection, state.library!!, viewModel) }
        item { state.message?.let { Text(it, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.primary) } }
    }
}

@Composable
private fun ManageRow(item: VideoCollection, library: FamilyVideoLibrary, viewModel: MediaLibraryManageViewModel) = Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Poster(item, Modifier.width(54.dp).height(72.dp))
        Column(Modifier.padding(start = 12.dp).weight(1f).clickable { viewModel.openEditor(item) }) { Text(item.name, fontWeight = FontWeight.SemiBold); Text("${if (item.mediaType == "movie") "电影" else "剧集"} · ${library.childrenFor(item.id).size} 个子剧集 · ${library.mediaFor(item.id).size} 个视频", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(item.driveFolderPath ?: "未记录路径", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        TextButton(onClick = { viewModel.refreshCollection(item.id) }, enabled = !stateWorking(viewModel)) { androidx.compose.material3.Icon(Icons.Filled.Refresh, "刷新") }
    }
}

private fun stateWorking(viewModel: MediaLibraryManageViewModel) = viewModel.state.working

@Composable
private fun CollectionEditor(state: MediaManageUiState, viewModel: MediaLibraryManageViewModel, onBack: () -> Unit) {
    val draft = state.editor ?: return
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { uploadSelectedCover(context, it, viewModel) } }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        item { Text("‹ 返回整理", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { viewModel.closeEditor(); onBack() }.padding(top = 18.dp, bottom = 14.dp)); Text(if (draft.id.isBlank()) "新建媒体条目" else "编辑媒体条目", style = MaterialTheme.typography.headlineMedium); Text("只配置名称、类型、封面和云盘目录。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
        item { OutlinedTextField(value = draft.name, onValueChange = { viewModel.editDraft { old -> old.copy(name = it) } }, label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) }
        item { Text("类型", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = draft.type == "series", onClick = { viewModel.editDraft { it.copy(type = "series") } }, label = { Text("剧集") }); FilterChip(selected = draft.type == "movie", onClick = { viewModel.editDraft { it.copy(type = "movie") } }, label = { Text("电影") }) } }
        if (draft.id.isBlank()) {
            item { CloudFolderField(draft, state, viewModel) }
            item {
                Text("封面", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                if (!draft.coverUrl.isNullOrBlank()) AsyncImage(model = draft.coverUrl, contentDescription = "封面预览", contentScale = ContentScale.Crop, modifier = Modifier.width(112.dp).height(150.dp))
                else Text("选择云盘目录后，自动读取其中的 folder.png、folder.jpg 或 folder.jpeg。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            item { Text("封面", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)); if (!draft.coverUrl.isNullOrBlank()) AsyncImage(model = draft.coverUrl, contentDescription = "封面预览", contentScale = ContentScale.Crop, modifier = Modifier.width(112.dp).height(150.dp)); OutlinedButton(onClick = { imagePicker.launch("image/*") }, enabled = !state.working, modifier = Modifier.padding(top = 8.dp)) { Text(if (draft.coverUrl.isNullOrBlank()) "选择封面" else "更换封面") } }
            item { CloudFolderField(draft, state, viewModel) }
        }
        item { if (state.working) Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp)); Text("正在处理…", Modifier.padding(start = 10.dp)) }; state.message?.let { Text(it, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.primary) }; Button(onClick = viewModel::save, enabled = !state.working, modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 28.dp)) { Text("保存并刷新视频") } }
    }
}

@Composable
private fun CloudFolderField(draft: CollectionDraft, state: MediaManageUiState, viewModel: MediaLibraryManageViewModel) {
    Text("云盘目录", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
    Text(draft.folderPath.ifBlank { "尚未选择" }, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    OutlinedButton(onClick = viewModel::openPicker, enabled = !state.working, modifier = Modifier.padding(top = 8.dp)) { Text("选择云盘目录") }
}

private fun uploadSelectedCover(context: Context, uri: Uri, viewModel: MediaLibraryManageViewModel) {
    runCatching { context.contentResolver.openInputStream(uri)?.use { input -> input.readBytes().also { require(it.size <= 8 * 1024 * 1024) { "封面图片不能超过 8MB" } } } ?: error("无法读取封面") }
        .onSuccess(viewModel::uploadCover)
        .onFailure { /* 读取失败时由后续保存校验阻止提交，避免把 URI 写入数据库。 */ }
}

private suspend fun downloadFolderCover(url: String): ByteArray = HttpClient().use { client ->
    client.get(url).body<ByteArray>().also { bytes ->
        require(bytes.isNotEmpty()) { "封面文件为空" }
        require(bytes.size <= 8 * 1024 * 1024) { "封面图片不能超过 8MB" }
    }
}

@Composable
private fun FolderPicker(state: MediaManageUiState, viewModel: MediaLibraryManageViewModel) = AlertDialog(
    onDismissRequest = viewModel::closePicker,
    title = { Text("选择云盘目录") },
    text = {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(state.path, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = viewModel::refreshCurrentFolder, enabled = !state.working) { Text("刷新目录") }
            }
            if (state.working) CircularProgressIndicator(Modifier.padding(vertical = 20.dp))
            else LazyColumn {
                if (state.folders.isEmpty()) item {
                    Text(
                        if (state.hasMoreFolders) "这一页没有目录，可继续加载。" else "当前目录没有可进入的子目录。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
                items(state.folders, key = { it.id }) { folder ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("📁 ${folder.name}", Modifier.weight(1f).clickable { viewModel.enter(folder) }.padding(vertical = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { viewModel.enter(folder) }) { Text("进入") }
                    }
                }
                if (state.hasMoreFolders) item {
                    OutlinedButton(onClick = viewModel::loadMoreFolders, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("加载更多目录") }
                }
            }
        }
    },
    confirmButton = { TextButton(onClick = viewModel::chooseCurrentFolder, enabled = state.folderId != "0" && !state.working) { Text("选择当前目录") } },
    dismissButton = { TextButton(onClick = viewModel::closePicker) { Text("取消") } },
)
