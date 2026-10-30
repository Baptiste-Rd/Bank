package fr.baptiste.bank.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.domain.budget.Dashboard
import fr.baptiste.bank.domain.budget.MonthDailySpending
import fr.baptiste.bank.domain.budget.PlanInput
import fr.baptiste.bank.domain.budget.PlanVerdict
import fr.baptiste.bank.domain.budget.average
import fr.baptiste.bank.domain.budget.simulatePlan
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.money.parseMoney
import fr.baptiste.bank.ui.components.BlurBehindWindow
import fr.baptiste.bank.ui.components.MoneyField
import fr.baptiste.bank.ui.components.StatusChip
import fr.baptiste.bank.ui.components.StatusKind
import fr.baptiste.bank.ui.components.toFieldText
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.format.label
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/*
 * « Essayer un plan » : un brouillon du plan du mois, dans un panneau qui se ferme sans rien
 * changer. Trois réglages (le projet, ses mois, l'épargne mensuelle) ; le quotidien est ce qui
 * reste, comparé à ce que je dépense d'habitude au quotidien. « Appliquer » crée l'achat prévu et
 * change l'épargne ; fermer ne change rien.
 */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlanSimulatorSheet(
    dashboard: Dashboard,
    /** Dépense du quotidien des derniers mois terminés (null : pas encore lue). */
    history: List<MonthDailySpending>,
    /** Une seule enveloppe d'épargne : l'épargne mensuelle peut être appliquée. */
    savingsEditable: Boolean,
    onApply: (name: String, project: Money, months: Int, monthlySavings: Money?) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = "",
    initialProject: Money? = null,
) {
    val currentSavings = dashboard.savings.target
    var name by rememberSaveable { mutableStateOf(initialName) }
    var projectText by rememberSaveable { mutableStateOf(initialProject?.toFieldText().orEmpty()) }
    var months by rememberSaveable { mutableStateOf(3) }
    var savingsText by rememberSaveable { mutableStateOf(currentSavings.toFieldText()) }
    // Un mois exceptionnel (vacances, Noël) peut fausser la moyenne : on peut l'exclure.
    var excluded by remember { mutableStateOf(emptySet<YearMonth>()) }

    val project = if (projectText.isBlank()) Money.ZERO else parseMoney(projectText)
    val savings = if (savingsText.isBlank()) Money.ZERO else parseMoney(savingsText)
    val kept = history.filter { it.month !in excluded }
    val reference = kept.average()
    val result = if (project != null && savings != null) {
        simulatePlan(dashboard, PlanInput(project, months, savings), currentSavings, reference)
    } else {
        null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        BlurBehindWindow()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Essayer un plan", style = MaterialTheme.typography.titleLarge)
            Text(
                "Un brouillon : ton budget ne change que si tu appliques.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 1. Le projet : un montant, un nom, et le nombre de mois pour le réunir.
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Projet") },
                placeholder = { Text("Ex. Voyage") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            MoneyField(value = projectText, onValueChange = { projectText = it }, label = "Montant à réunir")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "En $months mois, d'ici fin ${YearMonth.now().plusMonths(months - 1L).label}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { months = (months - 1).coerceAtLeast(1) }, enabled = months > 1) { Text("−") }
                TextButton(onClick = { months = (months + 1).coerceAtMost(36) }) { Text("+") }
            }

            // 2. L'épargne mensuelle.
            MoneyField(value = savingsText, onValueChange = { savingsText = it }, label = "Épargne par mois")
            if (!savingsEditable && savings != null && savings != currentSavings) {
                Text(
                    "Tu as plusieurs enveloppes d'épargne : ce réglage s'essaie, mais s'applique enveloppe par enveloppe.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 3. Le résultat, en direct.
            if (result != null) {
                fr.baptiste.bank.ui.components.RowDivider(0.dp)
                PlanLine("Projet", "${result.projectShare.formatted()} / mois")
                PlanLine("Épargne", "${savings?.formatted()} / mois", "aujourd'hui ${currentSavings.formatted()}")
                PlanLine(
                    "Quotidien",
                    "${result.dailyAfter.formatted()} / mois",
                    "soit ${result.perDayAfter.formatted()} par jour · aujourd'hui ${result.dailyBefore.formatted()}",
                    strong = true,
                )
                when (result.verdict) {
                    PlanVerdict.REALISTIC -> StatusChip("Réaliste", StatusKind.GOOD)
                    PlanVerdict.TIGHT -> {
                        StatusChip("Serré", StatusKind.WARNING)
                        Text(
                            "Il faudrait dépenser ${result.savePerDay?.formatted()} de moins par jour qu'à ton habitude.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    PlanVerdict.UNREALISTIC -> {
                        StatusChip("Peu réaliste", StatusKind.OVER)
                        Text(
                            result.tenableMonths?.let {
                                "Tenable en $it mois, d'ici fin ${YearMonth.now().plusMonths(it - 1L).label}, sans changer ta façon de dépenser."
                            } ?: "Pas tenable avec cette épargne : baisse-la, ou réduis le projet.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    PlanVerdict.UNKNOWN -> Text(
                        "Pas encore de mois terminé pour comparer à ta dépense habituelle.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // La référence du verdict, et les mois qui la font (touche un mois pour l'exclure).
                if (history.isNotEmpty()) {
                    Text(
                        reference?.let { "Comparé à ta dépense au quotidien : ${it.formatted()} par mois en moyenne." }
                            ?: "Aucun mois retenu pour comparer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        history.forEach { month ->
                            FilterChip(
                                selected = month.month !in excluded,
                                onClick = {
                                    excluded = if (month.month in excluded) excluded - month.month else excluded + month.month
                                },
                                label = {
                                    Text(
                                        "${month.month.month.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.FRANCE)} " +
                                            month.spent.formatted(),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            val canApply = result != null && project != null &&
                ((project.isPositive && name.isNotBlank()) || (savingsEditable && savings != currentSavings))
            Button(
                onClick = { if (project != null) onApply(name, project, months, savings?.takeIf { savingsEditable && it != currentSavings }) },
                enabled = canApply,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Appliquer") }
            if (project != null && project.isPositive && name.isBlank()) {
                Text(
                    "Donne un nom au projet pour l'appliquer : il deviendra un achat prévu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Une ligne du plan simulé : le libellé, la valeur avec le plan, et dessous la valeur d'aujourd'hui. */
@Composable
private fun PlanLine(label: String, value: String, detail: String? = null, strong: Boolean = false) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal),
            maxLines = 1,
            modifier = Modifier.padding(end = 12.dp),
        )
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(value, style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
