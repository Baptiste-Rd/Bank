package fr.baptiste.bank.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
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
 * Palette fixe bleu et blanc, plutôt que les couleurs dynamiques du fond d'écran : une couleur
 * porte ici un sens (bleu = dans le budget, rouge clair = à surveiller, rouge = dépassé) qui
 * doit rester le même quel que soit le téléphone.
 */
private val Bleu = Color(0xFF1D4ED8)
private val BleuClair = Color(0xFF8FB0FF)
private val RougeClair = Color(0xFFE5677A)
private val RougeClairNuit = Color(0xFFFF8F9C)
private val Rouge = Color(0xFFC81E3A)
private val RougeNuit = Color(0xFFFF5C6E)

private val LightColors = lightColorScheme(
    primary = Bleu,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF0B1F5C),
    secondary = Color(0xFF4A5A80),
    secondaryContainer = Color(0xFFE1E8F8),
    onSecondaryContainer = Color(0xFF16223F),
    background = Color(0xFFF6F8FD),
    onBackground = Color(0xFF0F1B3D),
    surface = Color(0xFFF6F8FD),
    onSurface = Color(0xFF0F1B3D),
    surfaceVariant = Color(0xFFE3E9F6),
    onSurfaceVariant = Color(0xFF55617E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFCFF),
    surfaceContainer = Color(0xFFEEF2FB),
    surfaceContainerHigh = Color(0xFFE7ECF8),
    surfaceContainerHighest = Color(0xFFE0E7F5),
    outline = Color(0xFF8E9BBA),
    outlineVariant = Color(0xFFD3DBEC),
    error = Rouge,
    onError = Color.White,
    errorContainer = Color(0xFFFCDDE2),
    onErrorContainer = Color(0xFF4A0614),
)

private val DarkColors = darkColorScheme(
    primary = BleuClair,
    onPrimary = Color(0xFF0B1F5C),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFFB4C2E3),
    secondaryContainer = Color(0xFF2A3656),
    onSecondaryContainer = Color(0xFFDCE4F7),
    background = Color(0xFF0A1228),
    onBackground = Color(0xFFE2E8F8),
    surface = Color(0xFF0A1228),
    onSurface = Color(0xFFE2E8F8),
    surfaceVariant = Color(0xFF2B3655),
    onSurfaceVariant = Color(0xFFAEBAD6),
    surfaceContainerLowest = Color(0xFF060C1C),
    surfaceContainerLow = Color(0xFF0F1830),
    surfaceContainer = Color(0xFF131D38),
    surfaceContainerHigh = Color(0xFF1A2542),
    surfaceContainerHighest = Color(0xFF222E4E),
    outline = Color(0xFF7887A8),
    outlineVariant = Color(0xFF2E3A5A),
    error = RougeNuit,
    onError = Color(0xFF4A0614),
    errorContainer = Color(0xFF7A1426),
    onErrorContainer = Color(0xFFFFD9DE),
)

/*
 * Variante Liquid Glass : toujours sombre, quel que soit le thème du téléphone. Le fond est un
 * bleu presque noir, les cartes et les fenêtres sont un cran plus claires, le texte est blanc
 * (contraste supérieur à 12:1 sur le fond, et à 4,5:1 pour le texte secondaire).
 */
private val GlassDarkColors = DarkColors.copy(
    primary = Color(0xFF7DB4FF),
    onPrimary = Color(0xFF041C45),
    primaryContainer = Color(0xFF173A7A),
    background = Color.Transparent,
    surface = Color.Transparent,
    onSurface = Color(0xFFFFFFFF),
    onBackground = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFFC3CDE6),
    // Fenêtres et menus : bleu nuit un peu plus clair que le fond, presque opaque.
    surfaceContainerLow = Color(0xF5111D3F),
    surfaceContainer = Color(0xF5142247),
    surfaceContainerHigh = Color(0xF517264E),
    surfaceContainerHighest = Color(0xF51C2D58),
    surfaceVariant = Color(0x29FFFFFF),
    outline = Color(0xFF8E9BBA),
    outlineVariant = Color(0x29FFFFFF),
)

/** Texte posé directement sur le fond : blanc, avec un secondaire plus lumineux que sur les cartes. */
private fun ColorScheme.onGlassBackground(): ColorScheme = copy(
    primary = Color(0xFFBFDBFF),
    onSurface = Color.White,
    onBackground = Color.White,
    onSurfaceVariant = Color(0xFFF0F5FF),
    outline = Color(0x99FFFFFF),
    outlineVariant = Color(0x33FFFFFF),
)

/*
 * Statuts en verre, selon la convention des feux : bleu (dans le budget), ambre (à surveiller),
 * rouge (dépassé). Contrastes mesurés sur une carte (#19357D) : bleu 5,3:1, ambre 6,8:1,
 * rouge 5,1:1, tous au-dessus du seuil de 4,5:1 du texte courant (WCAG 2.2, critère 1.4.3).
 */
private val GlassCardStatusDark = StatusColors(good = Color(0xFF7DB4FF), warning = Color(0xFFFBBF24), over = Color(0xFFFF8A97))
private val GlassBackgroundStatus = StatusColors(good = Color(0xFFBFDBFF), warning = Color(0xFFFCD34D), over = Color(0xFFFFB3BD))

/**
 * Applique les couleurs « sur fond » ([onBackground] = true) ou « sur verre » au contenu.
 * Le fond est entouré du premier ; chaque carte de verre revient au second.
 */
@Composable
fun GlassSurfaceColors(onBackground: Boolean, content: @Composable () -> Unit) {
    val scheme = if (onBackground) GlassDarkColors.onGlassBackground() else GlassDarkColors
    val status = if (onBackground) GlassBackgroundStatus else GlassCardStatusDark
    androidx.compose.runtime.CompositionLocalProvider(
        LocalStatusColors provides status,
        androidx.compose.material3.LocalContentColor provides scheme.onSurface,
    ) {
        MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, content = content)
    }
}

/**
 * Couleur d'identification de chaque type d'enveloppe, dans l'interface verre. Une palette
 * catégorielle : des teintes nettement séparées (bleu, violet, cyan, ardoise, vert), qui
 * évitent l'ambre et le rouge réservés aux statuts. Toutes dépassent 6:1 sur une carte, donc
 * utilisables aussi pour du texte. La couleur n'est jamais seule à porter l'information : le
 * type est toujours écrit en toutes lettres (WCAG 2.2, critère 1.4.1).
 */
fun envelopeTypeColor(type: fr.baptiste.bank.domain.model.EnvelopeType): Color = when (type) {
    fr.baptiste.bank.domain.model.EnvelopeType.DAILY -> Color(0xFF93C5FD)
    fr.baptiste.bank.domain.model.EnvelopeType.RECURRING -> Color(0xFFC4B5FD)
    fr.baptiste.bank.domain.model.EnvelopeType.ANNUAL -> Color(0xFF67E8F9)
    fr.baptiste.bank.domain.model.EnvelopeType.FIXED -> Color(0xFFCBD5E1)
    fr.baptiste.bank.domain.model.EnvelopeType.SAVINGS -> Color(0xFF6EE7B7)
}

/** Couleurs de statut qui n'existent pas dans le schéma Material. */
@Immutable
data class StatusColors(val good: Color, val warning: Color, val over: Color)

private val LocalStatusColors = staticCompositionLocalOf {
    StatusColors(good = Bleu, warning = RougeClair, over = Rouge)
}

/** Vrai quand l'interface Liquid Glass est active : les fenêtres floutent alors ce qu'il y a derrière. */
val LocalGlassStyle = staticCompositionLocalOf { false }

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
    glass: Boolean = false,
    content: @Composable () -> Unit,
) {
    val status = when {
        glass -> GlassCardStatusDark
        darkTheme -> StatusColors(good = BleuClair, warning = RougeClairNuit, over = RougeNuit)
        else -> StatusColors(good = Bleu, warning = RougeClair, over = Rouge)
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalStatusColors provides status,
        LocalGlassStyle provides glass,
    ) {
        MaterialTheme(
            colorScheme = when {
                glass -> GlassDarkColors
                darkTheme -> DarkColors
                else -> LightColors
            },
            typography = BankTypography,
            content = content,
        )
    }
}
