package dev.xhos.null_mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = ColorPrimaryButton,
    onPrimary = ColorBackground,
    primaryContainer = ColorSecondary,
    onPrimaryContainer = ColorForeground,
    secondary = ColorMuted,
    onSecondary = ColorForeground,
    secondaryContainer = ColorSecondary,
    onSecondaryContainer = ColorForeground,
    tertiary = ColorAccent,
    onTertiary = ColorBackground,
    background = ColorBackground,
    onBackground = ColorForeground,
    surface = ColorCard,
    onSurface = ColorForeground,
    surfaceVariant = ColorSecondary,
    onSurfaceVariant = ColorMutedForeground,
    outline = ColorBorder,
    outlineVariant = ColorBorder,
    error = ColorDestructive,
    onError = ColorBackground,
)

private val DarkColorScheme = darkColorScheme(
    primary = ColorDarkPrimaryButton,
    onPrimary = ColorDarkBackground,
    primaryContainer = ColorDarkSecondary,
    onPrimaryContainer = ColorDarkForeground,
    secondary = ColorDarkMuted,
    onSecondary = ColorDarkForeground,
    secondaryContainer = ColorDarkSecondary,
    onSecondaryContainer = ColorDarkForeground,
    tertiary = ColorAccent,
    onTertiary = ColorBackground,
    background = ColorDarkBackground,
    onBackground = ColorDarkForeground,
    surface = ColorDarkCard,
    onSurface = ColorDarkForeground,
    surfaceVariant = ColorDarkSecondary,
    onSurfaceVariant = ColorDarkMutedForeground,
    outline = ColorDarkBorder,
    outlineVariant = ColorDarkBorder,
    error = ColorDestructive,
    onError = ColorBackground,
)

// Sharp radii — defining characteristic of the design language
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(6.dp),
)

@Composable
fun NullmobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
