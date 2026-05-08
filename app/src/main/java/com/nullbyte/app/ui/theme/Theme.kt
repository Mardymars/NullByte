package com.nullbyte.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NullByteColors = darkColorScheme(
    primary = RoyalPurple,
    onPrimary = InkBlack,
    primaryContainer = RoyalPurpleBright,
    onPrimaryContainer = InkBlack,
    secondary = LilacTint,
    onSecondary = InkBlack,
    tertiary = SuccessMint,
    background = InkBlack,
    onBackground = Mist,
    surface = DeepBlack,
    onSurface = Mist,
    surfaceVariant = Graphite,
    onSurfaceVariant = LilacTint,
    error = DangerRed,
    onError = InkBlack,
)

@Composable
fun NullByteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NullByteColors,
        typography = NullByteTypography,
        content = content,
    )
}
