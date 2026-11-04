package fr.baptiste.bank.ui.envelopes

import androidx.compose.ui.graphics.Color
import fr.baptiste.bank.domain.model.EnvelopeType

/**
 * Les quatre familles montrées à l'écran, à la place des six types du calcul (comme Monarch :
 * fixe, flexible, non mensuel). Les types restent dans les données et dans le formulaire ; ils
 * se lisent en détail sous le nom de chaque enveloppe.
 */
enum class EnvelopeFamily(val label: String, val color: Color) {
    // Couleurs du prototype testé (barre du plan du mois et légende).
    DAILY("Au quotidien", Color(0xFF7DB4FF)),
    FIXED("Charges fixes", Color(0xFFC9B47C)),
    PROVISIONS("Rares et ponctuels", Color(0xFFF28DB2)),
    SAVINGS("Épargne", Color(0xFF6EE7A0)),
}

val EnvelopeType.family: EnvelopeFamily
    get() = when (this) {
        EnvelopeType.DAILY -> EnvelopeFamily.DAILY
        EnvelopeType.FIXED -> EnvelopeFamily.FIXED
        EnvelopeType.RECURRING, EnvelopeType.ANNUAL, EnvelopeType.PLANNED -> EnvelopeFamily.PROVISIONS
        EnvelopeType.SAVINGS -> EnvelopeFamily.SAVINGS
    }

