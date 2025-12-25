package com.prasad.smsarchiver.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF6200EE),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFBB86FC),
    secondary = androidx.compose.ui.graphics.Color(0xFF03DAC6),
    onSecondary = androidx.compose.ui.graphics.Color.Black,
    background = androidx.compose.ui.graphics.Color(0xFFFFFBFE),
    surface = androidx.compose.ui.graphics.Color(0xFFFFFBFE),
    onSurface = androidx.compose.ui.graphics.Color(0xFF1C1B1F)
)

@Composable
fun SMSArchiverTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
