package com.lemonkids.kidliteracy.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lemonkids.kidliteracy.R
import com.lemonkids.kidliteracy.ui.theme.*

@Composable
internal fun GardenArt(@DrawableRes resource: Int, modifier: Modifier = Modifier) {
    Image(painterResource(resource), contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
}

@Composable
internal fun GardenPageScaffold(@DrawableRes scene: Int, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Image(painterResource(scene), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 1200.dp).fillMaxSize()) { content() }
        }
    }
}

@Composable
internal fun GardenPanel(modifier: Modifier = Modifier, tint: Color = Background, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(28.dp), color = tint.copy(alpha = .97f),
        border = BorderStroke(2.dp, Color.White), shadowElevation = 4.dp) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

internal val GardenPrimaryArrangement = object : Arrangement.Vertical {
    override val spacing: Dp = 16.dp
    override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
        gardenPrimaryPositions(totalSize, sizes, spacing.roundToPx()).copyInto(outPositions)
    }
}

@Composable
private fun GardenTitle(title: String, compact: Boolean) {
    val style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge
    Box {
        Text(title, style = style.copy(drawStyle = Stroke(width = 6f)), color = Color.White,
            fontWeight = FontWeight.ExtraBold, modifier = Modifier.clearAndSetSemantics {})
        Text(title, style = style, color = Ink, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
internal fun GardenPageHeader(title: String, subtitle: String, onBack: (() -> Unit)? = null,
    mascot: Int = R.drawable.garden_mascot_welcome, action: @Composable () -> Unit = {}) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        val compact = maxWidth < 680.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.2f
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onBack != null) Surface(shape = CircleShape, color = Background, shadowElevation = 3.dp) {
                    IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Ink) }
                }
                if (!compact) GardenArt(R.drawable.garden_logo_lemon, Modifier.size(56.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GardenTitle(title, compact)
                    Surface(shape = RoundedCornerShape(12.dp), color = Background.copy(alpha = .95f)) {
                        Text(subtitle, color = Color(0xFF687D9F), modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                }
                if (!compact) {
                    action()
                    GardenArt(mascot, Modifier.size(100.dp))
                }
            }
            if (compact) action()
        }
    }
}

/** 与原按钮回调兼容，使用原生渐变和阴影，不拉伸按钮位图。 */
@Composable
internal fun GardenActionButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = CircleShape, colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit) {
    val base = if (enabled) colors.containerColor else colors.disabledContainerColor
    Box(modifier.heightIn(min = 48.dp).shadow(if (enabled) 3.dp else 0.dp, shape).clip(shape)
        .background(Brush.verticalGradient(listOf(base.copy(alpha = base.alpha * .84f), base))), propagateMinConstraints = true) {
        Button(onClick, enabled = enabled, shape = shape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = colors.contentColor,
                disabledContainerColor = Color.Transparent, disabledContentColor = colors.disabledContentColor),
            contentPadding = contentPadding, content = content)
    }
}

@Composable
internal fun GardenCharacterTile(character: String, modifier: Modifier = Modifier, completed: Boolean = false,
    onClick: (() -> Unit)? = null) {
    Surface(modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(20.dp), color = if (completed) LeafLight else Background,
        border = BorderStroke(2.dp, if (completed) Leaf else Color(0xFFE9CA80)), shadowElevation = 3.dp) {
        Box(contentAlignment = Alignment.Center) {
            Text(character, fontFamily = CharacterFont, fontSize = 60.sp, color = Ink)
            if (completed) Text("✓", color = Leaf, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
    }
}

@Composable
internal fun GardenMetricStrip(text: String, tint: Color = WheatLight) {
    GardenPanel(Modifier.fillMaxWidth(), tint) {
        Text(text, color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

/** 大表单和练习各使用一套 lazy 滚动；关闭按钮附着外壳，不受滚动影响。 */
@Composable
internal fun GardenDialogFrame(title: String, subtitle: String, onDismiss: () -> Unit,
    closeEnabled: Boolean = true, tint: Color = SkyLight, content: LazyListScope.() -> Unit) {
    Dialog(onDismissRequest = { if (closeEnabled) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(24.dp), contentAlignment = Alignment.Center) {
            val dialogWidth = maxWidth
            Surface(Modifier.widthIn(max = 1100.dp).fillMaxWidth(.96f).heightIn(max = maxHeight * .94f),
                shape = RoundedCornerShape(32.dp), color = Background, border = BorderStroke(3.dp, Color.White), shadowElevation = 16.dp) {
                Box {
                    LazyColumn(state = rememberLazyListState(), contentPadding = PaddingValues(24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        item(key = "garden-dialog-header") {
                            GardenPanel(Modifier.fillMaxWidth().padding(end = 48.dp), tint) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (dialogWidth > 600.dp) GardenArt(R.drawable.garden_floral_corner, Modifier.size(64.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(title, color = Ink, style = MaterialTheme.typography.headlineMedium)
                                        Text(subtitle, color = Color(0xFF687D9F))
                                    }
                                    if (dialogWidth > 720.dp) GardenArt(R.drawable.garden_mascot_welcome, Modifier.size(72.dp))
                                }
                            }
                        }
                        content()
                    }
                    Surface(Modifier.align(Alignment.TopEnd).padding(12.dp), shape = CircleShape, color = Color.White) {
                        IconButton(onClick = onDismiss, enabled = closeEnabled, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Close, "关闭", tint = Ink)
                        }
                    }
                }
            }
        }
    }
}

/** 小确认弹窗统一限制在系统栏/键盘内，内容自行滚动。 */
@Composable
internal fun GardenDialog(onDismissRequest: () -> Unit, properties: DialogProperties = DialogProperties(), content: @Composable () -> Unit) {
    Dialog(onDismissRequest, properties = properties) {
        Box(Modifier.safeDrawingPadding().imePadding().padding(12.dp), contentAlignment = Alignment.Center) { content() }
    }
}
