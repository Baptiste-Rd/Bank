package fr.baptiste.bank.ui.glass

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale

/*
 * Réglages du verre, modifiables depuis le menu développeur (⋮ > Réglages du verre).
 *
 * Les valeurs par défaut sont celles validées jusqu'ici : sans réglage enregistré, l'interface est
 * exactement la même qu'avant. Les réglages sont enregistrés comme une ligne « clé=valeur;... »,
 * que le menu sait aussi copier : il suffit de la transmettre pour en faire les nouvelles valeurs
 * par défaut. Une clé inconnue ou absente reprend sa valeur par défaut.
 */

/** Réglages du verre. Les épaisseurs sont en dp, les opacités entre 0 et 1, la teinte en degrés. */
data class GlassTuning(
    /** Voile blanc des cartes, et des cartes mises en avant. */
    val cardAlpha: Float = 0.075f,
    val cardStrongAlpha: Float = 0.11f,
    /** Voile des boutons et de la barre flottante. */
    val floatingAlpha: Float = 0.10f,
    /** Voile de l'indicateur de la barre d'onglets. */
    val indicatorAlpha: Float = 0.12f,
    /** Couleur du voile : teinte (0 à 360) et saturation (0 = blanc). */
    val tintHue: Float = 220f,
    val tintSaturation: Float = 0f,
    /** Opacité de la teinte bleue du bouton principal. */
    val accentAlpha: Float = 0.72f,
    /** Cartes : flou, largeur de la bande courbée depuis le bord, amplitude de la déviation. */
    val cardBlur: Float = 12f,
    val cardLensHeight: Float = 14f,
    val cardLensAmount: Float = 28f,
    /** Boutons (⋮, +, bouton principal). */
    val buttonBlur: Float = 2f,
    val buttonLensHeight: Float = 16f,
    val buttonLensAmount: Float = 28f,
    /** Barre d'onglets et messages. */
    val barBlur: Float = 2f,
    val barLensHeight: Float = 21f,
    val barLensAmount: Float = 28f,
    /** Indicateur de l'onglet choisi. */
    val indicatorLensHeight: Float = 14f,
    val indicatorLensAmount: Float = 24f,
    /** Effets : aberration chromatique (irisation des bords), saturation de ce qui est derrière. */
    val chromaticAberration: Boolean = true,
    val vibrancy: Boolean = true,
    /** Arrondi des cartes. */
    val cornerRadius: Float = 18f,
    /** Intensité des halos du fond (1 = normal). */
    val glowStrength: Float = 1f,
    /** Ombre sous les icônes et libellés de la barre. */
    val labelShadowAlpha: Float = 0.55f,
) {
    private val tint: Color get() = Color.hsv(tintHue.coerceIn(0f, 360f), tintSaturation.coerceIn(0f, 1f), 1f)

    fun surface(strong: Boolean): Color = tint.copy(alpha = if (strong) cardStrongAlpha else cardAlpha)
    val floating: Color get() = tint.copy(alpha = floatingAlpha)
    val bannerSurface: Color get() = tint.copy(alpha = cardStrongAlpha)
    val indicator: Color get() = tint.copy(alpha = indicatorAlpha)
    val corner: Dp get() = cornerRadius.dp
    val labelShadow: Shadow
        get() = Shadow(color = Color.Black.copy(alpha = labelShadowAlpha), offset = Offset(0f, 1f), blurRadius = 6f)

    /** Une ligne « clé=valeur;... », lisible et facile à transmettre. */
    fun encode(): String = fields().joinToString(";") { (key, value) -> "$key=$value" }

    private fun fields(): List<Pair<String, String>> = listOf(
        "cardAlpha" to cardAlpha.f(),
        "cardStrongAlpha" to cardStrongAlpha.f(),
        "floatingAlpha" to floatingAlpha.f(),
        "indicatorAlpha" to indicatorAlpha.f(),
        "tintHue" to tintHue.f(),
        "tintSaturation" to tintSaturation.f(),
        "accentAlpha" to accentAlpha.f(),
        "cardBlur" to cardBlur.f(),
        "cardLensHeight" to cardLensHeight.f(),
        "cardLensAmount" to cardLensAmount.f(),
        "buttonBlur" to buttonBlur.f(),
        "buttonLensHeight" to buttonLensHeight.f(),
        "buttonLensAmount" to buttonLensAmount.f(),
        "barBlur" to barBlur.f(),
        "barLensHeight" to barLensHeight.f(),
        "barLensAmount" to barLensAmount.f(),
        "indicatorLensHeight" to indicatorLensHeight.f(),
        "indicatorLensAmount" to indicatorLensAmount.f(),
        "chromaticAberration" to chromaticAberration.toString(),
        "vibrancy" to vibrancy.toString(),
        "cornerRadius" to cornerRadius.f(),
        "glowStrength" to glowStrength.f(),
        "labelShadowAlpha" to labelShadowAlpha.f(),
    )

    companion object {
        val Default = GlassTuning()

        fun decode(text: String?): GlassTuning {
            if (text.isNullOrBlank()) return Default
            val values = text.split(';').mapNotNull { part ->
                val index = part.indexOf('=')
                if (index <= 0) null else part.substring(0, index).trim() to part.substring(index + 1).trim()
            }.toMap()
            fun float(key: String, default: Float) = values[key]?.toFloatOrNull() ?: default
            fun bool(key: String, default: Boolean) = values[key]?.toBooleanStrictOrNull() ?: default
            val d = Default
            return GlassTuning(
                cardAlpha = float("cardAlpha", d.cardAlpha),
                cardStrongAlpha = float("cardStrongAlpha", d.cardStrongAlpha),
                floatingAlpha = float("floatingAlpha", d.floatingAlpha),
                indicatorAlpha = float("indicatorAlpha", d.indicatorAlpha),
                tintHue = float("tintHue", d.tintHue),
                tintSaturation = float("tintSaturation", d.tintSaturation),
                accentAlpha = float("accentAlpha", d.accentAlpha),
                cardBlur = float("cardBlur", d.cardBlur),
                cardLensHeight = float("cardLensHeight", d.cardLensHeight),
                cardLensAmount = float("cardLensAmount", d.cardLensAmount),
                buttonBlur = float("buttonBlur", d.buttonBlur),
                buttonLensHeight = float("buttonLensHeight", d.buttonLensHeight),
                buttonLensAmount = float("buttonLensAmount", d.buttonLensAmount),
                barBlur = float("barBlur", d.barBlur),
                barLensHeight = float("barLensHeight", d.barLensHeight),
                barLensAmount = float("barLensAmount", d.barLensAmount),
                indicatorLensHeight = float("indicatorLensHeight", d.indicatorLensHeight),
                indicatorLensAmount = float("indicatorLensAmount", d.indicatorLensAmount),
                chromaticAberration = bool("chromaticAberration", d.chromaticAberration),
                vibrancy = bool("vibrancy", d.vibrancy),
                cornerRadius = float("cornerRadius", d.cornerRadius),
                glowStrength = float("glowStrength", d.glowStrength),
                labelShadowAlpha = float("labelShadowAlpha", d.labelShadowAlpha),
            )
        }
    }
}

private fun Float.f(): String = String.format(Locale.ROOT, "%.3f", this).trimEnd('0').trimEnd('.')

/** Réglages du verre en cours : ceux enregistrés, ou ceux en cours d'essai dans le menu développeur. */
val LocalGlassTuning = compositionLocalOf { GlassTuning.Default }
