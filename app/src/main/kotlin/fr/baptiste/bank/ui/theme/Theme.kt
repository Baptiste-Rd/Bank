package fr.baptiste.bank.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Palette fixe, plutôt que les couleurs dynamiques du fond d'écran : une couleur porte
 * ici un sens (vert = dans le budget, ambre = à surveiller, brique = dépassé) qui doit
 * rester le même quel que soit le téléphone.
 */
private val Sapin = Color(0xFF1F5C4A)
private val SapinClair = Color(0xFF7CC4A6)
private val Ambre = Color(0xFFB7791F)
private val AmbreClair = Color(0xFFE3B55B)
private val Brique = Color(0xFFB3412E)
private val BriqueClair = Color(0xFFEF8A73)

private val LightColors = lightColorScheme(
    primary = Sapin,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5EADF),
    onPrimaryContainer = Color(0xFF0E3328),
    secondary = Color(0xFF4F6359),
    secondaryContainer = Color(0xFFDDE6E0),
    onSecondaryContainer = Color(0xFF1B2721),
    background = Color(0xFFF3F5F2),
    onBackground = Color(0xFF17201C),
    surface = Color(0xFFF3F5F2),
    onSurface = Color(0xFF17201C),
    surfaceVariant = Color(0xFFE2E8E3),
    onSurfaceVariant = Color(0xFF55635C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFBF9),
    surfaceContainer = Color(0xFFEDF1EE),
    surfaceContainerHigh = Color(0xFFE7ECE8),
    surfaceContainerHighest = Color(0xFFE1E7E2),
    outline = Color(0xFF8A9891),
    outlineVariant = Color(0xFFC9D3CD),
    error = Brique,
    onError = Color.White,
    errorContainer = Color(0xFFF7DAD3),
    onErrorContainer = Color(0xFF4A120A),
)

private val DarkColors = darkColorScheme(
    primary = SapinClair,
    onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF1F4A3C),
    onPrimaryContainer = Color(0xFFD5EADF),
    secondary = Color(0xFFB5C9BE),
    secondaryContainer = Color(0xFF34443C),
    onSecondaryContainer = Color(0xFFD9E5DE),
    background = Color(0xFF121815),
    onBackground = Color(0xFFDDE4DF),
    surface = Color(0xFF121815),
    onSurface = Color(0xFFDDE4DF),
    surfaceVariant = Color(0xFF3A4540),
    onSurfaceVariant = Color(0xFFB4C1BA),
    surfaceContainerLowest = Color(0xFF0D120F),
    surfaceContainerLow = Color(0xFF19201C),
    surfaceContainer = Color(0xFF1D2521),
    surfaceContainerHigh = Color(0xFF242D28),
    surfaceContainerHighest = Color(0xFF2E3832),
    outline = Color(0xFF7F8C86),
    outlineVariant = Color(0xFF3F4A45),
    error = BriqueClair,
    onError = Color(0xFF5C1407),
    errorContainer = Color(0xFF7A2A1C),
    onErrorContainer = Color(0xFFFFDAD2),
)

/** Couleurs de statut qui n'existent pas dans le schéma Material. */
@Immutable
data class StatusColors(val good: Color, val warning: Color, val over: Color)

private val LocalStatusColors = staticCompositionLocalOf {
    StatusColors(good = Sapin, warning = Ambre, over = Brique)
}

object BankTheme {
    val status: StatusColors
        @Composable @ReadOnlyComposable
        get() = LocalStatusColors.current
}

/**
 * Montants en chiffres tabulaires (« tnum ») : chaque chiffre a la même largeur,
 * les colonnes de montants restent alignées et un chiffre qui change ne fait pas bouger le reste.
 */
private const val TABULAR = "tnum"

private val BankTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(
            fontWeight = FontWeight.Light,
            fontSize = 64.sp,
            lineHeight = 68.sp,
            letterSpacing = (-1.5).sp,
            fontFeatureSettings = TABULAR,
        ),
        headlineMedium = base.headlineMedium.copy(fontFeatureSettings = TABULAR),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR),
        bodyLarge = base.bodyLarge.copy(fontFeatureSettings = TABULAR),
        bodyMedium = base.bodyMedium.copy(fontFeatureSettings = TABULAR),
        bodySmall = base.bodySmall.copy(fontFeatureSettings = TABULAR),
    )
}

/** Style d'un montant dans une liste : chiffres tabulaires, graisse marquée. */
val AmountStyle: TextStyle
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography.titleMedium

@Composable
fun BankTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val status = if (darkTheme) {
        StatusColors(good = SapinClair, warning = AmbreClair, over = BriqueClair)
    } else {
        StatusColors(good = Sapin, warning = Ambre, over = Brique)
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalStatusColors provides status) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = BankTypography,
            content = content,
        )
    }
}
