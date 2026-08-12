package com.lemonkids.kidtask

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.lemonkids.kidtask.navigation.KidTaskNavGraph
import com.lemonkids.kidtask.ui.theme.KidTaskTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KidTaskTheme {
                KidTaskNavGraph()
            }
        }
    }
}
