package app.lume.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Tema escuro profissional. Uma cor de destaque (azul #31A8FF, como o
 * protótipo v2) + neutros. Nunca âmbar/amarelo em layout. Sem preto
 * puro: o fundo mais escuro é #121417.
 *
 * A UI usa SEMPRE os papéis do [MaterialTheme.colorScheme] e da
 * [MaterialTheme.typography] — nunca hex solto em tela.
 */

// Cor de destaque única. Vive só aqui; telas usam colorScheme.primary.
private val AccentBlue = Color(0xFF31A8FF)
private val AccentBlueContainer = Color(0xFF0B2A45)
private val AccentBlueOnContainer = Color(0xFFBFE3FF)

private val SurfaceDark = Color(0xFF121417)
private val SurfaceContainerLowest = Color(0xFF0E1013)
private val SurfaceContainerLow = Color(0xFF1A1D21)
private val SurfaceContainer = Color(0xFF20242A)
private val SurfaceContainerHigh = Color(0xFF2A2F36)
private val SurfaceContainerHighest = Color(0xFF353B44)
private val OnSurface = Color(0xFFE8EAED)
private val OnSurfaceVariant = Color(0xFFA8B0B8)
private val Outline = Color(0xFF3A4149)
private val OutlineVariant = Color(0xFF2A2F36)

private val LumeColorScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color(0xFF00101D),
    primaryContainer = AccentBlueContainer,
    onPrimaryContainer = AccentBlueOnContainer,
    secondary = Color(0xFF8FB8D8),
    onSecondary = Color(0xFF0B1A26),
    surface = SurfaceDark,
    onSurface = OnSurface,
    surfaceVariant = SurfaceContainer,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainerLowest = SurfaceContainerLowest,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LumeShapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp)
)

/**
 * Escala de espaçamento fixa (DESIGN_GUIDE): 4/8/12/16/24/32.
 * Nunca valores aleatórios.
 */
object LumeSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

@Composable
fun LumeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LumeColorScheme,
        typography = Typography(),
        shapes = LumeShapes,
        content = content
    )
}
