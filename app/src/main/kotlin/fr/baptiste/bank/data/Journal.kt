package fr.baptiste.bank.data

import fr.baptiste.bank.data.local.AllocationEntity
import fr.baptiste.bank.data.local.EnvelopeEntity
import fr.baptiste.bank.data.local.ImputationEntity
import fr.baptiste.bank.data.local.RuleEntity
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
 * - les dépenses saisies à la main (à supprimer).
 */

/** Une catégorie et sa mère, avant un changement. */
data class CategoryState(val name: String, val parent: String?)

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
) {
    val isEmpty: Boolean
        get() = operations.isEmpty() && rulesCreated.isEmpty() && rulesRemoved.isEmpty() && transferRulesCreated.isEmpty() &&
            transfersCreated.isEmpty() && envelopesBefore.isEmpty() && envelopesCreated.isEmpty() && manualCreated.isEmpty() &&
            categoriesBefore.isEmpty() && categoriesCreated.isEmpty()

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
        put("categoriesBefore", JSONArray(categoriesBefore.map { JSONObject().put("name", it.name).putNullable("parent", it.parent) }))
        put("categoriesCreated", JSONArray(categoriesCreated))
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
                categoriesBefore = json.objects("categoriesBefore").map { CategoryState(it.getString("name"), it.stringOrNull("parent")) },
                categoriesCreated = json.optJSONArray("categoriesCreated")?.let { array -> (0 until array.length()).map { array.getString(it) } }.orEmpty(),
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
    .put("matchType", matchType.name)
    .put("pattern", pattern)
    .put("envelopeId", envelopeId)
    .put("priority", priority)

private fun JSONObject.toRule() = RuleEntity(
    matchType = RuleMatchType.valueOf(getString("matchType")),
    pattern = getString("pattern"),
    envelopeId = getLong("envelopeId"),
    priority = getInt("priority"),
)

private fun AllocationEntity.toJson() = JSONObject()
    .put("envelopeId", envelopeId)
    .put("periodKey", periodKey)
    .put("amountCents", amountCents)
    .putNullable("startDate", startDate?.toString())
    .putNullable("endDate", endDate?.toString())

private fun JSONObject.toAllocation() = AllocationEntity(
    envelopeId = getLong("envelopeId"),
    periodKey = getString("periodKey"),
    amountCents = getLong("amountCents"),
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
    .putNullable("startDate", startDate?.toString())
    .putNullable("firstAmountCents", firstAmountCents)
    .putNullable("firstReset", firstReset?.toString())
    .put("repeats", repeats)
    .putNullable("retiredOn", retiredOn?.toString())

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
    startDate = dateOrNull("startDate"),
    firstAmountCents = longOrNull("firstAmountCents"),
    firstReset = dateOrNull("firstReset"),
    repeats = getBoolean("repeats"),
    retiredOn = dateOrNull("retiredOn"),
)

/** Une entrée de l'historique, telle qu'affichée. */
data class JournalLine(val id: Long, val at: Long, val label: String, val undone: Boolean)
