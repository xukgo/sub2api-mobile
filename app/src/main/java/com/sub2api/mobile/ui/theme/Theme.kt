package com.sub2api.mobile.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.view.WindowCompat

val Ink = Color(0xFF17201C)
val Forest = Color(0xFF176B4D)
val Mint = Color(0xFF54D693)
val Coral = Color(0xFFA94738)
val Amber = Color(0xFF9A6714)
val Paper = Color(0xFFF4F7F4)
val Line = Color(0xFFDCE4DE)

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F1E5),
    onPrimaryContainer = Color(0xFF073D2A),
    secondary = Coral,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE2DC),
    onSecondaryContainer = Color(0xFF5B170F),
    tertiary = Amber,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEAF0EC),
    onSurfaceVariant = Color(0xFF4E5953),
    outline = Color(0xFF718078),
    outlineVariant = Line,
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF78D6AA),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF0B523A),
    onPrimaryContainer = Color(0xFFADF2CF),
    secondary = Color(0xFFFFB4A8),
    onSecondary = Color(0xFF5F160F),
    secondaryContainer = Color(0xFF812D22),
    onSecondaryContainer = Color(0xFFFFDAD4),
    tertiary = Color(0xFFEBC174),
    onTertiary = Color(0xFF442B00),
    background = Color(0xFF101613),
    onBackground = Color(0xFFE2EAE4),
    surface = Color(0xFF151C18),
    onSurface = Color(0xFFE2EAE4),
    surfaceVariant = Color(0xFF25302A),
    onSurfaceVariant = Color(0xFFBEC9C2),
    outline = Color(0xFF89958E),
    outlineVariant = Color(0xFF39443E),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF8C1D18),
)

private fun appTextStyle(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = 0.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val AppTypography = Typography(
    displaySmall = appTextStyle(32, 40, FontWeight.Bold),
    headlineMedium = appTextStyle(26, 34, FontWeight.SemiBold),
    headlineSmall = appTextStyle(22, 29, FontWeight.SemiBold),
    titleLarge = appTextStyle(20, 28, FontWeight.SemiBold),
    titleMedium = appTextStyle(16, 23, FontWeight.SemiBold),
    titleSmall = appTextStyle(14, 20, FontWeight.SemiBold),
    bodyLarge = appTextStyle(16, 24),
    bodyMedium = appTextStyle(14, 21),
    bodySmall = appTextStyle(12, 18),
    labelLarge = appTextStyle(14, 20, FontWeight.Medium),
    labelMedium = appTextStyle(12, 17, FontWeight.Medium),
    labelSmall = appTextStyle(11, 16, FontWeight.Medium),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

@Composable
fun Sub2ApiTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
