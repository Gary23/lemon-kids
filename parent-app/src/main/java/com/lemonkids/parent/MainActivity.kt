package com.lemonkids.parent

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lemonkids.parent.feature.local.LocalServerScreen
import com.lemonkids.parent.navigation.ParentNavGraph
import com.lemonkids.parent.ui.theme.ParentTheme
import com.lemonkids.shared.di.SupabaseEnvironment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var showLocalServerSettings by mutableStateOf(true)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        setContent {
            ParentTheme {
                if (SupabaseEnvironment.isLocal && showLocalServerSettings) {
                    LocalServerScreen(onContinue = { showLocalServerSettings = false })
                } else {
                    ParentNavGraph(onLocalServerSettingsClick = {
                        showLocalServerSettings = true
                    })
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
