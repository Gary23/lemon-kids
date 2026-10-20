package com.lemonkids.kidliteracy.ui.theme

import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationInstance
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.RippleTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Wheat = Color(0xFFFFDA4C)
val WheatLight = Color(0xFFFFF4CD)
val Sky = Color(0xFF2E9DFF)
val SkyLight = Color(0xFFEAF6FF)
val Leaf = Color(0xFF31B541)
val LeafLight = Color(0xFFEFFBE5)
val Coral = Color(0xFFFF6B70)
val CoralLight = Color(0xFFFFF0EB)
/** 仅用于口语评测中明确标示错读汉字的错误红。 */
val EvaluationErrorRed = Color(0xFFBA1A1A)
val Ink = Color(0xFF061A39)
val Background = Color(0xFFFFFDF4)
val Line = Color(0xFFE9DEBC)

private val colors = lightColorScheme(
    primary = Sky,
    onPrimary = Color.White,
    primaryContainer = SkyLight,
    secondary = Wheat,
    secondaryContainer = WheatLight,
    tertiary = Leaf,
    tertiaryContainer = LeafLight,
    background = Background,
    surface = Background,
    surfaceVariant = WheatLight,
    onSurface = Ink,
    onSurfaceVariant = Color(0xFF687D9F),
    outline = Line,
    error = EvaluationErrorRed
)

private val shapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp)
)

private val typography = Typography(
    headlineLarge = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.ExtraBold),
    headlineMedium = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.ExtraBold),
    titleLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    labelLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold)
)

private object NoPressIndication : Indication {
    @Composable
    override fun rememberUpdatedInstance(interactionSource: InteractionSource): IndicationInstance =
        object : IndicationInstance {
            override fun ContentDrawScope.drawIndication() {
                drawContent()
            }
        }
}

private object NoPressRippleTheme : RippleTheme {
    @Composable
    override fun defaultColor(): Color = Color.Transparent

    @Composable
    override fun rippleAlpha(): RippleAlpha = RippleAlpha(0f, 0f, 0f, 0f)
}

@Composable
fun LemonLiteracyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, shapes = shapes, typography = typography) {
        CompositionLocalProvider(
            LocalIndication provides NoPressIndication,
            LocalRippleTheme provides NoPressRippleTheme,
            content = content
        )
    }
}
