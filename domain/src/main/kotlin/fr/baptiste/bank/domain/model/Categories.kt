package fr.baptiste.bank.domain.model

import fr.baptiste.bank.domain.bank.envelopeNameKey

/*
 * Catégories : le nom des anciens groupes. Une enveloppe est ce que l'on budgète et où l'on range
 * une dépense (Courses, Bar, Parking) ; sa catégorie (Nourriture, Transports) sert aux statistiques
 * et réunit plusieurs enveloppes. Une enveloppe sans catégorie choisie est dans « Autres ».
 * Stockage : la colonne group_name, inchangée ; NULL vaut « Autres ».
 */

const val DEFAULT_CATEGORY = "Autres"

/** Liste de départ, proposée à la création d'une enveloppe. */
val defaultCategories = listOf(
    "Nourriture",
    "Logement",
    "Transports",
    "Abonnements",
    "Loisirs",
    "Santé",
    "Shopping",
    "Cadeaux",
    "Voyages",
    DEFAULT_CATEGORY,
)

/** Catégorie de l'enveloppe : son groupe, ou « Autres ». */
val Envelope.category: String get() = group?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_CATEGORY

/** Valeur à stocker pour une catégorie choisie : « Autres » (ou vide) redevient NULL. */
fun storedCategory(category: String): String? =
    category.trim().takeIf { it.isNotEmpty() && envelopeNameKey(it) != envelopeNameKey(DEFAULT_CATEGORY) }

/**
 * Catégories à proposer : la liste de départ, puis celles que j'ai créées (sans doublon à la casse
 * et aux accents près), « Autres » en dernier.
 */
fun categoryChoices(used: Collection<String>): List<String> {
    val known = defaultCategories.map(::envelopeNameKey).toSet()
    val mine = used.map { it.trim() }
        .filter { it.isNotEmpty() && envelopeNameKey(it) !in known }
        .distinctBy(::envelopeNameKey)
        .sorted()
    return defaultCategories.dropLast(1) + mine + DEFAULT_CATEGORY
}

private val categoryHints: List<Pair<String, List<String>>> = listOf(
    "Nourriture" to listOf("course", "aliment", "supermarche", "boulang", "repas", "cantine", "nourriture"),
    "Logement" to listOf("loyer", "electricite", "edf", "gaz", "eau", "assurance hab", "charges", "credit immo", "pret immo", "maison"),
    "Transports" to listOf("essence", "carburant", "parking", "peage", "train", "sncf", "metro", "bus", "voiture", "velo", "transport", "navigo"),
    "Abonnements" to listOf("netflix", "spotify", "deezer", "abonnement", "telephone", "forfait", "internet", "box", "disney", "prime"),
    "Loisirs" to listOf("sortie", "bar", "restaurant", "resto", "cinema", "concert", "sport", "salle", "jeu", "loisir"),
    "Santé" to listOf("sante", "pharmacie", "medecin", "mutuelle", "dentiste", "coiffeur"),
    "Cadeaux" to listOf("cadeau", "noel", "anniversaire"),
    "Voyages" to listOf("vacances", "voyage", "hotel", "avion", "week-end"),
    "Shopping" to listOf("vetement", "shopping", "habit", "chaussure"),
)

/**
 * Catégorie suggérée d'après le nom de l'enveloppe, ou null si rien ne la reconnaît. Un indice doit
 * commencer un mot du nom (« eau » reconnaît « Eau », pas « Cadeaux »).
 */
fun suggestCategory(envelopeName: String): String? {
    val key = envelopeNameKey(envelopeName).replace(Regex("""[^A-Z0-9 ]"""), " ")
    if (key.isBlank()) return null
    val padded = " " + key.replace(Regex("""\s+"""), " ").trim()
    return categoryHints.firstOrNull { (_, hints) ->
        hints.any { padded.contains(" " + envelopeNameKey(it).replace(Regex("""[^A-Z0-9 ]"""), " ")) }
    }?.first
}
