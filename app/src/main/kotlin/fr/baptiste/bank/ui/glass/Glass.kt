package fr.baptiste.bank.ui.glass

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import fr.baptiste.bank.ui.theme.GlassSurfaceColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Briques de l'interface verre.
 *
 * Le verre ne peut flouter que ce qui est dessiné derrière lui. L'écran est donc empilé en deux
 * couches enregistrées : le fond seul (flouté par les cartes du contenu), et le fond plus le
 * contenu (flouté par les éléments flottants : barre d'onglets, boutons, messages). Une carte ne
 * floute jamais une couche qui la contient, ce qui serait circulaire.
 *
 * Parti pris : sobriété. Pas de liseré lumineux, pas d'effet de lentille sur les bords, pas
 * d'ombre portée : le verre se lit uniquement par le flou et une teinte un peu plus claire que
 * le fond. Aucune bordure.
 */

/** Couche du fond seul, floutée par les cartes du contenu. */
val LocalBackgroundBackdrop = staticCompositionLocalOf<Backdrop> {
    error("GlassCard doit être placée dans GlassMainScreen")
}

/** Couche fond + contenu, floutée par les éléments flottants. */
val LocalScreenBackdrop = staticCompositionLocalOf<Backdrop> {
    error("Les éléments flottants doivent être placés dans GlassMainScreen")
}

/**
 * Teintes de l'interface verre : un seul jeu, bleu nuit, quel que soit le thème du téléphone.
 * Les surfaces sont plus claires que le fond (verre dépoli posé sur une nuit bleue).
 */
object Glass {
    /** Haut et bas du fond : bleu presque noir. */
    val BaseTop = Color(0xFF040A1C)
    val BaseBottom = Color(0xFF081534)

    /** Halos lents et discrets qui donnent de la profondeur au fond, sans l'éclaircir. */
    val Glows = listOf(
        Color(0xFF1D4ED8).copy(alpha = 0.34f),
        Color(0xFF4338CA).copy(alpha = 0.24f),
        Color(0xFF0EA5E9).copy(alpha = 0.10f),
        Color(0xFF1E3A8A).copy(alpha = 0.40f),
    )

    /** Cartes : un voile blanc léger sur le fond flouté. */
    val Surface = Color.White.copy(alpha = 0.075f)
    val SurfaceStrong = Color.White.copy(alpha = 0.11f)

    /** Barre d'onglets et boutons flottants, un cran plus clairs que les cartes. */
    val Floating = Color.White.copy(alpha = 0.10f)
    val Indicator = Color.White.copy(alpha = 0.16f)

    /** Bouton principal : bleu plein, texte blanc (contraste 5,2:1). */
    val Accent = Color(0xFF2563EB)

    val CornerRadius: Dp = 18.dp
}

private data class Glow(
    val x: Float,
    val y: Float,
    val radius: Float,
    val driftX: Float,
    val driftY: Float,
    val phase: Float,
)

private val glows = listOf(
    Glow(0.10f, 0.08f, 0.70f, 0.08f, 0.05f, 0f),
    Glow(0.95f, 0.35f, 0.60f, 0.06f, 0.08f, 1.7f),
    Glow(0.25f, 0.70f, 0.65f, 0.10f, 0.06f, 3.1f),
    Glow(0.80f, 0.95f, 0.55f, 0.07f, 0.05f, 4.4f),
)

/**
 * Fond bleu nuit, avec quelques halos qui dérivent très lentement (un tour en 60 secondes).
 * Sans un fond qui varie un peu, le flou du verre ne se verrait pas. L'animation suit le
 * réglage système d'échelle des animations (désactivée si les animations le sont).
 */
@Composable
fun LiquidBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "fond")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(60_000, easing = LinearEasing)),
        label = "dérive",
    )
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Glass.BaseTop, Glass.BaseBottom)))
        val unit = size.maxDimension
        glows.forEachIndexed { index, glow ->
            val color = Glass.Glows[index % Glass.Glows.size]
            val center = Offset(
                x = size.width * (glow.x + glow.driftX * sin(time + glow.phase)),
                y = size.height * (glow.y + glow.driftY * cos(time + glow.phase)),
            )
            val radius = unit * glow.radius
            drawCircle(
                brush = Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center, radius),
                radius = radius,
                center = center,
            )
        }
    }
}

/**
 * Surface de verre générique : flou et légère saturation de ce qui est derrière, voile clair,
 * aucune bordure. Le flou demande Android 12 ; en dessous, seul le voile reste.
 * [cornerRadius] nul donne une capsule.
 */
private fun Modifier.glass(backdrop: Backdrop, tint: Color, cornerRadius: Dp? = null): Modifier =
    drawBackdrop(
        backdrop = backdrop,
        shape = { if (cornerRadius == null) Capsule() else RoundedRectangle(cornerRadius) },
        effects = {
            vibrancy()
            blur(24.dp.toPx())
        },
        highlight = null,
        shadow = null,
        onDrawSurface = { drawRect(tint) },
    )

/** Carte de verre. Son contenu reprend les couleurs « sur verre ». */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdrop = LocalBackgroundBackdrop.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glass(backdrop, if (strong) Glass.SurfaceStrong else Glass.Surface, Glass.CornerRadius)
            .clip(RoundedCornerShape(Glass.CornerRadius))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
    ) {
        GlassSurfaceColors(onBackground = false) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
        }
    }
}

/** Bouton rond en verre, pour une icône seule (48 dp, la taille tactile minimale). */
@Composable
fun GlassIconButton(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    badge: Boolean = false,
) {
    val backdrop = LocalScreenBackdrop.current
    Box(
        modifier = modifier
            .size(48.dp)
            .then(
                if (accent) {
                    Modifier.clip(CircleShape).background(Glass.Accent)
                } else {
                    Modifier.glass(backdrop, Glass.Floating).clip(CircleShape)
                },
            )
            .clickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(22.dp))
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 11.dp, end = 11.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF5C6E)),
            )
        }
    }
}

/** Bouton principal en capsule : bleu plein, sans bordure. */
@Composable
fun GlassPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(Glass.Accent)
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

/** Un onglet de la barre. */
data class GlassTabItem(val title: String, val icon: Painter)

/**
 * Barre d'onglets flottante. L'indicateur glisse d'un onglet à l'autre en ligne droite
 * (250 ms, sans rebond) ; la barre et le reste de l'écran ne bougent pas.
 */
@Composable
fun GlassTabBar(
    items: List<GlassTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val backdrop = LocalScreenBackdrop.current
    val position by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "indicateur",
    )
    BoxWithConstraints(
        modifier = modifier
            .height(64.dp)
            .glass(backdrop, Glass.Floating)
            .padding(4.dp),
    ) {
        val tabWidth = maxWidth / items.size
        Box(
            Modifier
                .offset(x = tabWidth * position)
                .width(tabWidth)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(Glass.Indicator),
        )
        Row(Modifier.fillMaxSize()) {
            items.forEachIndexed { index, item ->
                GlassTab(item, selected = index == selectedIndex, onClick = { onSelect(index) })
            }
        }
    }
}

@Composable
private fun RowScope.GlassTab(item: GlassTabItem, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) Color.White else Color.White.copy(alpha = 0.72f)
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Tab
                this.selected = selected
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(item.icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Text(item.title, color = color, style = MaterialTheme.typography.labelSmall)
    }
}

/** Message en bas de l'écran, dans une capsule de verre. */
@Composable
fun GlassBanner(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val backdrop = LocalScreenBackdrop.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glass(backdrop, Glass.SurfaceStrong)
            .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Voile sous la barre d'état : le contenu qui remonte s'y fond progressivement au lieu de passer
 * sous l'heure et les icônes système.
 */
@Composable
fun StatusBarScrim(modifier: Modifier = Modifier, height: Dp) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        drawRect(Brush.verticalGradient(listOf(Glass.BaseTop, Glass.BaseTop.copy(alpha = 0f))))
    }
}
