package app.lightlink.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

private const val SCREEN_HOME = "home"
private const val SCREEN_COMM = "comm"
private const val SCREEN_LIGHTNING = "lightning"
private const val SCREEN_SETTINGS = "settings"

/** ناوبری ساده‌ی تک‌پشته‌ای بین صفحه‌ی اصلی و سه بخش برنامه. */
@Composable
fun LightLinkApp() {
    var screen by rememberSaveable { mutableStateOf(SCREEN_HOME) }

    BackHandler(enabled = screen != SCREEN_HOME) { screen = SCREEN_HOME }

    when (screen) {
        SCREEN_COMM -> CommScreen(onBack = { screen = SCREEN_HOME })
        SCREEN_LIGHTNING -> LightningScreen(onBack = { screen = SCREEN_HOME })
        SCREEN_SETTINGS -> SettingsScreen(onBack = { screen = SCREEN_HOME })
        else -> HomeScreen(
            onOpenComm = { screen = SCREEN_COMM },
            onOpenLightning = { screen = SCREEN_LIGHTNING },
            onOpenSettings = { screen = SCREEN_SETTINGS },
        )
    }
}
