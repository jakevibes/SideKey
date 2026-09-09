package com.snflist.sidekey

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 Expressive, and the phone's own colours where it has them.
 *
 * Expressive is not a separate theme any more: it graduated into MaterialTheme
 * when material3 1.4.0 went stable, and the old MaterialExpressiveTheme entry
 * point is internal now. So this is just MaterialTheme, and it is expressive.
 *
 * Android 12 onwards derives a palette from the wallpaper, which is the whole
 * point of Material You: the app looks like it belongs to this phone rather
 * than like it brought its own brand along. The terracotta below is only the
 * fallback for older phones.
 */
@Composable
fun SideKeyTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current

    val colors: ColorScheme = when {
        Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Terracotta, secondary = Clay)
        else -> lightColorScheme(primary = TerracottaDeep, secondary = Clay)
    }

    MaterialTheme(colorScheme = colors, content = content)
}

private val Terracotta = Color(0xFFC0564A)
private val TerracottaDeep = Color(0xFF8F3A31)
private val Clay = Color(0xFFB08968)
