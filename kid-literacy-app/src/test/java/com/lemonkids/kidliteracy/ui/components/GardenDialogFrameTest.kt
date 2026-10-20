package com.lemonkids.kidliteracy.ui.components

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.lemonkids.kidliteracy.ui.theme.LemonLiteracyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w640dp-h400dp-land")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GardenDialogFrameTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longLargeFontFormCanReachFooterWhileCloseStaysVisible() {
        var open by mutableStateOf(true)
        var submitted = 0
        var closed = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                LemonLiteracyTheme {
                    if (open) GardenDialogFrame("智能添加识字", "字不可修改；词和句子可按需要修改", onDismiss = { closed++; open = false }) {
                        items(12) { index ->
                            GardenPanel {
                                Text("第 ${index + 1} 组")
                                OutlinedTextField("春天的花园里开满了花。", {}, label = { Text("句子") }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        item {
                            GardenActionButton(onClick = { submitted++ }, modifier = Modifier.fillMaxWidth()) { Text("确认添加") }
                        }
                    }
                }
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(13)
        compose.onNodeWithText("确认添加").assertIsDisplayed().performClick()
        assertEquals(1, submitted)
        compose.onNodeWithContentDescription("关闭").assertIsDisplayed().performClick()
        assertEquals(1, closed)
        compose.onNodeWithText("确认添加").assertDoesNotExist()
    }
}
