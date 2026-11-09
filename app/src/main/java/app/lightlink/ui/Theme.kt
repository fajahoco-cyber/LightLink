package app.lightlink.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val StormScheme = darkColorScheme(
    primary = Color(0xFFFFC53D),
    onPrimary = Color(0xFF1B1400),
    background = Color(0xFF0B1020),
    onBackground = Color(0xFFEDF1FF),
    surface = Color(0xFF151C33),
    onSurface = Color(0xFFEDF1FF),
    onSurfaceVariant = Color(0xFFA5B0D0),
    outline = Color(0xFF2A3558),
)

/** تم تیره‌ی طوفانی؛ چیدمان همیشه راست‌به‌چپ است. */
@Composable
fun LightLinkTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = StormScheme, content = content)
    }
}
