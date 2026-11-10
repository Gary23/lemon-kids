package com.lemonkids.kidtask

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lemonkids.kidtask.feature.local.LocalServerScreen
import com.lemonkids.shared.di.SupabaseEnvironment
import com.lemonkids.kidtask.navigation.KidTaskNavGraph
import com.lemonkids.kidtask.ui.theme.KidTaskTheme
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
            KidTaskTheme {
                if (SupabaseEnvironment.isLocal && showLocalServerSettings) {
                    LocalServerScreen(onContinue = { showLocalServerSettings = false })
                } else {
                    KidTaskNavGraph(onLocalServerSettingsClick = { showLocalServerSettings = true })
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
