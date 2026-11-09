package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeTree
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.model.TreeNode
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.period.Frequency
import fr.baptiste.bank.domain.money.sumOfMoney
import java.time.YearMonth

/*
 * L'arbre dans les calculs. Un parent couvre ses enfants (voir EnvelopeTree) : pour le plan du mois,
 * il ne réserve que sa part propre, ce que ses enfants ne couvrent pas. Le montant saisi reste
 * enregistré ; seule la période en cours et les suivantes suivent la part propre, le passé est figé.
 * Budget affiche le budget couvert ; le calcul du mois ne voit que les parts propres, si bien qu'un
 * euro n'est jamais réservé deux fois.
 */

/** Un groupe : une enveloppe sans montant, qui ne sert qu'à en contenir d'autres. */
data class GroupNode(val id: Long, val name: String, val parentId: Long?, val position: Int = 0)

/** Enveloppes comptées dans l'arbre de [month] : une enveloppe arrêtée ce mois-ci réserve encore sa part. */
private fun Envelope.countsIn(month: YearMonth): Boolean = (!archived || retiredOn != null) && existedDuring(month)

/** L'arbre de [month], en parts du mois (enveloppes brutes, avec leur montant saisi). */
fun treeOf(envelopes: List<Envelope>, groups: List<GroupNode>, month: YearMonth, since: YearMonth?): EnvelopeTree = EnvelopeTree(
    envelopes.filter { it.countsIn(month) }.map {
        // Une somme mise à part ne pèse pas dans l'arbre, même le mois où elle est prise sur le salaire.
        TreeNode(it.id, it.parentId, it.position, if (it.type == EnvelopeType.LUMP) Money.ZERO else it.monthlyBudget(month, since))
    } +
        groups.map { TreeNode(it.id, it.parentId, it.position) },
)

/**
 * Ce que couvrent les enfants de chaque parent, exprimé pour sa période à lui. Enregistré à part du
 * montant saisi : la part propre d'un parent est son montant moins cette couverture, sans jamais
 * toucher à ce qui a été saisi, reporté ou corrigé.
 */
fun childrenAmounts(envelopes: List<Envelope>, groups: List<GroupNode>, month: YearMonth, since: YearMonth?): Map<Long, Money> {
    val tree = treeOf(envelopes, groups, month, since)
    return envelopes.filter { it.countsIn(month) && it.type != EnvelopeType.LUMP && tree.children(it.id).isNotEmpty() }.associate { parent ->
        val children = tree.children(parent.id).sumOfMoney { tree.covered(it.id) }
        // Même proportion que la part du mois : une provision commencée tard réserve plus d'un douzième.
        val total = parent.amountIn(parent.periodContaining(month.atEndOfMonth()))
        val share = parent.monthlyBudget(month, since)
        val covered = if (share.isPositive) Money(total.cents * children.cents / share.cents) else children.convert(from = Frequency.MONTHLY, to = parent.frequency)
        parent.id to if (covered > total) total else covered
    }
}

private fun Money.minusAtMost(other: Money): Money = if (other > this) Money.ZERO else this - other

/** Les enveloppes telles que les calculs doivent les voir : les parents ramenés à leur part propre. */
fun List<Envelope>.withOwnAmounts(groups: List<GroupNode>, month: YearMonth, since: YearMonth?): List<Envelope> {
    val children = childrenAmounts(this, groups, month, since)
    if (children.isEmpty() && none { envelope -> envelope.periods.any { it.covered.isPositive } }) return this
    return map { envelope ->
        val covered = children[envelope.id]
        envelope.copy(
            amount = covered?.let(envelope.amount::minusAtMost) ?: envelope.amount,
            firstAmount = if (covered != null) null else envelope.firstAmount,
            periods = envelope.periods.map { it.copy(amount = it.amount.minusAtMost(it.covered), covered = Money.ZERO) },
        )
    }
}

/** Un budget qui change parce qu'un parent couvre plus, ou moins. */
data class CoverageChange(val name: String, val before: Money, val after: Money, val own: Money = Money.ZERO)

/** Ce qu'un déplacement change : les parents touchés, et le plan du mois. */
data class TreeMoveResult(
    val label: String,
    val changes: List<CoverageChange>,
    val planDelta: Money,
    /** Ce que le déplacement change au « par jour », lu sur deux tableaux réels. */
    val perDayDelta: Money = Money.ZERO,
)

/**
 * Le message d'un déplacement : ce que devient l'argent. Un parent qui couvre plus dit aussi sa part
 * propre (« Essence passe à 320 €, dont 0 € pour elle »), et le plan dit ce qui revient à répartir.
 */
fun TreeMoveResult.message(summary: (Money) -> String): String = buildString {
    append(label)
    changes.forEach { append(" · ${it.name} passe à ${summary(it.after)}, dont ${summary(it.own)} pour elle") }
    when {
        planDelta.isNegative -> append(" · ${summary(-planDelta)} reviennent à répartir")
        planDelta.isPositive -> append(" · ${summary(planDelta)} de plus au budget")
    }
    if (perDayDelta != Money.ZERO) append(" · par jour ${if (perDayDelta.isNegative) "" else "+"}${summary(perDayDelta)}")
}

/** Compare deux états de l'arbre : les enveloppes dont le budget couvert a changé, le plan du mois. */
fun coverageChanges(label: String, before: EnvelopeTree, after: EnvelopeTree, names: Map<Long, String>): TreeMoveResult =
    TreeMoveResult(
        label = label,
        changes = names.mapNotNull { (id, name) ->
            val old = before.covered(id)
            val new = after.covered(id)
            // Un parent seulement : le budget d'une enveloppe sans enfant ne bouge pas avec l'arbre.
            CoverageChange(name, old, new, after.ownPart(id)).takeIf { old != new && after.children(id).isNotEmpty() }
        },
        planDelta = after.total - before.total,
    )

/** Une ligne de l'arbre de Budget : ce qui est prévu, sans aucune dépense. */
data class BudgetRow(
    val id: Long,
    val name: String,
    val depth: Int,
    val hasChildren: Boolean,
    /** Budget du mois, enfants couverts. */
    val covered: Money,
    /** Null pour un groupe. */
    val envelope: Envelope?,
    /** Nom de la branche du haut : elle donne sa couleur. */
    val rootName: String,
)

/** L'arbre de Budget à plat, sans ce que cachent les branches fermées (celles qui ne sont pas dans [open]). */
fun budgetRows(envelopes: List<Envelope>, groups: List<GroupNode>, month: YearMonth, since: YearMonth?, open: Set<Long>): List<BudgetRow> {
    val tree = treeOf(envelopes, groups, month, since)
    val byId = envelopes.associateBy { it.id }
    val names = groups.associate { it.id to it.name } + envelopes.associate { it.id to it.name }
    val rows = mutableListOf<BudgetRow>()
    // Une enveloppe arrêtée compte encore ce mois-ci, mais elle n'est plus proposée : elle est dans « Arrêtées ».
    fun shown(id: Long) = byId[id]?.archived != true
    fun walk(parent: Long?, depth: Int, root: String?) {
        tree.children(parent).forEach { node ->
            // Ce que contient une enveloppe arrêtée reste actif : il remonte à sa place.
            if (!shown(node.id)) {
                walk(node.id, depth, root)
                return@forEach
            }
            val name = names.getValue(node.id)
            val rootName = root ?: name
            rows += BudgetRow(node.id, name, depth, tree.descendants(node.id).any { shown(it.id) }, tree.covered(node.id), byId[node.id], rootName)
            if (node.id in open) walk(node.id, depth + 1, rootName)
        }
    }
    walk(null, 0, null)
    return rows
}

/** Le filtre de Dépenses : la catégorie, c'est-à-dire le type de dépense. */
enum class SpendFilter(val label: String) {
    ALL("Tout"),
    MONTHLY("Mensuel"),
    OCCASIONAL("Ponctuel"),
    SAVINGS("Épargne"),
    ;

    fun accepts(type: EnvelopeType): Boolean = when (this) {
        ALL -> true
        MONTHLY -> type == EnvelopeType.DAILY || type == EnvelopeType.FIXED
        OCCASIONAL -> type == EnvelopeType.RECURRING || type == EnvelopeType.ANNUAL || type == EnvelopeType.PLANNED || type == EnvelopeType.LUMP
        SAVINGS -> type == EnvelopeType.SAVINGS
    }
}

/** Une ligne de l'arbre de Dépenses. */
data class SpendingRow(
    val id: Long,
    val name: String,
    val depth: Int,
    val hasChildren: Boolean,
    /** Dépensé du mois sur toute la branche, filtre appliqué ; mis de côté avec le filtre Épargne. */
    val spent: Money,
    /** La ligne de l'enveloppe (reste, barre) ; null pour un groupe ou une enveloppe hors du mois. */
    val line: EnvelopeLine?,
    /** Null pour un groupe. */
    val envelope: Envelope?,
    val rootName: String,
)

/**
 * L'arbre de Dépenses à plat. Une branche sans enveloppe acceptée par [filter] n'apparaît pas ; son
 * total n'additionne que les enveloppes acceptées. [monthSpent] : dépensé du mois de chaque enveloppe.
 * [others] : enveloppes qui ont une dépense comptée ce mois-là sans figurer au tableau (dépense
 * d'avant leur départ, ponctuelle terminée) : l'arbre doit toujours redonner le total du mois.
 */
fun spendingRows(
    lines: List<EnvelopeLine>,
    others: List<Envelope>,
    monthSpent: Map<Long, Money>,
    groups: List<GroupNode>,
    filter: SpendFilter,
    open: Set<Long>,
): List<SpendingRow> {
    val byId = lines.associateBy { it.envelope.id }
    val extra = others.filter { it.id !in byId }
    val envelopes = lines.map { it.envelope } + extra
    val known = envelopes.associateBy { it.id }
    val tree = EnvelopeTree(
        envelopes.map { TreeNode(it.id, it.parentId, it.position) } + groups.map { TreeNode(it.id, it.parentId, it.position) },
    )
    val names = groups.associate { it.id to it.name } + envelopes.associate { it.id to it.name }
    val accepted = envelopes.filter { filter.accepts(it.type) }.map { it.id }.toSet()
    val values = if (filter == SpendFilter.SAVINGS) lines.associate { it.envelope.id to it.monthlyShare } else monthSpent
    val counted = values.filterKeys { it in accepted }
    fun shown(id: Long) = id in accepted || tree.descendants(id).any { it.id in accepted }
    val rows = mutableListOf<SpendingRow>()
    fun walk(parent: Long?, depth: Int, root: String?) {
        tree.children(parent).filter { shown(it.id) }.forEach { node ->
            val name = names.getValue(node.id)
            val rootName = root ?: name
            val hasChildren = tree.children(node.id).any { shown(it.id) }
            rows += SpendingRow(node.id, name, depth, hasChildren, tree.branchTotal(node.id, counted), byId[node.id], known[node.id], rootName)
            if (node.id in open) walk(node.id, depth + 1, rootName)
        }
    }
    walk(null, 0, null)
    return rows
}
