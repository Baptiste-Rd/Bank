package fr.baptiste.bank.domain.history

/*
 * Annuler sans écraser : une entrée de l'historique ne s'annule que si aucune entrée plus récente,
 * encore active, ne touche les mêmes éléments. Exemple : « Courses modifiée » (300 → 350), puis
 * « Courses → Nourriture » ; annuler la première remettrait Courses à 300 € et dans son ancienne
 * catégorie, défaisant le déplacement sans le dire. Elle reste donc bloquée tant que la seconde
 * est active ; annuler la seconde d'abord la débloque.
 */

/**
 * Une entrée de l'historique : son identifiant, si elle est déjà annulée, ce qu'elle change
 * ([touched]), ce qu'elle crée ([creates], une enveloppe) et ce qu'elle utilise sans le changer
 * ([uses] : l'enveloppe où elle range une opération, une saisie, un ajout ou une règle).
 */
data class HistoryEntry(
    val id: Long,
    val undone: Boolean,
    val touched: Set<String>,
    val creates: Set<String> = emptySet(),
    val uses: Set<String> = emptySet(),
)

/**
 * Entrées qui ne peuvent pas s'annuler maintenant. [entries] : de la plus récente à la plus
 * ancienne. Une entrée est bloquée si une entrée plus récente, encore active, change la même chose,
 * ou utilise ce qu'elle a créé (annuler la création d'une enveloppe où l'on a rangé depuis
 * effacerait ce rangement). Utiliser la même enveloppe ne bloque pas : deux tris dans Courses
 * s'annulent dans n'importe quel ordre. Une entrée annulée ne bloque rien.
 */
fun blockedEntries(entries: List<HistoryEntry>): Set<Long> {
    val blocked = mutableSetOf<Long>()
    val touchedLater = mutableSetOf<String>()
    val usedLater = mutableSetOf<String>()
    for (entry in entries) {
        if (!entry.undone && (entry.touched.any { it in touchedLater } || entry.creates.any { it in usedLater })) blocked += entry.id
        if (!entry.undone) {
            touchedLater += entry.touched
            usedLater += entry.uses
        }
    }
    return blocked
}
