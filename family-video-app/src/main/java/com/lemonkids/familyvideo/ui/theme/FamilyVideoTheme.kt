package com.lemonkids.familyvideo.ui.theme

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

private val Colors = lightColorScheme(
    primary = Color(0xFFE77CA8), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E7), secondary = Color(0xFF8D78C9),
    secondaryContainer = Color(0xFFE7DEFF), tertiary = Color(0xFF73BFA3),
    background = Color(0xFFFFF9FB), surface = Color.White, surfaceVariant = Color(0xFFF9EEF3)
)
private val Shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp))
private val Type = Typography(
    headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp)
)
@Composable fun FamilyVideoTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Colors, typography = Type, shapes = Shapes, content = content)
