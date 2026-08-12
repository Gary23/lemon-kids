package com.lemonkids.kidliteracy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.lemonkids.kidliteracy.ui.theme.LemonLiteracyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LemonLiteracyTheme { LemonLiteracyApp() } }
    }
}
