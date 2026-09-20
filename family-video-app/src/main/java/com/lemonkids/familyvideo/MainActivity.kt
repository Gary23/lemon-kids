package com.lemonkids.familyvideo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.lemonkids.familyvideo.navigation.FamilyVideoNavGraph
import com.lemonkids.familyvideo.ui.theme.FamilyVideoTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FamilyVideoTheme { FamilyVideoNavGraph() } }
    }
}
