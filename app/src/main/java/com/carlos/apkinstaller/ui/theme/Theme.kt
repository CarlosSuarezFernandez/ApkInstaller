package com.carlos.apkinstaller.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class ThemeSettings(val mode: ThemeMode = ThemeMode.SYSTEM, val dynamicColor: Boolean = true)

class ThemePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(
        ThemeSettings(
            mode = runCatching { ThemeMode.valueOf(prefs.getString(KEY_MODE, null)!!) }.getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC, true),
        ),
    )
    val settings: StateFlow<ThemeSettings> = _settings.asStateFlow()

    fun update(settings: ThemeSettings) {
        prefs.edit {
            putString(KEY_MODE, settings.mode.name)
            putBoolean(KEY_DYNAMIC, settings.dynamicColor)
        }
        _settings.value = settings
    }

    private companion object {
        const val KEY_MODE = "theme_mode"
        const val KEY_DYNAMIC = "dynamic_color"
    }
}

val supportsDynamicColor: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

private val LightColors = lightColorScheme(primary = Color(0xFF2E6A44), secondary = Color(0xFF4F6353))
private val DarkColors = darkColorScheme(primary = Color(0xFF93D5A8), secondary = Color(0xFFB6CCB8))

@Composable
fun ApkInstallerTheme(settings: ThemeSettings, content: @Composable () -> Unit) {
    val dark = when (settings.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        settings.dynamicColor && supportsDynamicColor ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
