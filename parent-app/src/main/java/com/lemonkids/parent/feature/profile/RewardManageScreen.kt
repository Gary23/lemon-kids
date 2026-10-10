package com.lemonkids.parent.feature.profile

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lemonkids.shared.model.Reward
import coil.compose.AsyncImage
import androidx.compose.foundation.shape.RoundedCornerShape

private val EmptyRewardImageColor = Color(0xFFF2F2F2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardManageScreen(
    onBack: () -> Unit,
    viewModel: RewardManageViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Reward?>(null) }
    var confirmStop by remember { mutableStateOf<Reward?>(null) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("奖励管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(enabled = !state.isBusy, onClick = viewModel::refresh) { Text("刷新") }
                    TextButton(
                        enabled = state.familyId != null && !state.isBusy,
                        onClick = { viewModel.beginEdit(); editing = null; editorOpen = true }
                    ) { Text("＋ 新增") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
            )

            Text(
                "全家共用的待兑换奖励。孩子兑换后的使用和取消在任务端处理。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            state.errorMessage?.let { error ->
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.familyId == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = viewModel::refresh) { Text("刷新家庭信息") }
                }
                state.errorMessage?.startsWith("加载奖励失败") == true && state.rewards.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = viewModel::refresh) { Text("重试加载奖励") }
                    }
                state.rewards.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有奖励，点击右上角新增", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.rewards, key = { it.id }) { reward ->
                        RewardRow(
                            reward = reward,
                            imageUrl = reward.imagePath?.let(state.imageUrls::get),
                            imageError = reward.imagePath?.let(state.imageErrors::contains) == true,
                            onRetryImage = { viewModel.retryImage(reward) },
                            busy = state.isBusy,
                            onEdit = { viewModel.beginEdit(); editing = reward; editorOpen = true },
                            onToggle = {
                                if (reward.isActive) confirmStop = reward
                                else viewModel.setActive(reward, true)
                            }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    if (editorOpen) {
        RewardEditorDialog(
            editing = editing,
            busy = state.isSaving || state.isPreparingImage,
            error = state.errorMessage,
            selectedImageBytes = state.selectedImageBytes,
            removeImage = state.removeImage,
            isPreparingImage = state.isPreparingImage,
            imageUrl = editing?.imagePath?.let(state.imageUrls::get),
            imageError = editing?.imagePath?.let(state.imageErrors::contains) == true,
            onRetryImage = { editing?.let(viewModel::retryImage) },
            onSelectImage = viewModel::selectImage,
            onRemoveImage = viewModel::removeImage,
            onDismiss = { if (!state.isBusy) { editorOpen = false; viewModel.clearError() } },
            onSave = { draft ->
                viewModel.save(draft, editing) { editorOpen = false; editing = null }
            }
        )
    }

    confirmStop?.let { reward ->
        AlertDialog(
            onDismissRequest = { if (!state.isBusy) confirmStop = null },
            title = { Text("停用奖励") },
            text = { Text("停用「${reward.title}」后，孩子不能再兑换。已经兑换的奖励仍然有效。") },
            confirmButton = {
                TextButton(enabled = !state.isBusy, onClick = {
                    confirmStop = null
                    viewModel.setActive(reward, false)
                }) { Text("停用") }
            },
            dismissButton = {
                TextButton(enabled = !state.isBusy, onClick = { confirmStop = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun RewardRow(
    reward: Reward, imageUrl: String?, imageError: Boolean, onRetryImage: () -> Unit,
    busy: Boolean, onEdit: () -> Unit, onToggle: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RewardImage(reward, imageUrl, imageError, onRetryImage)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(reward.title, fontWeight = FontWeight.SemiBold)
                    Text(
                        "⭐ ${reward.cost} · ${if (reward.repeatable) "常规奖励" else "一次性奖励"} · ${if (reward.isActive) "已启用" else "已停用"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (reward.isFeatured) Text("大心愿", color = MaterialTheme.colorScheme.primary)
            }
            reward.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(enabled = !busy, onClick = onEdit) { Text("编辑") }
                TextButton(enabled = !busy, onClick = onToggle) {
                    Text(if (reward.isActive) "停用" else "启用")
                }
            }
        }
    }
}

@Composable
private fun RewardImage(reward: Reward, imageUrl: String?, imageError: Boolean, onRetry: () -> Unit) {
    var loadFailed by remember(imageUrl) { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(EmptyRewardImageColor)) {
            if (reward.imagePath != null && !imageError && !loadFailed && imageUrl != null) {
                AsyncImage(
                    model = imageUrl, contentDescription = "${reward.title}的奖励图片",
                    contentScale = ContentScale.Crop,
                    onError = { loadFailed = true },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        if (reward.imagePath != null && (imageError || loadFailed)) {
            TextButton(onClick = onRetry) { Text("图片重试") }
        }
    }
}

@Composable
private fun RewardEditorDialog(
    editing: Reward?,
    busy: Boolean,
    error: String?,
    selectedImageBytes: ByteArray?,
    removeImage: Boolean,
    isPreparingImage: Boolean,
    imageUrl: String?,
    imageError: Boolean,
    onRetryImage: () -> Unit,
    onSelectImage: (Uri) -> Unit,
    onRemoveImage: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (RewardDraft) -> Unit
) {
    var title by remember(editing?.id) { mutableStateOf(editing?.title.orEmpty()) }
    var cost by remember(editing?.id) { mutableStateOf(editing?.cost?.toString().orEmpty()) }
    var repeatable by remember(editing?.id) { mutableStateOf(editing?.repeatable ?: true) }
    var description by remember(editing?.id) { mutableStateOf(editing?.description.orEmpty()) }
    var featured by remember(editing?.id) { mutableStateOf(editing?.isFeatured ?: false) }
    var imageLoadFailed by remember(imageUrl) { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(onSelectImage)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "新增奖励" else "编辑奖励") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, label = { Text("奖励名称") },
                    singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = cost, onValueChange = { cost = it }, label = { Text("积分价格") },
                    singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("奖励类型", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !repeatable, onClick = { repeatable = false }, enabled = !busy,
                        label = { Text("一次性") })
                    FilterChip(selected = repeatable, onClick = { repeatable = true }, enabled = !busy,
                        label = { Text("常规") })
                }
                Text(
                    if (repeatable) "常规奖励可以多次兑换" else "一次性奖励全家只能兑换一次；未使用前取消会恢复名额",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("说明（可选）") }, enabled = !busy, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("奖励图片（可选）", style = MaterialTheme.typography.labelLarge)
                val preview = remember(selectedImageBytes) {
                    selectedImageBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                }
                Box(Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)).background(EmptyRewardImageColor)) {
                    if (preview != null) {
                        Image(
                            bitmap = preview.asImageBitmap(), contentDescription = "待上传的奖励图片",
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                        )
                    } else if (!removeImage && !imageError && imageUrl != null && !imageLoadFailed) {
                        AsyncImage(
                            model = imageUrl, contentDescription = "当前奖励图片",
                            contentScale = ContentScale.Crop,
                            onError = { imageLoadFailed = true }, modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                if (preview == null && !removeImage && editing?.imagePath != null && (imageError || imageLoadFailed)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("当前图片读取失败")
                        TextButton(onClick = onRetryImage) { Text("重试") }
                    }
                }
                Row {
                    TextButton(enabled = !busy && !isPreparingImage, onClick = { imagePicker.launch("image/*") }) {
                        Text(if (preview != null || (!removeImage && editing?.imagePath != null)) "更换图片" else "选择图片")
                    }
                    if (preview != null || (!removeImage && editing?.imagePath != null)) {
                        TextButton(enabled = !busy && !isPreparingImage, onClick = onRemoveImage) { Text("移除图片") }
                    }
                }
                if (isPreparingImage) Text("正在处理图片…", style = MaterialTheme.typography.bodySmall)
                Text("支持静态 JPEG、PNG；上传时转为 JPEG，最大 5 MB", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = featured, onCheckedChange = { featured = it }, enabled = !busy)
                    Text("作为大心愿展示")
                }
                Text("同一家庭最多一个启用的大心愿", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && !isPreparingImage, onClick = {
                onSave(RewardDraft(title, cost, repeatable, description, featured))
            }) {
                if (busy) CircularProgressIndicator(Modifier.height(18.dp)) else Text("保存")
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") } }
    )
}
