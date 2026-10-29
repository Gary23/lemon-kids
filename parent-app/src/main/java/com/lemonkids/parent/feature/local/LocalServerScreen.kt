package com.lemonkids.parent.feature.local

import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import io.github.jan.supabase.gotrue.SettingsSessionManager
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
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("本地服务地址")
        Text("填写电脑当前的 IP:端口。手机与电脑需处于可互访网络；默认端口 54421。")
        OutlinedTextField(
            value = address,
            onValueChange = { address = it; error = null },
            label = { Text("IP:端口") },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        )
        error?.let { Text(it) }
        Button(
            enabled = !busy,
            onClick = {
                val url = parseLocalServerAddress(address)
                if (url == null) {
                    error = "请输入私有局域网或 127.0.0.1 的 IPv4 地址及有效端口"
                    return@Button
                }
                busy = true
                scope.launch {
                    val reachable = withContext(Dispatchers.IO) { checkLocalHealth(url) }
                    if (!reachable) {
                        error = "无法连接本地服务，请检查电脑 IP、端口及网络"
                        busy = false
                        return@launch
                    }
                    if (url == SupabaseEnvironment.url) {
                        onContinue()
                    } else {
                        try {
                            // Discard the old server's saved token before changing the URL.
                            SettingsSessionManager().deleteSession()
                            if (!SupabaseEnvironment.saveLocalUrl(url)) {
                                error = "保存地址失败，请重试"
                                busy = false
                                return@launch
                            }
                            restartApplication(context)
                        } catch (_: Exception) {
                            error = "切换服务失败，请重试"
                            busy = false
                        }
                    }
                }
            }
        ) {
            if (busy) CircularProgressIndicator() else Text("连接并继续")
        }
    }
}

private fun checkLocalHealth(url: String): Boolean = try {
    val connection = URL("$url/auth/v1/health").openConnection() as HttpURLConnection
    connection.connectTimeout = 2500
    connection.readTimeout = 2500
    try {
        connection.requestMethod = "GET"
        connection.responseCode in 200..299
    } finally {
        connection.disconnect()
    }
} catch (_: Exception) {
    false
}

private fun restartApplication(context: Context) {
    // The visible helper takes responsibility before the original process exits.
    context.startActivity(Intent(context, LocalRestartActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra(LocalRestartActivity.EXTRA_OLD_PID, Process.myPid())
    })
}
