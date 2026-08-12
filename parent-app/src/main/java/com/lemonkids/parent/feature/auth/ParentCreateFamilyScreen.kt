package com.lemonkids.parent.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lemonkids.shared.ui.auth.AuthViewModel

@Composable
fun ParentCreateFamilyScreen(
    onComplete: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.needsFamilySetup) {
        if (!uiState.needsFamilySetup && uiState.isLoggedIn) {
            onComplete()
        }
    }

    val showSuccess = uiState.familyInviteCode != null && !uiState.isLoading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))
        Text(text = "\uD83C\uDFE0", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "创建你的家庭",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        if (showSuccess) {
            FamilyCreatedView(
                inviteCode = uiState.familyInviteCode ?: "",
                childCredentials = uiState.childCredentials,
                isLoading = uiState.isLoading,
                onCreateChild = { viewModel.createChildAccount(it) },
                onComplete = onComplete
            )
        } else if (!uiState.isLoading) {
            CreateFamilyForm(viewModel, uiState)
        }

        if (uiState.isLoading) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(Modifier.size(36.dp))
        }
    }
}

@Composable
private fun FamilyCreatedView(
    inviteCode: String,
    childCredentials: com.lemonkids.shared.repository.ChildCredentials?,
    isLoading: Boolean,
    onCreateChild: (String) -> Unit,
    onComplete: () -> Unit
) {
    var childName by remember { mutableStateOf("") }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "家庭创建成功！",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text("邀请码", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer {
            Text(
                text = inviteCode,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (childCredentials == null) {
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text(
                text = "为孩子创建登录账号",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = childName,
                onValueChange = { childName = it },
                label = { Text("孩子的名字") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onCreateChild(childName.trim()) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = childName.isNotBlank() && !isLoading
            ) {
                Text("生成孩子账号", fontSize = 16.sp)
            }
        } else {
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                text = "孩子登录信息",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "账号",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SelectionContainer {
                        Text(
                            text = childCredentials.email,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "密码",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SelectionContainer {
                        Text(
                            text = childCredentials.password,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "在孩子平板的 App 中输入以上信息登录",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("进入首页", fontSize = 16.sp)
        }
    }
}

@Composable
private fun CreateFamilyForm(viewModel: AuthViewModel, uiState: com.lemonkids.shared.ui.auth.AuthUiState) {
    var familyName by remember { mutableStateOf("") }

    Spacer(Modifier.height(16.dp))
    Text(
        text = "给家庭起个名字，然后分享邀请码\n让孩子的 App 加入进来",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(24.dp))

    if (uiState.errorMessage != null) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(uiState.errorMessage!!, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                TextButton(onClick = { viewModel.clearError() }) { Text("关闭") }
            }
        }
    }

    OutlinedTextField(
        value = familyName,
        onValueChange = { familyName = it },
        label = { Text("家庭名称") },
        placeholder = { Text("例：快乐一家") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
    )

    Spacer(modifier = Modifier.height(28.dp))
    Button(
        onClick = { viewModel.createFamily(familyName.trim()) {} },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        enabled = familyName.isNotBlank()
    ) {
        Text("创建家庭", fontSize = 16.sp)
    }
}
