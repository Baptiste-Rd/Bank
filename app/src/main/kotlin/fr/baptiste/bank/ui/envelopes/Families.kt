package fr.baptiste.bank.ui.envelopes

import androidx.compose.ui.graphics.Color
import fr.baptiste.bank.domain.budget.EnvelopeLine
import fr.baptiste.bank.domain.budget.convert
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.period.Frequency
import fr.baptiste.bank.ui.theme.envelopeTypeColor

/**
 * Les quatre familles montrées à l'écran, à la place des six types du calcul (comme Monarch :
 * fixe, flexible, non mensuel). Les types restent dans les données et dans le formulaire ; ils
 * se lisent en détail sous le nom de chaque enveloppe.
 */
enum class EnvelopeFamily(val label: String, val color: Color) {
    DAILY("Au quotidien", envelopeTypeColor(EnvelopeType.DAILY)),
    FIXED("Charges fixes", envelopeTypeColor(EnvelopeType.FIXED)),
    PROVISIONS("Provisions", envelopeTypeColor(EnvelopeType.ANNUAL)),
    SAVINGS("Épargne", envelopeTypeColor(EnvelopeType.SAVINGS)),
}

val EnvelopeType.family: EnvelopeFamily
    get() = when (this) {
        EnvelopeType.DAILY -> EnvelopeFamily.DAILY
        EnvelopeType.FIXED -> EnvelopeFamily.FIXED
        EnvelopeType.RECURRING, EnvelopeType.ANNUAL, EnvelopeType.PLANNED -> EnvelopeFamily.PROVISIONS
        EnvelopeType.SAVINGS -> EnvelopeFamily.SAVINGS
    }

/** Part mensuelle d'une enveloppe : ce qu'elle prend au salaire chaque mois. */
val EnvelopeLine.monthlyShare: Money get() = allocated.convert(envelope.frequency, Frequency.MONTHLY)
