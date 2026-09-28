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

// 公共壳层与今日任务使用的 Stitch 柠檬主题令牌。
val Lemon = Color(0xFFFACC15)
val LemonPressed = Color(0xFFEAB308)
val LemonShadow = Color(0xFFCA8A04)
val LemonSoft = Color(0xFFFEF08A)
val LemonBorder = Color(0xFFFDE68A)
val Canvas = Color(0xFFFFFDF5)
val Butter = Color(0xFFFFF9E6)
val FreshMint = Color(0xFF22C55E)
val FreshMintSoft = Color(0xFFDCFCE7)
val FreshMintShadow = Color(0xFF15803D)
val Strawberry = Color(0xFFFB7185)
val StrawberrySoft = Color(0xFFFFE4E6)
val SkyBlue = Color(0xFF38BDF8)
val SkyBlueSoft = Color(0xFFE0F2FE)
val SlateInk = Color(0xFF334155)
val SlateMuted = Color(0xFF64748B)
val TaskPanel = Color(0xFFF4F7FF)

// 旧页面继续使用原有令牌；后续批次按各自设计替换。

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
/** 天空蓝 — 任务分类色，避免与已完成薄荷绿混淆。 */
val Sky = Color(0xFF64B5F6)
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
    primary = Lemon,
    onPrimary = SlateInk,
    primaryContainer = LemonSoft,
    secondary = FreshMint,
    onSecondary = Color.White,
    secondaryContainer = FreshMintSoft,
    tertiary = Strawberry,
    background = Canvas,
    surface = Color.White,
    surfaceVariant = Butter,
    error = Coral,
    outline = LemonBorder,
    onSurface = SlateInk,
    onSurfaceVariant = SlateMuted
)

private val KidShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val KidTypography = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.ExtraBold),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold),
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
