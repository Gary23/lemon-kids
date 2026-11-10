package com.lemonkids.kidtask.feature.local

import android.content.Intent
import android.os.Process
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lemonkids.shared.di.SupabaseEnvironment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun LocalServerScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf(SupabaseEnvironment.url.removePrefix("http://")) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("柠檬任务 · 本地服务地址", style = MaterialTheme.typography.titleLarge)
        Text("填写电脑的 IP:端口，例如 192.168.1.10:54421。手机与电脑需处于可互访网络。")
        Text("更换地址将清除此测试包的登录与绑定信息，需要使用对应本地家长端生成的任务绑定码重新登录。")
        OutlinedTextField(
            value = address,
            onValueChange = { address = it; error = null },
            label = { Text("IP:端口") },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(enabled = !busy, onClick = {
            val url = parseLocalServerAddress(address)
            if (url == null) {
                error = "请输入私有局域网或 127.0.0.1 的 IPv4 地址及有效端口"
                return@Button
            }
            busy = true
            scope.launch {
                try {
                    if (!withContext(Dispatchers.IO) { checkLocalHealth(url) }) {
                        error = "无法连接本地服务，请检查电脑 IP、54421 端口及网络后重试"
                        busy = false
                    } else if (url == SupabaseEnvironment.url) {
                        onContinue()
                    } else {
                        context.startActivity(Intent(context, LocalRestartActivity::class.java).apply {
                            putExtra(LocalRestartActivity.EXTRA_OLD_PID, Process.myPid())
                            putExtra(LocalRestartActivity.EXTRA_TARGET_URL, url)
                        })
                        // The foreground helper clears persisted state only after this process exits.
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    error = "切换服务失败，请重试"
                    busy = false
                }
            }
        }) {
            if (busy) CircularProgressIndicator() else Text("连接并继续")
        }
    }
}

private fun checkLocalHealth(url: String): Boolean = try {
    val connection = URL("$url/auth/v1/health").openConnection() as HttpURLConnection
    connection.connectTimeout = 2500
    connection.readTimeout = 2500
    connection.instanceFollowRedirects = false
    try {
        connection.requestMethod = "GET"
        connection.responseCode in 200..299
    } finally {
        connection.disconnect()
    }
} catch (_: Exception) {
    false
}
