package com.duende.safegrab

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Texto negro o blanco según el fondo, para que SIEMPRE se lea. */
fun readable(c: Color): Color = if (c.luminance() > 0.5f) Color.Black else Color.White

@Composable
fun SafeGrabTheme(prefs: Prefs, content: @Composable () -> Unit) {
    val dark = when (prefs.themeMode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val bg = if (prefs.bgColor != 0) Color(prefs.bgColor) else base.background
    val bar = if (prefs.barColor != 0) Color(prefs.barColor) else base.surfaceContainer
    val btn = if (prefs.btnColor != 0) Color(prefs.btnColor) else base.primary
    val onBg = readable(bg)
    val card = lerp(bg, onBg, 0.08f)
    val scheme = base.copy(
        background = bg, onBackground = onBg,
        surface = bg, onSurface = onBg,
        surfaceVariant = card, onSurfaceVariant = onBg.copy(alpha = 0.75f),
        surfaceContainer = bar,
        surfaceContainerHigh = card, surfaceContainerHighest = card,
        outline = onBg.copy(alpha = 0.5f),
        primary = btn, onPrimary = readable(btn)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
