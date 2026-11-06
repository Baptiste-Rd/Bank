package fr.baptiste.bank.domain.model

import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.money.sumOfMoney

/*
 * L'arbre des enveloppes : une enveloppe peut en contenir d'autres, sans limite de profondeur.
 *
 * Un parent couvre ses enfants : son budget vaut le plus grand de son montant saisi et de la somme
 * des budgets de ses enfants. Rien n'est réécrit : le montant saisi reste enregistré, donc un parent
 * redescend seul quand un enfant baisse. Seule la part propre d'un parent (ce que ses enfants ne
 * couvrent pas) s'ajoute au plan : A à 300 € contenant B à 200 € contenant C à 400 € coûte 400 €.
 * Tous les montants d'un même arbre sont exprimés pour la même durée (le mois).
 */

/** Une enveloppe dans l'arbre. [amount] est son montant saisi, ramené à la durée commune. */
data class TreeNode(
    val id: Long,
    val parentId: Long?,
    val position: Int = 0,
    val amount: Money = Money.ZERO,
)

/** Où poser une enveloppe déplacée. */
sealed interface TreeDrop {
    /** L'enveloppe à côté de laquelle on pose ; null quand on pose dans un parent. */
    val siblingId: Long? get() = null

    /** À la fin de [parentId] ; null : à la fin du haut de l'arbre. */
    data class Into(val parentId: Long?) : TreeDrop

    /** Juste avant [siblingId], dans le même parent. */
    data class Before(override val siblingId: Long) : TreeDrop

    /** Juste après [siblingId], dans le même parent. */
    data class After(override val siblingId: Long) : TreeDrop
}

class EnvelopeTree(nodes: Collection<TreeNode>) {
    private val byId = nodes.associateBy { it.id }
    private val byParent = nodes
        .groupBy(::safeParent)
        .mapValues { (_, list) -> list.sortedWith(compareBy({ it.position }, { it.id })) }

    /** Son parent ; null s'il est inconnu (arrêté, supprimé) ou si l'enveloppe est dans une boucle : elle reste comptée, en haut. */
    private fun safeParent(node: TreeNode): Long? {
        val parentId = node.parentId?.takeIf { it in byId } ?: return null
        val seen = mutableSetOf<Long>()
        var current: Long? = parentId
        while (current != null && seen.add(current)) {
            if (current == node.id) return null
            current = byId[current]?.parentId
        }
        return parentId
    }

    /** Les enfants directs de [parentId], dans leur ordre ; null : le haut de l'arbre. */
    fun children(parentId: Long?): List<TreeNode> = byParent[parentId].orEmpty()

    /** Tout ce que contient [id], à tous les niveaux. Une boucle dans les données s'arrête d'elle-même. */
    fun descendants(id: Long): List<TreeNode> {
        val seen = mutableSetOf(id)
        val found = mutableListOf<TreeNode>()
        fun walk(parent: Long) {
            children(parent).forEach { child ->
                if (seen.add(child.id)) {
                    found += child
                    walk(child.id)
                }
            }
        }
        walk(id)
        return found
    }

    /** Budget de [id] : son montant saisi, ou la somme de ses enfants si elle est plus grande. */
    fun covered(id: Long): Money {
        val node = byId[id] ?: return Money.ZERO
        return maxOf(node.amount, children(id).sumOfMoney { covered(it.id) })
    }

    /** Ce que [id] ajoute au plan en plus de ses enfants. La somme sur tout l'arbre vaut le plan. */
    fun ownPart(id: Long): Money = covered(id) - children(id).sumOfMoney { covered(it.id) }

    /** Budget total de l'arbre : la somme des budgets du haut. */
    val total: Money get() = children(null).sumOfMoney { covered(it.id) }

    /** La somme de [values] sur [id] et tout ce qu'il contient : le total d'une branche. */
    fun branchTotal(id: Long, values: Map<Long, Money>): Money =
        (listOfNotNull(byId[id]) + descendants(id)).sumOfMoney { values[it.id] ?: Money.ZERO }

    /** Vrai si [id] peut aller dans [parentId] : ni dans elle-même, ni dans ce qu'elle contient. */
    fun canMoveInto(id: Long, parentId: Long?): Boolean =
        parentId == null || (parentId in byId && parentId != id && descendants(id).none { it.id == parentId })

    /**
     * Applique un déplacement : renvoie les enveloppes dont le parent ou la position change, ou null
     * s'il est impossible. Les enveloppes de la branche d'arrivée sont renumérotées de 0 à n.
     */
    fun move(id: Long, drop: TreeDrop): List<TreeNode>? {
        val moved = byId[id] ?: return null
        if (drop.siblingId == id) return null
        val parentId = when (drop) {
            is TreeDrop.Into -> drop.parentId
            is TreeDrop.Before -> if (drop.siblingId !in byId) return null else byParent.entries.firstOrNull { (_, list) -> list.any { it.id == drop.siblingId } }?.key
            is TreeDrop.After -> if (drop.siblingId !in byId) return null else byParent.entries.firstOrNull { (_, list) -> list.any { it.id == drop.siblingId } }?.key
        }
        if (!canMoveInto(id, parentId)) return null
        val siblings = children(parentId).filter { it.id != id }.toMutableList()
        val index = when (drop) {
            is TreeDrop.Into -> siblings.size
            is TreeDrop.Before -> siblings.indexOfFirst { it.id == drop.siblingId }
            is TreeDrop.After -> siblings.indexOfFirst { it.id == drop.siblingId } + 1
        }
        if (index < 0) return null
        siblings.add(index, moved)
        return siblings.mapIndexedNotNull { position, node ->
            node.copy(parentId = parentId, position = position).takeIf { it != node }
        }
    }
}
