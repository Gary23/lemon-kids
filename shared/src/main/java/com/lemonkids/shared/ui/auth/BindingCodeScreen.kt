package com.lemonkids.shared.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun BindingCodeScreen(
    type: String,           // "task" 或 "monitor"
    deviceId: String,       // ANDROID_ID
    onSuccess: () -> Unit,
    title: String? = null,
    subtitle: String? = null,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by authViewModel.uiState.collectAsState()
    var code by remember { mutableStateOf("") }

    val isTaskType = type == "task"
    val displayTitle = title ?: if (isTaskType) "输入任务绑定码" else "输入监控绑定码"
    val displaySubtitle = subtitle ?: if (isTaskType) "请让家长在家长端生成任务绑定码" else "请让家长在家长端生成监控绑定码"

    LaunchedEffect(uiState.bindingCodeStatus) {
        if (uiState.bindingCodeStatus == "success") {
            onSuccess()
        }
    }

    if (uiState.bindingCodeStatus == "already_bound" && !isTaskType) {
        AlertDialog(
            onDismissRequest = { authViewModel.clearBindingCodeStatus() },
            title = { Text("设备绑定提示") },
            text = { Text("此监控码已绑定其他设备，是否解绑原设备并绑定当前设备？") },
            confirmButton = {
                TextButton(onClick = {
                    authViewModel.forceRebindBindingCode(code, deviceId, type)
                }) { Text("解绑并绑定", color = Color(0xFF4CAF50)) }
            },
            dismissButton = {
                TextButton(onClick = { authViewModel.clearBindingCodeStatus() }) { Text("取消") }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "\uD83C\uDF4B", fontSize = 48.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = displayTitle,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = displaySubtitle,
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = code,
                onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) code = it },
                placeholder = { Text("请输入6位绑定码") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = !uiState.isLoading
            )

            if (uiState.errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = uiState.errorMessage ?: "",
                    color = Color(0xFFE53935),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { authViewModel.enterBindingCode(code, deviceId, type) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                enabled = code.length == 6 && !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("确认绑定", fontSize = 16.sp, color = Color.White)
                }
            }
        }
    }
}
