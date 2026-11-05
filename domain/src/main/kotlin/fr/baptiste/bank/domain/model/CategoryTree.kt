package fr.baptiste.bank.domain.model

import fr.baptiste.bank.domain.bank.envelopeNameKey

/*
 * Catégories imbriquées : une catégorie peut être rangée dans une autre (« Courses » dans
 * « Nourriture »), sans limite de profondeur. Les dépenses et les budgets remontent vers le haut.
 * « Autres » reste toujours une catégorie principale. Les noms se comparent sans casse ni accents.
 */

/** Une catégorie et sa catégorie mère (null : catégorie principale). */
data class CategoryNode(val name: String, val parent: String? = null)

/** Mère de chaque catégorie, par clé de nom ; une catégorie principale n'y figure pas. */
fun parentsOf(nodes: Collection<CategoryNode>): Map<String, String> =
    nodes.mapNotNull { node -> node.parent?.trim()?.takeIf { it.isNotEmpty() }?.let { envelopeNameKey(node.name) to it } }.toMap()

/** Les catégories au-dessus de [name], de la mère directe à la principale (sans boucle). */
fun ancestorsOf(name: String, parents: Map<String, String>): List<String> {
    val seen = mutableSetOf(envelopeNameKey(name))
    val chain = mutableListOf<String>()
    var current = parents[envelopeNameKey(name)]
    while (current != null && seen.add(envelopeNameKey(current))) {
        chain += current
        current = parents[envelopeNameKey(current)]
    }
    return chain
}

/** « Nourriture › Courses » : le chemin d'une catégorie. */
fun categoryPath(name: String, parents: Map<String, String>): String =
    (ancestorsOf(name, parents).reversed() + name).joinToString(" › ")

/**
 * Vrai si [name] peut être rangée dans [newParent] : pas dans elle-même, pas dans l'une de ses
 * sous-catégories (une boucle), et « Autres » ne se range nulle part.
 */
fun canMoveCategory(name: String, newParent: String?, parents: Map<String, String>): Boolean {
    val key = envelopeNameKey(name)
    if (key == envelopeNameKey(DEFAULT_CATEGORY)) return newParent == null
    if (newParent == null) return true
    val target = envelopeNameKey(newParent)
    if (target == key || target == envelopeNameKey(DEFAULT_CATEGORY)) return false
    return ancestorsOf(newParent, parents).none { envelopeNameKey(it) == key }
}

/** La catégorie principale de [name] (elle-même si elle n'a pas de mère). */
fun rootOf(name: String, parents: Map<String, String>): String = ancestorsOf(name, parents).lastOrNull() ?: name
