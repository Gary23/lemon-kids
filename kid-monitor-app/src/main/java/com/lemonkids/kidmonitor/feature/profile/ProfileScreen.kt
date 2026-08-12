package com.lemonkids.kidmonitor.feature.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.lemonkids.shared.ui.auth.AuthViewModel

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showEditDialog by remember { mutableStateOf(false) }
    var showSwitchDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                val bytes = stream.readBytes()
                val ext = context.contentResolver.getType(it)?.split("/")?.lastOrNull() ?: "jpg"
                viewModel.uploadAvatar(bytes, "avatar_${System.currentTimeMillis()}.$ext")
            }
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // 每60秒自动刷新使用数据
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000L)
            viewModel.refreshUsage()
        }
    }

    if (showSwitchDialog) {
        AlertDialog(
            onDismissRequest = { showSwitchDialog = false },
            title = { Text("切换账号") },
            text = { Text("确定要切换账号吗？需要重新输入绑定码。") },
            confirmButton = {
                TextButton(onClick = {
                    showSwitchDialog = false
                    authViewModel.signOut()
                }) { Text("确定", color = Color(0xFFE53935)) }
            },
            dismissButton = {
                TextButton(onClick = { showSwitchDialog = false }) { Text("取消") }
            }
        )
    }

    if (showEditDialog) {
        val inputName = remember { mutableStateOf(uiState.userName.ifEmpty { "" }) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("修改名字") },
            text = {
                OutlinedTextField(
                    value = inputName.value,
                    onValueChange = { inputName.value = it },
                    singleLine = true,
                    label = { Text("昵称") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showEditDialog = false
                    viewModel.updateName(inputName.value.trim())
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("取消") }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .clickable { imagePickerLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (uiState.avatarUrl != null) {
                    AsyncImage(
                        model = uiState.avatarUrl,
                        contentDescription = "头像",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color(0xFFE0E0E0)),
                        contentAlignment = Alignment.Center
                    ) { Text("\uD83D\uDC64", fontSize = 36.sp) }
                }
                if (uiState.isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(80.dp).background(Color.Black.copy(alpha = 0.3f), CircleShape),
                        color = Color.White
                    )
                }
            }

            Text(
                "\uD83D\uDC66 ${uiState.userName}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { showEditDialog = true }
            )
            Text("⭐ ${uiState.totalPoints} 积分", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "点击头像换头像，点击名字换昵称",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (!uiState.isUsageLoading && uiState.appLimits.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("\uD83D\uDD12 使用限制", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        uiState.appLimits.forEach { limit ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(limit.appName, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    val parts = mutableListOf<String>()
                                    when {
                                        limit.dailyLimitMinutes == 0 -> parts.add("禁止使用")
                                        limit.dailyLimitMinutes == 999 -> parts.add("不限时")
                                        else -> parts.add("${limit.dailyLimitMinutes}分钟/天")
                                    }
                                    if (limit.singleSessionMinutes > 0) parts.add("单次${limit.singleSessionMinutes}分钟")
                                    if (limit.cooldownMinutes > 0) parts.add("间隔${limit.cooldownMinutes}分钟")
                                    Text(
                                        parts.joinToString(" · "),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("\uD83D\uDCCA 今日平板使用", fontWeight = FontWeight.Bold)
                    if (uiState.isUsageLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    } else {
                        val usageText = if (uiState.dailyLimitMinutes > 0) {
                            "${formatUsageMinutes(uiState.todayUsageMinutes)} / ${formatUsageMinutes(uiState.dailyLimitMinutes.toLong())}"
                        } else {
                            formatUsageMinutes(uiState.todayUsageMinutes)
                        }
                        Text(usageText)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { showSwitchDialog = true },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
            ) {
                Text("切换账号", fontSize = 16.sp, color = Color.White)
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

private fun formatUsageMinutes(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}小时${minutes}分" else "${minutes}分钟"
}
