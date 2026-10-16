package com.lemonkids.kidliteracy.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.lemonkids.kidliteracy.ui.theme.LemonLiteracyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1280dp-h800dp-land")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StudyDialogFrameTest {
    @get:Rule val compose = createComposeRule()

    @Test fun scrollingAwayAndBackKeepsCharacterAndWordSessions() {
        val created = mutableMapOf<String, Int>()
        val disposed = mutableMapOf<String, Int>()
        compose.setContent {
            LemonLiteracyTheme {
                StudyDialogFrame(character = "花", onDismiss = {}, footer = { Text("通过") }) {
                    key("character") { ResultRow("character", created, disposed) }
                    Spacer(Modifier.height(650.dp))
                    key("word") { ResultRow("word", created, disposed) }
                    Spacer(Modifier.height(650.dp))
                    Box(Modifier.fillMaxWidth().height(80.dp).testTag("list-end")) { Text("列表末尾") }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(mapOf("character" to 1, "word" to 1), created)

        compose.onNodeWithTag("list-end").performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithTag("character-row").performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithText("character：重试").assertExists()
        compose.onNodeWithTag("word-row").performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithText("word：重试").assertExists()
        compose.onNodeWithTag("list-end").assertExists()
        assertEquals("滚动不得重新创建评测会话", mapOf("character" to 1, "word" to 1), created)
        assertEquals("滚动不得释放评测会话", emptyMap<String, Int>(), disposed)
    }

    @Composable
    private fun ResultRow(
        id: String,
        created: MutableMap<String, Int>,
        disposed: MutableMap<String, Int>
    ) {
        var result by remember {
            created[id] = (created[id] ?: 0) + 1
            mutableStateOf("重试")
        }
        DisposableEffect(id) {
            onDispose { disposed[id] = (disposed[id] ?: 0) + 1 }
        }
        Box(Modifier.fillMaxWidth().height(80.dp).testTag("$id-row")) {
            Text("$id：$result")
        }
    }
}
