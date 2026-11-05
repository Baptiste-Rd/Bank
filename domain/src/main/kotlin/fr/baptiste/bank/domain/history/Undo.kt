package fr.baptiste.bank.domain.history

/*
 * Annuler sans écraser : une entrée de l'historique ne s'annule que si aucune entrée plus récente,
 * encore active, ne touche les mêmes éléments. Exemple : « Courses modifiée » (300 → 350), puis
 * « Courses → Nourriture » ; annuler la première remettrait Courses à 300 € et dans son ancienne
 * catégorie, défaisant le déplacement sans le dire. Elle reste donc bloquée tant que la seconde
 * est active ; annuler la seconde d'abord la débloque.
 */

/** Une entrée de l'historique : son identifiant, si elle est déjà annulée, et ce qu'elle touche. */
data class HistoryEntry(val id: Long, val undone: Boolean, val touched: Set<String>)

/**
 * Entrées qui ne peuvent pas s'annuler maintenant. [entries] : de la plus récente à la plus
 * ancienne. Une entrée annulée ne bloque rien.
 */
fun blockedEntries(entries: List<HistoryEntry>): Set<Long> {
    val blocked = mutableSetOf<Long>()
    val touchedLater = mutableSetOf<String>()
    for (entry in entries) {
        if (!entry.undone && entry.touched.any { it in touchedLater }) blocked += entry.id
        if (!entry.undone) touchedLater += entry.touched
    }
    return blocked
}
