package com.lemonkids.parent.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lemonkids.shared.repository.BindingCodeInfo
import com.lemonkids.shared.repository.ChildUserInfo
import com.lemonkids.shared.ui.auth.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyManageScreen(
    onBack: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by authViewModel.uiState.collectAsState()
    var selectedChild by remember { mutableStateOf<ChildUserInfo?>(null) }
    var showBindingCodeType by remember { mutableStateOf<String?>(null) }
    var newChildName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        authViewModel.refreshBindingCodes()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("家庭管理") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (uiState.errorMessage != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(Modifier.padding(12.dp)) {
                                Text(
                                    uiState.errorMessage!!,
                                    Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                TextButton(onClick = { authViewModel.clearError() }) {
                                    Text("关闭")
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    Text("孩子列表", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))

                    if (uiState.childUsers.isEmpty()) {
                        Text(
                            "还没有添加孩子",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        uiState.childUsers.forEach { child ->
                            // 筛选当前孩子的活跃绑定码
                            val childCodes = uiState.bindingCodes.filter { it.childUid == child.uid }
                            val taskCode = childCodes.find { it.type == "task" }
                            val monitorCode = childCodes.find { it.type == "monitor" }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                    Text(
                                        child.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        "⭐ ${child.totalPoints} 积分",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                selectedChild = child
                                                showBindingCodeType = "task"
                                                authViewModel.generateChildBindingCode(
                                                    child.uid, null, "task"
                                                )
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF4CAF50)
                                            )
                                        ) {
                                            Text("生成任务码", fontSize = 13.sp)
                                        }
                                        Button(
                                            onClick = {
                                                selectedChild = child
                                                // 认字应用与任务应用使用同一个儿童绑定码，保证登录的是同一儿童账号。
                                                showBindingCodeType = "literacy"
                                                authViewModel.generateChildBindingCode(
                                                    child.uid, null, "task"
                                                )
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF5C8DFF)
                                            )
                                        ) {
                                            Text("生成认字码", fontSize = 13.sp)
                                        }
                                        Button(
                                            onClick = {
                                                selectedChild = child
                                                showBindingCodeType = "monitor"
                                                authViewModel.generateChildBindingCode(
                                                    child.uid, null, "monitor"
                                                )
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF2196F3)
                                            )
                                        ) {
                                            Text("生成监控码", fontSize = 13.sp)
                                        }
                                    }

                                    // 显示已有的绑定码
                                    if (childCodes.isNotEmpty()) {
                                        Spacer(Modifier.height(8.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        Spacer(Modifier.height(8.dp))

                                        if (taskCode != null) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "任务 / 认字码: ",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                SelectionContainer {
                                                    Text(
                                                        text = taskCode.code,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF4CAF50)
                                                    )
                                                }
                                            }
                                            Text(
                                                "此码可在任务和认字应用的多个设备上使用",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }

                                        if (monitorCode != null) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "监控码: ",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                SelectionContainer {
                                                    Text(
                                                        text = monitorCode.code,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF2196F3)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))

                    Text("添加孩子", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "输入名字创建孩子账号",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = newChildName,
                        onValueChange = { newChildName = it },
                        label = { Text("孩子名字") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            authViewModel.createChildAccount(newChildName.trim())
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = newChildName.isNotBlank() && !uiState.isLoading
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("创建孩子", fontSize = 16.sp)
                        }
                    }

                    if (uiState.childCredentials != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "创建成功！请为孩子生成绑定码",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(40.dp)
                )
            }
        }
    }

    // 显示生成的绑定码
    val bindingCodeStatus = uiState.bindingCodeStatus
    if (bindingCodeStatus != null && bindingCodeStatus.startsWith("generated:")) {
        val code = bindingCodeStatus.removePrefix("generated:")
        val typeLabel = when (showBindingCodeType) {
            "task" -> "任务"
            "literacy" -> "认字应用"
            else -> "监控"
        }
        val childName = selectedChild?.name ?: ""
        BindingCodeDialog(
            code = code,
            typeLabel = typeLabel,
            childName = childName,
            onDismiss = {
                authViewModel.clearBindingCodeStatus()
                selectedChild = null
                showBindingCodeType = null
            }
        )
    }
}

@Composable
private fun BindingCodeDialog(
    code: String,
    typeLabel: String,
    childName: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (childName.isNotEmpty()) "$childName 的${typeLabel}绑定码"
                else "${typeLabel}绑定码"
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "请让孩子在 App 中输入此码",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                SelectionContainer {
                    Text(
                        text = code,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 10.sp,
                        color = Color(0xFF4CAF50)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "绑定码长期有效",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (typeLabel == "监控") {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "监控码只能绑定一个设备",
                        fontSize = 12.sp,
                        color = Color(0xFFE53935)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
