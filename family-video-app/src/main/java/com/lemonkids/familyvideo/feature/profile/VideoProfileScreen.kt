package com.lemonkids.familyvideo.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lemonkids.shared.ui.auth.AuthViewModel

@Composable
fun VideoProfileScreen(onCategories: () -> Unit, onSignedOut: () -> Unit, authViewModel: AuthViewModel) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("我的", style = MaterialTheme.typography.headlineLarge)
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) {
            Text("123 云盘同步", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.padding(4.dp)); Text("尚未连接。OAuth 授权服务配置完成后，可在这里安全连接云盘。")
            Spacer(Modifier.padding(8.dp)); Button(onClick = { }) { Text("连接 123 云盘") }
        } }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) {
            Text("同步目录", style = MaterialTheme.typography.titleLarge)
            Text("选择一个根目录；它的第一层子文件夹会成为剧集或电影。")
            OutlinedButton(onClick = { }) { Text("选择同步目录") }
            Button(onClick = { }) { Text("立即同步") }
        } }
        OutlinedButton(onClick = onCategories, modifier = Modifier.fillMaxWidth()) { Text("分类管理") }
        OutlinedButton(onClick = { authViewModel.signOut(); onSignedOut() }, modifier = Modifier.fillMaxWidth()) { Text("退出登录") }
    }
}

@Composable
fun VideoCategoryScreen(onBack: () -> Unit) = Column(Modifier.fillMaxSize().padding(20.dp)) {
    Text("‹ 返回", color = MaterialTheme.colorScheme.primary)
    Text("分类管理", style = MaterialTheme.typography.headlineLarge)
    Text("分类仅保存于 App 数据库，不会移动或更名云盘文件。同步完成后，可在此管理未分类、添加、改名和排序。", modifier = Modifier.padding(top = 12.dp))
}
