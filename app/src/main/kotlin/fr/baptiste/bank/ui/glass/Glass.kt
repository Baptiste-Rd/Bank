package fr.baptiste.bank.ui.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle
import fr.baptiste.bank.ui.theme.GlassSurfaceColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Briques de l'interface Liquid Glass.
 *
 * Le verre ne peut réfracter que ce qui est dessiné derrière lui. L'écran est donc empilé en
 * deux couches enregistrées : le fond liquide seul (que réfractent les cartes du contenu), et
 * le fond plus le contenu (que réfractent les éléments flottants : barre d'onglets, boutons,
 * messages). Une carte ne réfracte jamais une couche qui la contient, ce qui serait circulaire.
 */

/** Couche du fond liquide seul, réfractée par les cartes du contenu. */
val LocalBackgroundBackdrop = staticCompositionLocalOf<Backdrop> {
    error("GlassCard doit être placée dans GlassMainScreen")
}

/** Couche fond + contenu, réfractée par les éléments flottants. */
val LocalScreenBackdrop = staticCompositionLocalOf<Backdrop> {
    error("Les éléments flottants doivent être placés dans GlassMainScreen")
}

/** Teintes du verre, qui dépendent du thème clair ou sombre. */
@Immutable
data class GlassTokens(
    val surface: Color,
    val surfaceStrong: Color,
    val shadow: Color,
    val baseTop: Color,
    val baseBottom: Color,
    val blobs: List<Color>,
)

/*
 * Fond bleu profond dans les deux modes : le verre ressort sur un fond riche et sombre, et le
 * texte blanc posé dessus garde un contraste d'au moins 4,5:1 même là où les taches se superposent.
 * En mode clair, les cartes sont presque blanches (verre dépoli) ; en mode sombre, bleu nuit.
 */
private val LightTokens = GlassTokens(
    surface = Color.White.copy(alpha = 0.78f),
    surfaceStrong = Color.White.copy(alpha = 0.88f),
    shadow = Color(0xFF020A24).copy(alpha = 0.22f),
    baseTop = Color(0xFF163A94),
    baseBottom = Color(0xFF0B2366),
    blobs = listOf(
        Color(0xFF2563EB).copy(alpha = 0.60f),
        Color(0xFF0EA5E9).copy(alpha = 0.30f),
        Color(0xFF4F46E5).copy(alpha = 0.45f),
        Color(0xFF3B82F6).copy(alpha = 0.55f),
        Color(0xFF1E40AF).copy(alpha = 0.60f),
    ),
)

private val DarkTokens = GlassTokens(
    surface = Color(0xFF0B1630).copy(alpha = 0.55f),
    surfaceStrong = Color(0xFF0B1630).copy(alpha = 0.70f),
    shadow = Color.Black.copy(alpha = 0.40f),
    baseTop = Color(0xFF040A1C),
    baseBottom = Color(0xFF0A1A45),
    blobs = listOf(
        Color(0xFF2563EB).copy(alpha = 0.55f),
        Color(0xFF0EA5E9).copy(alpha = 0.22f),
        Color(0xFF4F46E5).copy(alpha = 0.40f),
        Color(0xFF1E40AF).copy(alpha = 0.60f),
        Color(0xFF38BDF8).copy(alpha = 0.14f),
    ),
)

object Glass {
    val tokens: GlassTokens
        @Composable
        get() = if (isSystemInDarkTheme()) DarkTokens else LightTokens
}

/** Trajectoire d'une tache de couleur : position de départ, amplitude et vitesse de dérive. */
private data class Blob(
    val x: Float,
    val y: Float,
    val radius: Float,
    val driftX: Float,
    val driftY: Float,
    val speedX: Float,
    val speedY: Float,
    val phase: Float,
)

private val blobs = listOf(
    Blob(0.15f, 0.12f, 0.65f, 0.10f, 0.06f, 1f, 1f, 0f),
    Blob(0.90f, 0.30f, 0.55f, 0.08f, 0.10f, 1f, 2f, 1.3f),
    Blob(0.30f, 0.62f, 0.70f, 0.12f, 0.07f, 2f, 1f, 2.1f),
    Blob(0.75f, 0.85f, 0.50f, 0.07f, 0.09f, 1f, 1f, 3.4f),
    Blob(0.55f, 0.40f, 0.35f, 0.15f, 0.12f, 1f, 2f, 4.6f),
)

/**
 * Fond liquide : de grandes taches de bleu qui dérivent lentement (un tour en 40 secondes).
 * Le verre a besoin d'un fond qui bouge et varie pour que la réfraction se voie ; un aplat
 * uni ne montrerait rien. L'animation suit le réglage système d'échelle des animations.
 */
@Composable
fun LiquidBackground(modifier: Modifier = Modifier) {
    val tokens = Glass.tokens
    val transition = rememberInfiniteTransition(label = "fond liquide")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing)),
        label = "dérive",
    )
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(tokens.baseTop, tokens.baseBottom)))
        val unit = size.maxDimension
        blobs.forEachIndexed { index, blob ->
            val color = tokens.blobs[index % tokens.blobs.size]
            val center = Offset(
                x = size.width * (blob.x + blob.driftX * sin(time * blob.speedX + blob.phase)),
                y = size.height * (blob.y + blob.driftY * cos(time * blob.speedY + blob.phase)),
            )
            val radius = unit * blob.radius
            drawCircle(
                brush = Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center, radius),
                radius = radius,
                center = center,
            )
        }
    }
}

/** Arrondi des cartes : franc mais discret. Les petits contrôles restent en capsule. */
val GlassCornerRadius: Dp = 18.dp

/**
 * Carte de verre : elle floute et sature le fond liquide, et le réfracte légèrement sur ses bords
 * (effet de lentille), avec une ombre portée douce. Pas de liseré de lumière : il soulignait les
 * coins de façon inégale. Son contenu reprend les couleurs « sur verre ».
 * Le flou demande Android 12 ; la réfraction, Android 13. En dessous, la carte reste une surface
 * translucide teintée, assez opaque pour que le texte reste lisible.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = GlassCornerRadius,
    strong: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdrop = LocalBackgroundBackdrop.current
    val tokens = Glass.tokens
    val tint = if (strong) tokens.surfaceStrong else tokens.surface
    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(cornerRadius) },
                effects = {
                    vibrancy()
                    blur(18.dp.toPx())
                    lens(refractionHeight = 10.dp.toPx(), refractionAmount = 18.dp.toPx())
                },
                highlight = null,
                shadow = { Shadow(radius = 20.dp, color = tokens.shadow) },
                onDrawSurface = { drawRect(tint) },
            )
            .clip(RoundedRectangle(cornerRadius))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GlassSurfaceColors(onBackground = false) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
        }
    }
}

/**
 * Voile sous la barre d'état : le contenu qui remonte s'y fond progressivement au lieu de passer
 * sous l'heure et les icônes système.
 */
@Composable
fun StatusBarScrim(modifier: Modifier = Modifier, height: Dp) {
    val tokens = Glass.tokens
    Canvas(modifier.fillMaxWidth().then(Modifier.padding(0.dp)).height(height)) {
        drawRect(Brush.verticalGradient(listOf(tokens.baseTop, tokens.baseTop.copy(alpha = 0f))))
    }
}
