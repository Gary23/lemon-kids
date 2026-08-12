package com.lemonkids.kidtask.feature.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.lemonkids.kidtask.ui.theme.Coral
import com.lemonkids.kidtask.ui.theme.Cream
import com.lemonkids.kidtask.ui.theme.InkBrown
import com.lemonkids.kidtask.ui.theme.Lavender
import com.lemonkids.kidtask.ui.theme.LavenderSoft
import com.lemonkids.kidtask.ui.theme.MutedGray
import com.lemonkids.kidtask.ui.theme.Pink
import com.lemonkids.kidtask.ui.theme.PinkSoft
import com.lemonkids.kidtask.ui.theme.Sunny
import com.lemonkids.shared.ui.auth.AuthViewModel

@Composable
fun ProfileScreen(
    onPlanClick: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    var showEditDialog by remember { mutableStateOf(false) }
    var showSwitchDialog by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
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
            viewModel.clearError()
        }
    }

    // 切换账号确认弹窗
    if (showSwitchDialog) {
        AlertDialog(
            onDismissRequest = { showSwitchDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = Color.White,
            title = { Text("切换账号", fontWeight = FontWeight.ExtraBold) },
            text = { Text("确定要切换账号吗？需要重新输入绑定码。") },
            confirmButton = {
                Button(
                    onClick = {
                        showSwitchDialog = false
                        authViewModel.signOut()
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Coral)
                ) {
                    Text("确定", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSwitchDialog = false }) {
                    Text("取消", color = MutedGray)
                }
            }
        )
    }

    // 修改昵称弹窗
    if (showEditDialog) {
        val inputName = remember { mutableStateOf(uiState.userName.ifEmpty { "" }) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = Color.White,
            title = { Text("修改名字", fontWeight = FontWeight.ExtraBold) },
            text = {
                OutlinedTextField(
                    value = inputName.value,
                    onValueChange = { inputName.value = it },
                    singleLine = true,
                    label = { Text("昵称") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEditDialog = false
                        viewModel.updateName(inputName.value.trim())
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Pink)
                ) {
                    Text("确定", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("取消", color = MutedGray)
                }
            }
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Cream) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部个人信息区
            ProfileHeader(
                avatarUrl = uiState.avatarUrl,
                userName = uiState.userName,
                totalPoints = uiState.totalPoints,
                isUploading = uiState.isUploading,
                onAvatarClick = { imagePickerLauncher.launch("image/*") },
                onNameClick = { showEditDialog = true }
            )

            // 功能入口列表
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(20.dp))

                // 计划入口
                EntryCard(
                    iconBg = LavenderSoft.copy(alpha = 0.5f),
                    iconColor = Lavender,
                    icon = Icons.Filled.CalendarMonth,
                    title = "计划",
                    desc = "查看未来日期的任务安排",
                    onClick = onPlanClick
                )
                Spacer(Modifier.height(12.dp))

                // 使用限制入口
                if (uiState.appLimits.isNotEmpty()) {
                    EntryCard(
                        iconBg = Coral.copy(alpha = 0.15f),
                        iconColor = Coral,
                        icon = Icons.Filled.Lock,
                        title = "使用限制",
                        desc = uiState.appLimits.joinToString(" · ") { "${it.appName} ${it.dailyLimitMinutes}分钟/天" },
                        onClick = {}
                    )
                    Spacer(Modifier.height(12.dp))
                }

                Spacer(Modifier.height(20.dp))

                // 切换账号按钮
                Button(
                    onClick = { showSwitchDialog = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Coral)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("切换账号", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ==================== 顶部个人信息 Header ====================

@Composable
private fun ProfileHeader(
    avatarUrl: String?,
    userName: String,
    totalPoints: Int,
    isUploading: Boolean,
    onAvatarClick: () -> Unit,
    onNameClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(
                Brush.linearGradient(listOf(PinkSoft, Color(0xFFFF9DBA), LavenderSoft))
            )
            .padding(top = 24.dp, bottom = 28.dp, start = 24.dp, end = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 头像
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(4.dp, Color.White, CircleShape)
                    .shadow(8.dp, CircleShape)
                    .clickable(onClick = onAvatarClick),
                contentAlignment = Alignment.Center
            ) {
                if (avatarUrl != null) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "头像",
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(PinkSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("👧", fontSize = 48.sp)
                    }
                }
                // 编辑铅笔图标
                Surface(
                    shape = CircleShape,
                    color = Pink,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(32.dp)
                        .shadow(4.dp, CircleShape)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Edit, contentDescription = "编辑头像", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                if (isUploading) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 昵称
            Row(
                modifier = Modifier.clickable(onClick = onNameClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    userName.ifEmpty { "小当家" },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.Edit, contentDescription = "改昵称", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            }

            Spacer(Modifier.height(8.dp))

            // 积分徽章
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.95f),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = Sunny, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "$totalPoints",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Pink
                    )
                    Spacer(Modifier.width(2.dp))
                    Text("积分", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MutedGray)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "点击头像换头像，点击名字改昵称",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

// ==================== 入口卡片 ====================

@Composable
private fun EntryCard(
    iconBg: Color,
    iconColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = iconBg,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = InkBrown)
                Text(desc, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MutedGray)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MutedGray, modifier = Modifier.size(24.dp))
        }
    }
}

// ==================== 切换账号 ====================



// ==================== 修改昵称弹窗 ====================