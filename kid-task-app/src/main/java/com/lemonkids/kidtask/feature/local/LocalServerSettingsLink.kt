package com.lemonkids.kidtask.feature.local

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.lemonkids.shared.di.SupabaseEnvironment

@Composable
internal fun LocalServerSettingsLink(onClick: () -> Unit) {
    if (SupabaseEnvironment.isLocal) {
        TextButton(onClick = onClick) {
            Text("修改本地服务地址 · ${SupabaseEnvironment.url.removePrefix("http://")}")
        }
    }
}
