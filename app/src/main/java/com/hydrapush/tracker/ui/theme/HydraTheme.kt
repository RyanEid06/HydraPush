package com.hydrapush.tracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object HydraColors {
    val Background = Color(0xFFF5FCFD)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceAqua = Color(0xFFE8FAF9)
    val SurfaceLime = Color(0xFFF3FCE9)
    val SurfaceCoral = Color(0xFFFFF0EA)
    val SurfaceBlue = Color(0xFFECF5FD)

    val PrimaryTeal = Color(0xFF0CC5BB)
    val DeepTeal = Color(0xFF07373B)
    val Aqua = Color(0xFF12D3D0)
    val PushupLime = Color(0xFF8BD70F)
    val LimeBright = Color(0xFFA5E93F)
    val ProteinCoral = Color(0xFFFB7659)
    val SecondaryBlue = Color(0xFF46B5EF)

    val Navy = Color(0xFF153139)
    val TextPrimary = Color(0xFF153139)
    val TextSecondary = Color(0xFF61727B)
    val Muted = Color(0xFF61727B)
    val Stroke = Color(0xFFD6E8E9)
    val PerfectDayOrange = Color(0xFFFF7D4A)

    // Convenient aliases
    val Lime = PushupLime
    val Coral = ProteinCoral
    val Sky = SecondaryBlue
    val Mist = Background
    val TealCard = SurfaceAqua
    val GreenCard = SurfaceLime
    val RedCard = SurfaceCoral
}

private val HydraLight = lightColorScheme(
    primary = HydraColors.PrimaryTeal,
    secondary = HydraColors.PushupLime,
    tertiary = HydraColors.ProteinCoral,
    background = HydraColors.Background,
    surface = HydraColors.Surface,
    onBackground = HydraColors.TextPrimary,
    onSurface = HydraColors.TextPrimary,
    onPrimary = Color.White
)

private val HydraDark = darkColorScheme(
    primary = HydraColors.Aqua,
    secondary = HydraColors.LimeBright,
    tertiary = HydraColors.ProteinCoral,
    background = Color(0xFF0C1D21),
    surface = Color(0xFF152A2F),
    onBackground = Color(0xFFE5F5F7),
    onSurface = Color(0xFFE5F5F7),
    onPrimary = Color(0xFF07373B)
)

@Composable
fun HydraTheme(
    themeMode: String = "SYSTEM",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode.uppercase()) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemInDarkTheme()
    }

    val colors = if (darkTheme) HydraDark else HydraLight
    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
