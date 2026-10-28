package fr.baptiste.bank.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

/*
 * Menu développeur : régler le verre en le regardant.
 *
 * Le panneau occupe le bas de l'écran ; au-dessus, l'app reste vivante (on peut défiler, changer
 * d'onglet) et suit chaque curseur en direct. Rien n'est enregistré tant qu'on n'a pas touché
 * « Enregistrer » ; « Origine » revient aux valeurs validées. « Copier » met les réglages dans le
 * presse-papiers, pour les transmettre et en faire les nouvelles valeurs par défaut.
 */

/** Préréglages de couleur du voile : teinte et saturation. */
private val tintPresets = listOf(
    Triple("Blanc", 220f, 0f),
    Triple("Bleu", 220f, 0.18f),
    Triple("Lavande", 255f, 0.14f),
    Triple("Glace", 190f, 0.16f),
    Triple("Chaud", 35f, 0.12f),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlassTuningPanel(
    tuning: GlassTuning,
    onChange: (GlassTuning) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xF20B1328), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Réglages du verre", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text("Annuler") }
            TextButton(onClick = onSave) { Text("Enregistrer") }
        }
        Row(Modifier.padding(horizontal = 12.dp)) {
            TextButton(onClick = { onChange(GlassTuning.Default) }) { Text("Origine") }
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(tuning.encode()))
                onNotify("Réglages copiés.")
            }) { Text("Copier") }
            TextButton(onClick = {
                val pasted = clipboard.getText()?.text
                if (pasted.isNullOrBlank() || '=' !in pasted) {
                    onNotify("Rien à coller : copie d'abord une ligne de réglages.")
                } else {
                    onChange(GlassTuning.decode(pasted))
                }
            }) { Text("Coller") }
        }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Section("Couleur")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tintPresets.forEach { (name, hue, saturation) ->
                    FilterChip(
                        selected = tuning.tintHue == hue && tuning.tintSaturation == saturation,
                        onClick = { onChange(tuning.copy(tintHue = hue, tintSaturation = saturation)) },
                        label = { Text(name) },
                    )
                }
            }
            Setting("Teinte", tuning.tintHue, 0f..360f, "°") { onChange(tuning.copy(tintHue = it)) }
            Setting("Saturation", tuning.tintSaturation, 0f..0.6f) { onChange(tuning.copy(tintSaturation = it)) }
            Setting("Bouton principal (bleu)", tuning.accentAlpha, 0.2f..1f) { onChange(tuning.copy(accentAlpha = it)) }
            Setting("Halos du fond", tuning.glowStrength, 0f..2f) { onChange(tuning.copy(glowStrength = it)) }

            Section("Transparence (voile)")
            Setting("Cartes", tuning.cardAlpha, 0f..0.3f) { onChange(tuning.copy(cardAlpha = it)) }
            Setting("Cartes en avant", tuning.cardStrongAlpha, 0f..0.3f) { onChange(tuning.copy(cardStrongAlpha = it)) }
            Setting("Boutons et barre", tuning.floatingAlpha, 0f..0.3f) { onChange(tuning.copy(floatingAlpha = it)) }
            Setting("Indicateur d'onglet", tuning.indicatorAlpha, 0f..0.4f) { onChange(tuning.copy(indicatorAlpha = it)) }

            Section("Effets")
            Toggle("Aberration chromatique", tuning.chromaticAberration) { onChange(tuning.copy(chromaticAberration = it)) }
            Toggle("Saturation du fond (vibrance)", tuning.vibrancy) { onChange(tuning.copy(vibrancy = it)) }

            Section("Cartes")
            Setting("Flou", tuning.cardBlur, 0f..40f, " dp") { onChange(tuning.copy(cardBlur = it)) }
            Setting("Lentille : largeur", tuning.cardLensHeight, 0f..40f, " dp") { onChange(tuning.copy(cardLensHeight = it)) }
            Setting("Lentille : déviation", tuning.cardLensAmount, 0f..64f, " dp") { onChange(tuning.copy(cardLensAmount = it)) }
            Setting("Arrondi", tuning.cornerRadius, 0f..36f, " dp") { onChange(tuning.copy(cornerRadius = it)) }

            Section("Boutons (⋮, +, bouton principal)")
            Setting("Flou", tuning.buttonBlur, 0f..20f, " dp") { onChange(tuning.copy(buttonBlur = it)) }
            Setting("Lentille : largeur", tuning.buttonLensHeight, 0f..24f, " dp") { onChange(tuning.copy(buttonLensHeight = it)) }
            Setting("Lentille : déviation", tuning.buttonLensAmount, 0f..64f, " dp") { onChange(tuning.copy(buttonLensAmount = it)) }

            Section("Barre d'onglets et messages")
            Setting("Flou", tuning.barBlur, 0f..20f, " dp") { onChange(tuning.copy(barBlur = it)) }
            Setting("Lentille : largeur", tuning.barLensHeight, 0f..32f, " dp") { onChange(tuning.copy(barLensHeight = it)) }
            Setting("Lentille : déviation", tuning.barLensAmount, 0f..64f, " dp") { onChange(tuning.copy(barLensAmount = it)) }
            Setting("Indicateur : largeur", tuning.indicatorLensHeight, 0f..28f, " dp") {
                onChange(tuning.copy(indicatorLensHeight = it))
            }
            Setting("Indicateur : déviation", tuning.indicatorLensAmount, 0f..48f, " dp") {
                onChange(tuning.copy(indicatorLensAmount = it))
            }
            Setting("Ombre des libellés", tuning.labelShadowAlpha, 0f..1f) { onChange(tuning.copy(labelShadowAlpha = it)) }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
    )
}

/** Un curseur : libellé, valeur, et le curseur dessous. */
@Composable
private fun Setting(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String = "",
    onChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            val shown = if (range.endInclusive > 2f) {
                String.format(Locale.FRANCE, "%.0f", value)
            } else {
                String.format(Locale.FRANCE, "%.3f", value)
            }
            Text(
                shown + unit,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Les épaisseurs et la teinte se règlent au point près ; les opacités au millième.
        val whole = range.endInclusive > 2f
        Slider(
            value = value.coerceIn(range),
            onValueChange = { onChange(if (whole) it.roundToInt().toFloat() else it) },
            valueRange = range,
        )
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
