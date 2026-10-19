package com.lemonkids.kidliteracy.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lemonkids.kidliteracy.R
import com.lemonkids.kidliteracy.ui.theme.*

/** A09/A10 的学习专用弹层；练习项持续组合，滚动不销毁正在进行的朗读会话。 */
@Composable
internal fun StudyDialogFrame(
    character: String,
    onDismiss: () -> Unit,
    footer: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            val scale = LocalGardenLayoutScale.current
            val compact = maxWidth < 650.dp || maxHeight < 430.dp || LocalDensity.current.fontScale > 1.25f
            val frameWidth = if (compact) maxWidth * .96f else (maxWidth * .74f).coerceAtMost(900.dp)
            val headerHeight = frameWidth * (199f / 1136f)
            val bodyTop = frameWidth * (59f / 1136f)
            val titleSize = (if (compact) 24.sp else 34.sp) * scale
            val frameHeight = maxHeight * .98f
            Box(Modifier.width(frameWidth).height(frameHeight).testTag("study-dialog-frame")) {
                Surface(
                    Modifier.fillMaxSize().padding(top = bodyTop),
                    shape = RoundedCornerShape(30.dp),
                    color = Color(0xFFFFFEF8),
                    border = BorderStroke(3.dp, Color.White),
                    shadowElevation = 14.dp
                ) {
                    Box(Modifier.fillMaxSize()) {
                        if (!compact) {
                            GardenArt(R.drawable.garden_floral_corner,
                                Modifier.align(Alignment.BottomStart).offset(x = 5.dp, y = (-2).dp).width(140.dp).height(65.dp))
                            GardenArt(R.drawable.garden_floral_corner,
                                Modifier.align(Alignment.BottomEnd).offset(x = (-5).dp, y = (-2).dp).width(140.dp).height(65.dp))
                        }
                    Column(Modifier.fillMaxSize().padding(top = headerHeight - bodyTop + if (compact) 2.dp else 8.dp)) {
                        Column(
                            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                                .padding(start = (if (compact) 14.dp else 28.dp) * scale,
                                    end = (if (compact) 14.dp else 28.dp) * scale, top = 4.dp, bottom = 12.dp)
                                .testTag("study-dialog-list"),
                            verticalArrangement = Arrangement.spacedBy(if (compact) 9.dp else 8.dp),
                            content = content
                        )
                        Box(Modifier.fillMaxWidth().padding(horizontal = (if (compact) 18.dp else 100.dp) * scale,
                            vertical = 8.dp).testTag("study-dialog-footer")) { footer() }
                    }
                    }
                }
                // 云朵和装饰均不承载交互。动态标题覆盖在云朵上，文字可由无障碍读取。
                Image(painterResource(R.drawable.garden_study_header_cloud), null,
                    Modifier.fillMaxWidth().height(headerHeight), contentScale = ContentScale.Fit)
                Image(painterResource(R.drawable.garden_study_header_cloud_layers), null,
                    Modifier.fillMaxWidth().offset(y = headerHeight * .26f).height(headerHeight * .74f)
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            drawContent()
                            drawRect(Brush.verticalGradient(
                                0f to Color.Transparent,
                                .35f to Color.White.copy(alpha = .52f),
                                .75f to Color.White.copy(alpha = .40f),
                                1f to Color.Transparent
                            ), blendMode = BlendMode.DstIn)
                        },
                    contentScale = ContentScale.FillBounds)
                if (!compact) {
                    GardenArt(R.drawable.garden_study_rays,
                        Modifier.align(Alignment.TopStart).offset(x = frameWidth * .05f, y = (-18).dp)
                            .width(frameWidth * .088f).height(headerHeight * .46f))
                    GardenArt(R.drawable.garden_study_lemon,
                        Modifier.align(Alignment.TopStart).offset(x = frameWidth * .059f, y = (-8).dp)
                            .width(frameWidth * .206f).height(headerHeight * 1.04f))
                    GardenArt(R.drawable.garden_floral_corner,
                        Modifier.align(Alignment.TopStart).offset(x = frameWidth * .006f, y = headerHeight * .60f)
                            .width(frameWidth * .36f).height(headerHeight * .40f))
                    GardenArt(R.drawable.garden_floral_corner,
                        Modifier.align(Alignment.TopEnd).offset(x = (-5).dp, y = headerHeight * .47f)
                            .width(frameWidth * .294f).height(headerHeight * .53f))
                }
                Column(Modifier.align(Alignment.TopCenter).padding(top = if (compact) 7.dp else headerHeight * .25f),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(color = Coral)) { append(character) }
                        append("的朗读练习")
                    }, fontSize = titleSize, fontWeight = FontWeight.ExtraBold, color = Ink,
                        maxLines = 1)
                    Text("长按汉字可听正确读音", color = Color(0xFF3973B8),
                        fontSize = (if (compact) 13.sp else 17.sp) * scale, fontWeight = FontWeight.Bold)
                }
                Surface(Modifier.align(Alignment.TopEnd).padding(top = if (compact) 5.dp else headerHeight * .17f, end = 3.dp),
                    shape = CircleShape, color = Color.White, shadowElevation = 3.dp) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(56.dp).testTag("study-dialog-close")) {
                        Icon(Icons.Default.Close, "关闭", tint = Ink)
                    }
                }
            }
        }
    }
}
