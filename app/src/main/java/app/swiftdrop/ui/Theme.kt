package app.swiftdrop.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Netral dengan satu aksen teal yang dibatasi.
private val Light = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    background = Color(0xFFFAFAF7),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1C1C1A),
    onSurface = Color(0xFF1C1C1A),
    onSurfaceVariant = Color(0xFF4A4A46),
    error = Color(0xFFB42318),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF5EC7BD),
    onPrimary = Color(0xFF00201E),
    background = Color(0xFF121412),
    surface = Color(0xFF1B1E1C),
    onBackground = Color(0xFFEDEDEA),
    onSurface = Color(0xFFEDEDEA),
    onSurfaceVariant = Color(0xFFC4C4BE),
    error = Color(0xFFFFB4AB),
)

@Composable
fun SwiftDropTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
