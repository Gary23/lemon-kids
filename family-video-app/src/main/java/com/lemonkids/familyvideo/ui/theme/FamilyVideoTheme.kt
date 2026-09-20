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

/**
 * 柔和、高明度的配色让儿童在平板上长时间使用时更舒适：
 * 蜜桃色用于主操作，薰衣草和薄荷绿作为信息层级，不再使用高对比霓虹深色界面。
 */
private val Colors = lightColorScheme(
    primary = Color(0xFFB64F79), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E5), onPrimaryContainer = Color(0xFF3E0020),
    secondary = Color(0xFF5A648D), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E5FF), onSecondaryContainer = Color(0xFF121B43),
    tertiary = Color(0xFF386B61), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBDEFE1), onTertiaryContainer = Color(0xFF00201A),
    background = Color(0xFFFFF8F4), onBackground = Color(0xFF24191D),
    surface = Color(0xFFFFFBFF), onSurface = Color(0xFF24191D),
    surfaceVariant = Color(0xFFF4E8EC), onSurfaceVariant = Color(0xFF53434A),
    outline = Color(0xFF85737A),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002)
)
private val Shapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
)
private val Type = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 31.sp),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
)
@Composable fun FamilyVideoTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Colors, typography = Type, shapes = Shapes, content = content)
