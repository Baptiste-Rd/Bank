package fr.baptiste.bank.data

import fr.baptiste.bank.data.local.AllocationEntity
import fr.baptiste.bank.data.local.EnvelopeEntity
import fr.baptiste.bank.data.local.ImputationEntity
import fr.baptiste.bank.data.local.LabelAliasEntity
import fr.baptiste.bank.data.local.RuleEntity
import fr.baptiste.bank.data.local.TransactionEntity
import fr.baptiste.bank.domain.model.TransactionSource
import fr.baptiste.bank.domain.model.EnvelopeStatus
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.model.ImputationMode
import fr.baptiste.bank.domain.model.RuleMatchType
import fr.baptiste.bank.domain.period.Scale
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/*
 * Historique des changements. Chaque entrée garde de quoi revenir à l'état d'avant, sans toucher
 * à ce qui a changé depuis ailleurs :
 * - pour des opérations (tri, répartition, remise à trier, hors budget...), l'état de chacune
 *   avant le changement : ses parts dans les enveloppes, hors budget ou non, viré vers un compte ;
 * - les règles créées (à supprimer) et celles remplacées (à remettre) ;
 * - les transferts créés (à annuler, ils restent visibles barrés dans le journal) ;
 * - les enveloppes modifiées ou arrêtées (leur ligne et leurs périodes d'avant), ou créées (à supprimer) ;
 * - les dépenses saisies à la main (à supprimer), ou supprimées (à remettre) ;
 * - les dates d'achat corrigées, les noms d'opérations et les alias (à remettre) ;
 * - les mois de référence choisis (à remettre) ;
 * - les transferts annulés depuis « Ajouts et retraits » (à rétablir).
 *
 * Une entrée ne s'annule que si aucune entrée plus récente, encore active, ne touche les mêmes
 * éléments ([JournalPayload.touched]) : sinon l'annuler écraserait ce qui a changé depuis.
 */

/** Préfixe des éléments utilisés (et non changés) par une entrée, dans [JournalPayload.extraTouched]. */
const val USE_PREFIX = "use:"

/** Clé d'une règle ou d'un alias dans l'Historique : le motif normalisé (voir normalizeLabel). */
fun ruleKey(pattern: String) = "rule:" + fr.baptiste.bank.domain.bank.normalizeLabel(pattern)
fun aliasKey(pattern: String) = "alias:" + fr.baptiste.bank.domain.bank.normalizeLabel(pattern)

/** Une catégorie et sa mère, avant un changement ; [id] est absent des entrées d'avant la v18. */
data class CategoryState(val name: String, val parent: String?, val id: Long? = null, val position: Int = 0, val parentId: Long? = null)

/** Une saisie manuelle supprimée : de quoi la remettre telle quelle. */
data class ManualState(val transaction: TransactionEntity, val imputations: List<ImputationEntity>)

/** Le jour d'une opération avant une correction de date. */
data class DateState(val transactionId: Long, val date: LocalDate)

/** Le nom choisi d'une opération avant un renommage (null : le libellé de la banque). */
data class AliasState(val transactionId: Long, val alias: String?)

/** Un mois de référence avant un changement (null : le choix par défaut). */
data class MonthCountState(val month: java.time.YearMonth, val counted: Boolean?)

/** État d'une opération avant un changement. */
data class OperationState(
    val transactionId: Long,
    val ignored: Boolean,
    val transferAccountId: Long?,
    val imputations: List<ImputationEntity>,
)

data class JournalPayload(
    val operations: List<OperationState> = emptyList(),
    val rulesCreated: List<Long> = emptyList(),
    val rulesRemoved: List<RuleEntity> = emptyList(),
    val transferRulesCreated: List<Long> = emptyList(),
    val transfersCreated: List<Long> = emptyList(),
    val envelopesBefore: List<EnvelopeEntity> = emptyList(),
    val allocationsBefore: List<AllocationEntity> = emptyList(),
    val envelopesCreated: List<Long> = emptyList(),
    val manualCreated: List<Long> = emptyList(),
    /** Catégories telles qu'avant (nom, mère) ; celles créées par le changement, à retirer. */
    val categoriesBefore: List<CategoryState> = emptyList(),
    val categoriesCreated: List<String> = emptyList(),
    val manualDeleted: List<ManualState> = emptyList(),
    val datesBefore: List<DateState> = emptyList(),
    val aliasesBefore: List<AliasState> = emptyList(),
    /** Alias créés (à supprimer) et remplacés ou supprimés (à remettre). */
    val aliasRowsCreated: List<Long> = emptyList(),
    val aliasRowsRemoved: List<LabelAliasEntity> = emptyList(),
    val monthCountsBefore: List<MonthCountState> = emptyList(),
    val transfersCancelled: List<Long> = emptyList(),
    /** Mouvements d'épargne créés (à retirer) et retirés (à remettre). */
    val placementsCreated: List<Long> = emptyList(),
    val placementsRemoved: List<fr.baptiste.bank.data.local.PlacementEntity> = emptyList(),
    /**
     * Éléments touchés lus en base au moment du changement : enveloppes utilisées (une opération
     * rangée, une saisie, un ajout ou une règle vers elle), motifs des règles et des alias créés.
     */
    val extraTouched: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = operations.isEmpty() && rulesCreated.isEmpty() && rulesRemoved.isEmpty() && transferRulesCreated.isEmpty() &&
            transfersCreated.isEmpty() && envelopesBefore.isEmpty() && envelopesCreated.isEmpty() && manualCreated.isEmpty() &&
            categoriesBefore.isEmpty() && categoriesCreated.isEmpty() && manualDeleted.isEmpty() && datesBefore.isEmpty() &&
            aliasesBefore.isEmpty() && aliasRowsCreated.isEmpty() && aliasRowsRemoved.isEmpty() && monthCountsBefore.isEmpty() &&
            transfersCancelled.isEmpty() && placementsCreated.isEmpty() && placementsRemoved.isEmpty()

    /**
     * Ce que l'entrée touche : opérations, enveloppes, catégories, transferts, alias, mois. Deux
     * entrées qui partagent un élément ne s'annulent que dans l'ordre inverse.
     */
    fun touched(): Set<String> = buildSet {
        operations.forEach { add("op:${it.transactionId}") }
        manualCreated.forEach { add("op:$it") }
        manualDeleted.forEach { add("op:${it.transaction.id}") }
        datesBefore.forEach { add("op:${it.transactionId}") }
        aliasesBefore.forEach { add("op:${it.transactionId}") }
        envelopesBefore.forEach { add("env:${it.id}") }
        envelopesCreated.forEach { add("env:$it") }
        categoriesBefore.forEach { add("cat:${it.name.lowercase()}") }
        categoriesCreated.forEach { add("cat:${it.lowercase()}") }
        (transfersCreated + transfersCancelled).forEach { add("transfer:$it") }
        (placementsCreated + placementsRemoved.map { it.id }).forEach { add("placement:$it") }
        // Règles et alias : une clé par motif normalisé, la même à la création et à la suppression.
        rulesRemoved.forEach { add(ruleKey(it.pattern)) }
        aliasRowsRemoved.forEach { add(aliasKey(it.pattern)) }
        monthCountsBefore.forEach { add("month:${it.month}") }
        addAll(extraTouched.filterNot { it.startsWith(USE_PREFIX) })
    }

    /** Enveloppes créées par l'entrée (« env:X ») : bloquées tant qu'une entrée plus récente les utilise. */
    fun creates(): Set<String> = envelopesCreated.map { "env:$it" }.toSet()

    /** Enveloppes utilisées sans être changées : où vont ses opérations, saisies, ajouts et règles. */
    fun uses(): Set<String> = (extraTouched.filter { it.startsWith(USE_PREFIX) }.map { it.removePrefix(USE_PREFIX) } +
        rulesRemoved.map { "env:${it.envelopeId}" } +
        manualDeleted.flatMap { m -> m.imputations.map { "env:${it.envelopeId}" } }).toSet()

    /** Enveloppes dont l'entrée a besoin pour s'annuler : celles qu'elle change ou utilise (pas celles qu'elle crée). */
    fun envelopesNeeded(): Set<Long> = ((touched() - creates()) + uses())
        .filter { it.startsWith("env:") }
        .mapNotNull { it.removePrefix("env:").toLongOrNull() }
        .toSet()

    /**
     * Plusieurs changements faits d'un coup (un tri assisté, un plan appliqué) : une seule entrée.
     * Pour une opération touchée deux fois, on garde son état d'avant le premier changement.
     */
    operator fun plus(other: JournalPayload) = JournalPayload(
        operations = (operations + other.operations).distinctBy { it.transactionId },
        rulesCreated = rulesCreated + other.rulesCreated,
        rulesRemoved = rulesRemoved + other.rulesRemoved,
        transferRulesCreated = transferRulesCreated + other.transferRulesCreated,
        transfersCreated = transfersCreated + other.transfersCreated,
        envelopesBefore = (envelopesBefore + other.envelopesBefore).distinctBy { it.id }.filter { it.id !in envelopesCreated },
        allocationsBefore = allocationsBefore + other.allocationsBefore.filter { a -> envelopesBefore.none { it.id == a.envelopeId } },
        envelopesCreated = envelopesCreated + other.envelopesCreated,
        manualCreated = manualCreated + other.manualCreated,
        categoriesBefore = (categoriesBefore + other.categoriesBefore).distinctBy { it.name.lowercase() },
        categoriesCreated = categoriesCreated + other.categoriesCreated,
        manualDeleted = manualDeleted + other.manualDeleted,
        datesBefore = (datesBefore + other.datesBefore).distinctBy { it.transactionId },
        aliasesBefore = (aliasesBefore + other.aliasesBefore).distinctBy { it.transactionId },
        aliasRowsCreated = aliasRowsCreated + other.aliasRowsCreated,
        aliasRowsRemoved = aliasRowsRemoved + other.aliasRowsRemoved,
        monthCountsBefore = (monthCountsBefore + other.monthCountsBefore).distinctBy { it.month },
        transfersCancelled = transfersCancelled + other.transfersCancelled,
        placementsCreated = placementsCreated + other.placementsCreated,
        placementsRemoved = placementsRemoved + other.placementsRemoved,
        extraTouched = (extraTouched + other.extraTouched).distinct(),
    )

    fun encode(): String = JSONObject().apply {
        put("operations", JSONArray(operations.map { it.toJson() }))
        put("rulesCreated", JSONArray(rulesCreated))
        put("rulesRemoved", JSONArray(rulesRemoved.map { it.toJson() }))
        put("transferRulesCreated", JSONArray(transferRulesCreated))
        put("transfersCreated", JSONArray(transfersCreated))
        put("envelopesBefore", JSONArray(envelopesBefore.map { it.toJson() }))
        put("allocationsBefore", JSONArray(allocationsBefore.map { it.toJson() }))
        put("envelopesCreated", JSONArray(envelopesCreated))
        put("manualCreated", JSONArray(manualCreated))
        put("categoriesBefore", JSONArray(categoriesBefore.map { JSONObject().put("name", it.name).putNullable("parent", it.parent).putNullable("id", it.id).put("position", it.position).putNullable("parentId", it.parentId) }))
        put("categoriesCreated", JSONArray(categoriesCreated))
        put("manualDeleted", JSONArray(manualDeleted.map { m -> JSONObject().put("transaction", m.transaction.toJson()).put("imputations", JSONArray(m.imputations.map { it.toJson() })) }))
        put("datesBefore", JSONArray(datesBefore.map { JSONObject().put("id", it.transactionId).put("date", it.date.toString()) }))
        put("aliasesBefore", JSONArray(aliasesBefore.map { JSONObject().put("id", it.transactionId).putNullable("alias", it.alias) }))
        put("aliasRowsCreated", JSONArray(aliasRowsCreated))
        put("aliasRowsRemoved", JSONArray(aliasRowsRemoved.map { JSONObject().put("id", it.id).put("pattern", it.pattern).put("alias", it.alias) }))
        put("monthCountsBefore", JSONArray(monthCountsBefore.map { JSONObject().put("month", it.month.toString()).putNullable("counted", it.counted) }))
        put("transfersCancelled", JSONArray(transfersCancelled))
        put("placementsCreated", JSONArray(placementsCreated))
        put("placementsRemoved", JSONArray(placementsRemoved.map { it.toJson() }))
        put("extraTouched", JSONArray(extraTouched))
    }.toString()

    companion object {
        fun decode(text: String): JournalPayload {
            val json = JSONObject(text)
            return JournalPayload(
                operations = json.objects("operations").map { it.toOperationState() },
                rulesCreated = json.longs("rulesCreated"),
                rulesRemoved = json.objects("rulesRemoved").map { it.toRule() },
                transferRulesCreated = json.longs("transferRulesCreated"),
                transfersCreated = json.longs("transfersCreated"),
                envelopesBefore = json.objects("envelopesBefore").map { it.toEnvelope() },
                allocationsBefore = json.objects("allocationsBefore").map { it.toAllocation() },
                envelopesCreated = json.longs("envelopesCreated"),
                manualCreated = json.longs("manualCreated"),
                categoriesBefore = json.objects("categoriesBefore").map { CategoryState(it.getString("name"), it.stringOrNull("parent"), it.longOrNull("id"), it.optInt("position"), it.longOrNull("parentId")) },
                categoriesCreated = json.optJSONArray("categoriesCreated")?.let { array -> (0 until array.length()).map { array.getString(it) } }.orEmpty(),
                manualDeleted = json.objects("manualDeleted").map { ManualState(it.getJSONObject("transaction").toTransaction(), it.objects("imputations").map { i -> i.toImputation() }) },
                datesBefore = json.objects("datesBefore").map { DateState(it.getLong("id"), LocalDate.parse(it.getString("date"))) },
                aliasesBefore = json.objects("aliasesBefore").map { AliasState(it.getLong("id"), it.stringOrNull("alias")) },
                aliasRowsCreated = json.longs("aliasRowsCreated"),
                aliasRowsRemoved = json.objects("aliasRowsRemoved").map { LabelAliasEntity(id = it.optLong("id", 0L), pattern = it.getString("pattern"), alias = it.getString("alias")) },
                monthCountsBefore = json.objects("monthCountsBefore").map { MonthCountState(java.time.YearMonth.parse(it.getString("month")), if (it.isNull("counted")) null else it.getBoolean("counted")) },
                transfersCancelled = json.longs("transfersCancelled"),
                placementsCreated = json.longs("placementsCreated"),
                placementsRemoved = json.optJSONArray("placementsRemoved")?.let { rows -> (0 until rows.length()).map { rows.getJSONObject(it).toPlacement() } }.orEmpty(),
                extraTouched = json.optJSONArray("extraTouched")?.let { array -> (0 until array.length()).map { array.getString(it) } }.orEmpty(),
            )
        }
    }
}

private fun JSONObject.objects(name: String): List<JSONObject> {
    val array = optJSONArray(name) ?: return emptyList()
    return (0 until array.length()).map { array.getJSONObject(it) }
}

private fun JSONObject.longs(name: String): List<Long> {
    val array = optJSONArray(name) ?: return emptyList()
    return (0 until array.length()).map { array.getLong(it) }
}

private fun JSONObject.longOrNull(name: String): Long? = if (isNull(name) || !has(name)) null else getLong(name)
private fun JSONObject.stringOrNull(name: String): String? = if (isNull(name) || !has(name)) null else getString(name)
private fun JSONObject.dateOrNull(name: String): LocalDate? = stringOrNull(name)?.let(LocalDate::parse)
private fun JSONObject.putNullable(name: String, value: Any?): JSONObject = put(name, value ?: JSONObject.NULL)

private fun OperationState.toJson() = JSONObject()
    .put("transactionId", transactionId)
    .put("ignored", ignored)
    .putNullable("transferAccountId", transferAccountId)
    .put("imputations", JSONArray(imputations.map { it.toJson() }))

private fun JSONObject.toOperationState() = OperationState(
    transactionId = getLong("transactionId"),
    ignored = getBoolean("ignored"),
    transferAccountId = longOrNull("transferAccountId"),
    imputations = objects("imputations").map { it.toImputation() },
)

private fun ImputationEntity.toJson() = JSONObject()
    .put("transactionId", transactionId)
    .put("envelopeId", envelopeId)
    .put("periodKey", periodKey)
    .put("amountCents", amountCents)
    .put("mode", mode.name)
    .put("wholePeriod", wholePeriod)

private fun JSONObject.toImputation() = ImputationEntity(
    transactionId = getLong("transactionId"),
    envelopeId = getLong("envelopeId"),
    periodKey = getString("periodKey"),
    amountCents = getLong("amountCents"),
    mode = ImputationMode.valueOf(getString("mode")),
    wholePeriod = getBoolean("wholePeriod"),
)

private fun RuleEntity.toJson() = JSONObject()
    .put("id", id)
    .put("matchType", matchType.name)
    .put("pattern", pattern)
    .put("envelopeId", envelopeId)
    .put("priority", priority)

private fun JSONObject.toRule() = RuleEntity(
    // L'identifiant d'origine : une règle remise garde le sien (une création annulée ensuite la retrouve).
    id = optLong("id", 0L),
    matchType = RuleMatchType.valueOf(getString("matchType")),
    pattern = getString("pattern"),
    envelopeId = getLong("envelopeId"),
    priority = getInt("priority"),
)

private fun AllocationEntity.toJson() = JSONObject()
    .put("envelopeId", envelopeId)
    .put("periodKey", periodKey)
    .put("amountCents", amountCents)
    .put("coveredCents", coveredCents)
    .putNullable("startDate", startDate?.toString())
    .putNullable("endDate", endDate?.toString())

private fun JSONObject.toAllocation() = AllocationEntity(
    envelopeId = getLong("envelopeId"),
    periodKey = getString("periodKey"),
    amountCents = getLong("amountCents"),
    coveredCents = optLong("coveredCents"),
    startDate = dateOrNull("startDate"),
    endDate = dateOrNull("endDate"),
)

private fun EnvelopeEntity.toJson() = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("type", type.name)
    .put("scale", scale.name)
    .put("amountCents", amountCents)
    .put("status", status.name)
    .put("priority", priority)
    .put("carryOver", carryOver)
    .put("archived", archived)
    .put("every", every)
    .putNullable("anchorDate", anchorDate?.toString())
    .putNullable("groupName", groupName)
    .putNullable("fundingStart", fundingStart)
    .putNullable("catchUpFrom", catchUpFrom)
    .putNullable("startDate", startDate?.toString())
    .putNullable("firstAmountCents", firstAmountCents)
    .putNullable("firstReset", firstReset?.toString())
    .put("repeats", repeats)
    .putNullable("retiredOn", retiredOn?.toString())
    .putNullable("parentId", parentId)
    .putNullable("goalCents", goalCents)
    .putNullable("goalBy", goalBy)
    .putNullable("accountId", accountId)
    .put("position", position)

private fun JSONObject.toEnvelope() = EnvelopeEntity(
    id = getLong("id"),
    name = getString("name"),
    type = EnvelopeType.valueOf(getString("type")),
    scale = Scale.valueOf(getString("scale")),
    amountCents = getLong("amountCents"),
    status = EnvelopeStatus.valueOf(getString("status")),
    priority = getInt("priority"),
    carryOver = getBoolean("carryOver"),
    archived = getBoolean("archived"),
    every = getInt("every"),
    anchorDate = dateOrNull("anchorDate"),
    groupName = stringOrNull("groupName"),
    fundingStart = stringOrNull("fundingStart"),
    catchUpFrom = stringOrNull("catchUpFrom"),
    startDate = dateOrNull("startDate"),
    firstAmountCents = longOrNull("firstAmountCents"),
    firstReset = dateOrNull("firstReset"),
    repeats = getBoolean("repeats"),
    retiredOn = dateOrNull("retiredOn"),
    parentId = longOrNull("parentId"),
    goalCents = longOrNull("goalCents"),
    goalBy = stringOrNull("goalBy"),
    accountId = longOrNull("accountId"),
    position = optInt("position"),
)

private fun TransactionEntity.toJson() = JSONObject()
    .put("id", id)
    .put("accountId", accountId)
    .put("date", date.toString())
    .put("amountCents", amountCents)
    .put("rawLabel", rawLabel)
    .put("cleanLabel", cleanLabel)
    .putNullable("importHash", importHash)
    .put("source", source.name)
    .putNullable("reconciledWithId", reconciledWithId)
    .put("ignored", ignored)
    .putNullable("alias", alias)
    .putNullable("transferAccountId", transferAccountId)
    .putNullable("bookingDate", bookingDate?.toString())
    .putNullable("bankDate", bankDate?.toString())

private fun JSONObject.toTransaction() = TransactionEntity(
    id = getLong("id"),
    accountId = getLong("accountId"),
    date = LocalDate.parse(getString("date")),
    amountCents = getLong("amountCents"),
    rawLabel = getString("rawLabel"),
    cleanLabel = getString("cleanLabel"),
    importHash = stringOrNull("importHash"),
    source = TransactionSource.valueOf(getString("source")),
    reconciledWithId = longOrNull("reconciledWithId"),
    ignored = getBoolean("ignored"),
    alias = stringOrNull("alias"),
    transferAccountId = longOrNull("transferAccountId"),
    bookingDate = dateOrNull("bookingDate"),
    bankDate = dateOrNull("bankDate"),
)

/**
 * Une entrée de l'historique, telle qu'affichée. [blocked] : une entrée plus récente touche les
 * mêmes éléments ; l'annuler écraserait ce changement-là (« Modifié depuis »).
 */
data class JournalLine(val id: Long, val at: Long, val label: String, val undone: Boolean, val blocked: Boolean = false)

private fun fr.baptiste.bank.data.local.PlacementEntity.toJson() = JSONObject()
    .put("id", id).put("envelopeId", envelopeId).put("date", date.toString()).put("amountCents", amountCents).put("isStart", isStart)

private fun JSONObject.toPlacement() = fr.baptiste.bank.data.local.PlacementEntity(
    id = getLong("id"),
    envelopeId = getLong("envelopeId"),
    date = java.time.LocalDate.parse(getString("date")),
    amountCents = getLong("amountCents"),
    isStart = optBoolean("isStart"),
)
