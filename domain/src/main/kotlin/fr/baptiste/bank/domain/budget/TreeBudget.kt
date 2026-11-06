package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeTree
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.model.TreeNode
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.period.Frequency

/*
 * L'arbre dans les calculs. Un parent couvre ses enfants (voir EnvelopeTree) : pour le plan du mois,
 * il ne réserve que sa part propre, ce que ses enfants ne couvrent pas. Le montant saisi reste
 * enregistré ; seule la période en cours et les suivantes suivent la part propre, le passé est figé.
 * Budget affiche le budget couvert ; le calcul du mois ne voit que les parts propres, si bien qu'un
 * euro n'est jamais réservé deux fois.
 */

/** Un groupe : une enveloppe sans montant, qui ne sert qu'à en contenir d'autres. */
data class GroupNode(val id: Long, val name: String, val parentId: Long?, val position: Int = 0)

private fun Envelope.monthlyAmount(): Money = amount.convert(from = frequency, to = Frequency.MONTHLY)

/** L'arbre des enveloppes actives et des groupes, en montants du mois. */
fun treeOf(envelopes: List<Envelope>, groups: List<GroupNode>): EnvelopeTree = EnvelopeTree(
    envelopes.filter { !it.archived }.map { TreeNode(it.id, it.parentId, it.position, it.monthlyAmount()) } +
        groups.map { TreeNode(it.id, it.parentId, it.position) },
)

/** La part propre de chaque enveloppe qui en contient d'autres, exprimée pour sa période à elle. */
fun ownAmounts(envelopes: List<Envelope>, groups: List<GroupNode>): Map<Long, Money> {
    val tree = treeOf(envelopes, groups)
    return envelopes.filter { !it.archived && tree.children(it.id).isNotEmpty() }
        .associate { it.id to tree.ownPart(it.id).convert(from = Frequency.MONTHLY, to = it.frequency) }
}

/** Les enveloppes telles que le calcul du mois doit les voir : les parents ramenés à leur part propre. */
fun List<Envelope>.withOwnAmounts(groups: List<GroupNode>): List<Envelope> {
    val own = ownAmounts(this, groups)
    if (own.isEmpty()) return this
    return map { envelope -> own[envelope.id]?.let { envelope.copy(amount = it, firstAmount = null) } ?: envelope }
}

/** Un budget qui change parce qu'un parent couvre plus, ou moins. */
data class CoverageChange(val name: String, val before: Money, val after: Money)

/** Ce qu'un déplacement change : les parents touchés, et le plan du mois. */
data class TreeMoveResult(val label: String, val changes: List<CoverageChange>, val planDelta: Money)

/** Compare deux états de l'arbre : les enveloppes dont le budget couvert a changé, le plan du mois. */
fun coverageChanges(label: String, before: EnvelopeTree, after: EnvelopeTree, names: Map<Long, String>): TreeMoveResult =
    TreeMoveResult(
        label = label,
        changes = names.mapNotNull { (id, name) ->
            val old = before.covered(id)
            val new = after.covered(id)
            // Un parent seulement : le budget d'une enveloppe sans enfant ne bouge pas avec l'arbre.
            CoverageChange(name, old, new).takeIf { old != new && after.children(id).isNotEmpty() }
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
fun budgetRows(envelopes: List<Envelope>, groups: List<GroupNode>, open: Set<Long>): List<BudgetRow> {
    val active = envelopes.filter { !it.archived }
    val tree = treeOf(active, groups)
    val byId = active.associateBy { it.id }
    val names = groups.associate { it.id to it.name } + active.associate { it.id to it.name }
    val rows = mutableListOf<BudgetRow>()
    fun walk(parent: Long?, depth: Int, root: String?) {
        tree.children(parent).forEach { node ->
            val name = names.getValue(node.id)
            val rootName = root ?: name
            rows += BudgetRow(node.id, name, depth, tree.children(node.id).isNotEmpty(), tree.covered(node.id), byId[node.id], rootName)
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
        OCCASIONAL -> type == EnvelopeType.RECURRING || type == EnvelopeType.ANNUAL || type == EnvelopeType.PLANNED
        SAVINGS -> type == EnvelopeType.SAVINGS
    }
}

/** Une ligne de l'arbre de Dépenses. */
data class SpendingRow(
    val id: Long,
    val name: String,
    val depth: Int,
    val hasChildren: Boolean,
    /** Dépensé du mois sur toute la branche, filtre appliqué. */
    val spent: Money,
    /** La ligne de l'enveloppe (reste, barre) ; null pour un groupe. */
    val line: EnvelopeLine?,
    val rootName: String,
)

/**
 * L'arbre de Dépenses à plat. Une branche sans enveloppe acceptée par [filter] n'apparaît pas ; son
 * total n'additionne que les enveloppes acceptées. [monthSpent] : dépensé du mois de chaque enveloppe.
 */
fun spendingRows(
    lines: List<EnvelopeLine>,
    monthSpent: Map<Long, Money>,
    groups: List<GroupNode>,
    filter: SpendFilter,
    open: Set<Long>,
): List<SpendingRow> {
    val byId = lines.associateBy { it.envelope.id }
    val tree = EnvelopeTree(
        lines.map { TreeNode(it.envelope.id, it.envelope.parentId, it.envelope.position) } +
            groups.map { TreeNode(it.id, it.parentId, it.position) },
    )
    val names = groups.associate { it.id to it.name } + lines.associate { it.envelope.id to it.envelope.name }
    val accepted = lines.filter { filter.accepts(it.envelope.type) }.map { it.envelope.id }.toSet()
    val spent = monthSpent.filterKeys { it in accepted }
    fun shown(id: Long) = id in accepted || tree.descendants(id).any { it.id in accepted }
    val rows = mutableListOf<SpendingRow>()
    fun walk(parent: Long?, depth: Int, root: String?) {
        tree.children(parent).filter { shown(it.id) }.forEach { node ->
            val name = names.getValue(node.id)
            val rootName = root ?: name
            val hasChildren = tree.children(node.id).any { shown(it.id) }
            rows += SpendingRow(node.id, name, depth, hasChildren, tree.branchTotal(node.id, spent), byId[node.id], rootName)
            if (node.id in open) walk(node.id, depth + 1, rootName)
        }
    }
    walk(null, 0, null)
    return rows
}
