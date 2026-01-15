package cu.cuban.cmcubano.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun CMCubanoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    amoledDark: Boolean = false,
    content: @Composable () -> Unit
) {
    val baseColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val colorScheme = when {
        darkTheme && amoledDark -> {
            baseColorScheme.copy(
                background = Color.Black,
                surface = Color(0xFF121212),
                surfaceVariant = Color(0xFF1B1B1B),
                surfaceContainerLowest = Color(0xFF0A0A0A),
                surfaceContainerLow = Color(0xFF111111),
                surfaceContainer = Color(0xFF161616),
                surfaceContainerHigh = Color(0xFF1C1C1C),
                surfaceContainerHighest = Color(0xFF222222)
            )
        }

        darkTheme -> {
            baseColorScheme.copy(
                background = Color(0xFF0F0F0F),
                surface = Color(0xFF1A1A1A),
                surfaceVariant = Color(0xFF242424),
                surfaceContainerLowest = Color(0xFF141414),
                surfaceContainerLow = Color(0xFF1A1A1A),
                surfaceContainer = Color(0xFF202020),
                surfaceContainerHigh = Color(0xFF262626),
                surfaceContainerHighest = Color(0xFF2C2C2C)
            )
        }

        else -> baseColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}