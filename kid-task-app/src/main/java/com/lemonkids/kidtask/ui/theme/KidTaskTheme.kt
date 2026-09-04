package com.lemonkids.kidtask.ui.theme

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

// ========== 卡通糖果色板（来源 v0 设计稿） ==========

/** 主粉色 #FF85A2 — 按钮主色、选中态 */
val Pink = Color(0xFFFF85A2)
/** 淡粉 #FFB3C6 */
val PinkSoft = Color(0xFFFFB3C6)
/** 薰衣草紫 #C3AED6 */
val Lavender = Color(0xFFC3AED6)
/** 淡紫 */
val LavenderSoft = Color(0xFFE1D5F0)
/** 薄荷绿 #A8E6CF — 已完成 */
val Mint = Color(0xFF66BB6A)
/** 淡薄荷 */
val MintSoft = Color(0xFFD4F5E3)
/** 已完成任务卡片底色，比通用淡薄荷更醒目。 */
val CompletedTaskBackground = Color(0xFFC8EACF)
/** 已完成任务卡片边框。 */
val CompletedTaskBorder = Color(0xFF88C797)
/** 珊瑚橙 #FF8A80 — 未完成/过期 */
val Coral = Color(0xFFFF8A80)
/** 淡珊瑚 */
val CoralSoft = Color(0xFFFFD0CC)
/** 奶黄 #FFCA28 — 积分星星 */
val Sunny = Color(0xFFFFCA28)
/** 浅奶黄 #FFF8E1 — 页面背景 */
val Cream = Color(0xFFFFF8E1)
/** 柔和深粉棕 — 正文文字 */
val InkBrown = Color(0xFF6B4B4B)
/** 灰色 muted */
val MutedGray = Color(0xFFA3A3A3)
/** 浅灰背景 */
val MutedBg = Color(0xFFF5F0EB)

private val KidLightColors = lightColorScheme(
    primary = Pink,
    onPrimary = Color.White,
    primaryContainer = PinkSoft,
    secondary = Lavender,
    onSecondary = Color.White,
    secondaryContainer = LavenderSoft,
    tertiary = Mint,
    background = Cream,
    surface = Color.White,
    surfaceVariant = MutedBg,
    error = Coral,
    outline = Color(0xFFE0D8D0),
    onSurfaceVariant = MutedGray
)

private val KidShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val KidTypography = Typography(
    headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
)

@Composable
fun KidTaskTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KidLightColors,
        shapes = KidShapes,
        typography = KidTypography,
        content = content
    )
}
