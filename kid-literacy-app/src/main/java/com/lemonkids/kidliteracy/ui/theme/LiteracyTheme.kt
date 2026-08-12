package com.lemonkids.kidliteracy.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Wheat = Color(0xFFF4C95D)
val WheatLight = Color(0xFFFFF3C9)
val Sky = Color(0xFF62B6E8)
val SkyLight = Color(0xFFE0F4FF)
val Leaf = Color(0xFF65B96B)
val LeafLight = Color(0xFFE0F5E2)
val Coral = Color(0xFFF28B63)
val CoralLight = Color(0xFFFFE8DE)
/** 仅用于口语评测中明确标示错读汉字的错误红。 */
val EvaluationErrorRed = Color(0xFFBA1A1A)
val Ink = Color(0xFF40505B)
val Background = Color(0xFFFFF9E8)
val Line = Color(0xFFE7DAB8)

private val colors = lightColorScheme(
    primary = Sky,
    onPrimary = Color.White,
    primaryContainer = SkyLight,
    secondary = Wheat,
    secondaryContainer = WheatLight,
    tertiary = Leaf,
    tertiaryContainer = LeafLight,
    background = Background,
    surface = Color.White,
    surfaceVariant = WheatLight,
    onSurface = Ink,
    onSurfaceVariant = Color(0xFF78838A),
    outline = Line,
    error = Coral
)

private val shapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp)
)

private val typography = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.ExtraBold),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
)

@Composable
fun LemonLiteracyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, shapes = shapes, typography = typography, content = content)
}
