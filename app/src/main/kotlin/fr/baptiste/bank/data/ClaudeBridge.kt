package fr.baptiste.bank.data

import fr.baptiste.bank.domain.bank.ClaudePlan
import fr.baptiste.bank.domain.bank.PlanAction
import fr.baptiste.bank.domain.bank.ProposalTarget
import fr.baptiste.bank.domain.bank.envelopeNameKey
import fr.baptiste.bank.domain.bank.exportLabels
import fr.baptiste.bank.domain.bank.extractJsonObject
import fr.baptiste.bank.domain.bank.isOutOfBudget
import fr.baptiste.bank.domain.bank.opaqueId
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.model.ImputationMode
import fr.baptiste.bank.domain.model.TransactionSource
import fr.baptiste.bank.domain.model.category
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.money.parseMoney
import fr.baptiste.bank.domain.period.Frequency
import fr.baptiste.bank.domain.period.Scale
import fr.baptiste.bank.data.local.BankDatabase
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

/*
 * Le pont avec le skill Claude « bank-controle-depenses » (voir claude-skill/ à la racine du dépôt).
 *
 * 1. L'export : un fichier JSON anonymisé (format décrit dans FORMAT.md du skill), partagé vers
 *    Claude. Identifiants opaques, libellés nettoyés, aucun IBAN, numéro ni solde.
 * 2. Le plan : la réponse de Claude (PLAN.md du skill), collée dans l'app, lue en actions que
 *    l'utilisateur valide une à une. Rien n'est appliqué sans lui.
 */
class ClaudeBridge(
    private val database: BankDatabase,
    private val settings: SettingsRepository,
    private val repository: BudgetRepository,
) {
    private val transactionDao = database.transactionDao()

    /** L'export JSON, sur douze mois glissants au plus (depuis le début du suivi). */
    suspend fun export(today: LocalDate = LocalDate.now()): String {
        val salt = settings.exportSalt()
        val since = maxOf(repository.trackingStart(today).first(), YearMonth.from(today).minusMonths(12))
        val from = since.atDay(1)
        val envelopes = repository.envelopes.first() + repository.archivedEnvelopes.first()
        val envId = envelopes.associate { it.id to opaqueId("env", salt, it.id) }
        val accounts = database.accountDao().active()
        val accId = accounts.associate { it.id to opaqueId("acc", salt, it.id) }
        val grouped = transactionDao.exportRows(from).groupBy { it.id }
        val rows = grouped.values.map { it.first() }
        val salaryKey = repository.salary.first()?.first
        val labels = exportLabels(rows.map { Triple(it.rawLabel, it.alias, it.amountCents) }, salaryKey)
        val transferLabels = transactionDao.transferRawLabels().filter { fr.baptiste.bank.domain.bank.isTransferLabel(it) }
        val spent = database.imputationDao().spentByPeriod()
            .groupBy { it.envelopeId }
            .mapValues { (_, list) -> list.associate { it.periodKey to it.amountCents } }

        val root = JSONObject()
            .put("format", "bank-export")
            .put("version", 1)
            .put("export_id", "${today}-${opaqueId("x", salt, System.currentTimeMillis()).takeLast(4)}")
            .put("created", today.toString())
            .put("currency", "EUR")
            .put("period", JSONObject().put("from", from.toString()).put("to", today.toString()))
            .put("accounts", JSONArray(accounts.map { JSONObject().put("id", accId[it.id]).put("name", it.name).put("type", it.type.name) }))
            .put(
                "envelopes",
                JSONArray(
                    envelopes.map { envelope ->
                        JSONObject()
                            .put("id", envId[envelope.id])
                            .put("name", envelope.name)
                            .put("category", envelope.category)
                            // Rangée dans une autre enveloppe (l'arbre de Budget) ; null en haut ou dans une catégorie.
                            .put("parent", envelope.parentId?.let(envId::get) ?: JSONObject.NULL)
                            .put("family", familyWord(envelope.type))
                            .put("amount", euros(envelope.amount.cents))
                            .put("rhythm", if (envelope.type == fr.baptiste.bank.domain.model.EnvelopeType.LUMP) "somme mise à part" else rhythm(envelope.frequency))
                            .put("repeats", envelope.repeats)
                            .put("keeps_leftover", envelope.carryOver)
                            .put("start", envelope.start?.toString() ?: JSONObject.NULL)
                            .put("reset", envelope.firstReset?.toString() ?: JSONObject.NULL)
                            .put("stopped", envelope.retiredOn?.toString() ?: if (envelope.archived) "oui" else JSONObject.NULL)
                            .put(
                                "periods",
                                JSONArray(
                                    envelope.periods.filter { !it.start.isBefore(from) }.map { period ->
                                        JSONObject()
                                            .put("start", period.start.toString())
                                            .put("end", if (envelope.type == fr.baptiste.bank.domain.model.EnvelopeType.LUMP) JSONObject.NULL else period.endInclusive.toString())
                                            // La part propre : un parent couvre ses enfants, leurs budgets ne se comptent qu'une fois.
                                            .put("budget", euros(maxOf(0, period.amount.cents - period.covered.cents)))
                                            .apply { if (period.covered.isPositive) put("children_cover", euros(period.covered.cents)) }
                                            .put("spent", euros(spent[envelope.id]?.get(period.key) ?: 0))
                                    },
                                ),
                            )
                    },
                ),
            )
            .put(
                "rules",
                JSONArray(
                    // Mots-clés nettoyés comme les libellés ; ceux qui désignent un virement (nom d'une
                    // personne possible) ne partent pas, d'après tous les virements connus.
                    database.ruleDao().all().mapNotNull { rule ->
                        val pattern = fr.baptiste.bank.domain.bank.exportRulePattern(rule.pattern, transferLabels) ?: return@mapNotNull null
                        envId[rule.envelopeId]?.let { JSONObject().put("pattern", pattern).put("envelope", it) }
                    }.distinctBy { it.getString("pattern") + it.getString("envelope") },
                ),
            )
            .put(
                "operations",
                JSONArray(
                    rows.mapIndexed { index, row ->
                        JSONObject()
                            .put("id", opaqueId("op", salt, row.id))
                            .put("date", row.date.toString())
                            .put("booking_date", row.bookingDate?.toString() ?: JSONObject.NULL)
                            .put("amount", euros(row.amountCents))
                            .put("label", labels[index])
                            .put("account", accId[row.accountId] ?: JSONObject.NULL)
                            .put("source", if (row.source == TransactionSource.MANUAL) "saisie" else "banque")
                            .put("status", status(row))
                            .put("envelope", row.envelopeId?.let { envId[it] } ?: JSONObject.NULL)
                            .put("by_rule", row.mode == ImputationMode.RULE)
                            .apply {
                                // Opération répartie : chaque part, son enveloppe et son montant (positif).
                                val parts = grouped[row.id].orEmpty().filter { it.envelopeId != null }
                                if (parts.size > 1) {
                                    put("parts", JSONArray(parts.map { JSONObject().put("envelope", envId[it.envelopeId]).put("amount", euros(kotlin.math.abs(it.partCents ?: 0))) }))
                                }
                            }
                    },
                ),
            )
        return root.toString(1)
    }

    /** Lit la réponse de Claude : l'objet JSON du plan (bloc ```json``` ou texte brut). Null si illisible. */
    fun parsePlan(reply: String): ClaudePlan? {
        val json = extractJsonObject(reply) ?: return null
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val array = root.optJSONArray("actions") ?: return null
        val actions = (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val reason = item.text("reason")
            val confidence = item.text("confidence")
            when (item.text("type")) {
                "move" -> {
                    val operation = item.text("operation") ?: return@mapNotNull null
                    val envelope = item.text("envelope") ?: return@mapNotNull null
                    PlanAction.Move(operation, envelope, item.text("name"), item.text("keyword"), reason, confidence)
                }
                "split" -> {
                    val operation = item.text("operation") ?: return@mapNotNull null
                    val list = item.optJSONArray("parts") ?: return@mapNotNull null
                    val parts = (0 until list.length()).mapNotNull { i ->
                        val part = list.optJSONObject(i) ?: return@mapNotNull null
                        val envelope = part.text("envelope") ?: return@mapNotNull null
                        val amount = part.amount("amount") ?: return@mapNotNull null
                        envelope to amount
                    }
                    if (parts.size < 2) return@mapNotNull null
                    PlanAction.Split(operation, parts, reason, confidence)
                }
                "rule" -> PlanAction.Rule(item.text("pattern") ?: return@mapNotNull null, item.text("envelope") ?: return@mapNotNull null, reason, confidence)
                "rename" -> PlanAction.Rename(item.text("pattern") ?: return@mapNotNull null, item.text("name") ?: return@mapNotNull null, reason, confidence)
                "envelope" -> PlanAction.CreateEnvelope(
                    name = item.text("name") ?: return@mapNotNull null,
                    family = item.text("family") ?: "quotidien",
                    amount = item.amount("amount") ?: Money.ZERO,
                    category = item.text("category"),
                    every = item.optInt("every", 0).takeIf { it > 0 },
                    unit = item.text("unit"),
                    reset = item.text("reset")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                    repeats = item.optBoolean("repeats", true),
                    reason = reason,
                    confidence = confidence,
                )
                "flag" -> PlanAction.Flag(item.text("operation"), item.text("issue") ?: "autre", item.text("note"), reason, confidence)
                else -> null
            }
        }
        return ClaudePlan(root.text("export_id"), actions)
    }

    /** Ce que l'app sait afficher d'une action : le libellé de l'opération visée, le nom de l'enveloppe. */
    data class Context(val operationLabels: Map<String, String>, val envelopeNames: Map<String, String>)

    suspend fun context(): Context {
        val salt = settings.exportSalt()
        val envelopes = repository.envelopes.first() + repository.archivedEnvelopes.first()
        val rows = transactionDao.exportRows(LocalDate.of(2000, 1, 1)).groupBy { it.id }.values.map { it.first() }
        return Context(
            operationLabels = rows.associate { opaqueId("op", salt, it.id) to "${it.alias ?: it.rawLabel} · ${euros(it.amountCents)} € · ${it.date}" },
            envelopeNames = envelopes.associate { opaqueId("env", salt, it.id) to it.name },
        )
    }

    /**
     * Applique les actions cochées, dans l'ordre qui a du sens : créer les budgets, ranger, puis les
     * règles et les noms. Rend le nombre d'actions appliquées.
     */
    suspend fun apply(actions: List<PlanAction>, today: LocalDate = LocalDate.now()): Int {
        val salt = settings.exportSalt()
        val envelopes = repository.envelopes.first()
        val envelopeName = envelopes.associate { opaqueId("env", salt, it.id) to it.name }
        val transactionId = transactionDao.allIds().associateBy { opaqueId("op", salt, it) }
        fun targetName(ref: String) = envelopeName[ref] ?: ref
        var applied = 0

        actions.filterIsInstance<PlanAction.CreateEnvelope>().forEach { action ->
            if (envelopes.none { envelopeNameKey(it.name) == envelopeNameKey(action.name) }) {
                repository.saveEnvelope(newEnvelope(action, today))
                applied++
            }
        }
        val moves = actions.filterIsInstance<PlanAction.Move>().mapNotNull { action ->
            val id = transactionId[action.operation] ?: return@mapNotNull null
            val target = if (isOutOfBudget(action.envelope)) ProposalTarget.OutOfBudget else ProposalTarget.Existing(targetName(action.envelope))
            AssistedSortChoice(id, target, alias = action.name, keyword = action.keyword)
        }
        if (moves.isNotEmpty()) applied += repository.applyAssistedSort(moves).sorted
        // Répartitions : les enveloppes créées plus haut comprises.
        val splits = actions.filterIsInstance<PlanAction.Split>()
        if (splits.isNotEmpty()) {
            val byName = repository.envelopes.first().associateBy { envelopeNameKey(it.name) }
            splits.forEach { action ->
                val id = transactionId[action.operation] ?: return@forEach
                val parts = action.parts.map { (ref, amount) -> byName[envelopeNameKey(targetName(ref))] to amount }
                if (parts.all { it.first != null } && repository.splitOperation(id, parts.map { it.first!! to it.second })) applied++
            }
        }
        actions.filterIsInstance<PlanAction.Rule>().forEach { action ->
            if (repository.addRule(action.pattern, targetName(action.envelope))) applied++
        }
        actions.filterIsInstance<PlanAction.Rename>().forEach { action ->
            repository.addAlias(action.pattern, action.name)
            applied++
        }
        if (actions.any { it is PlanAction.Rule }) repository.applyRules()
        return applied
    }

    private fun newEnvelope(action: PlanAction.CreateEnvelope, today: LocalDate): Envelope {
        val type = typeForFamily(action.family).let { if (it == EnvelopeType.ANNUAL && !action.repeats) EnvelopeType.PLANNED else it }
        val scale = when (envelopeNameKey(action.unit.orEmpty())) {
            "AN", "ANS", "ANNEE", "ANNEES", "YEAR" -> Scale.YEAR
            "SEMAINE", "SEMAINES", "WEEK" -> Scale.WEEK
            else -> Scale.MONTH
        }
        val frequency = Frequency(action.every ?: 1, scale)
        val rare = type == EnvelopeType.ANNUAL || type == EnvelopeType.RECURRING || type == EnvelopeType.PLANNED
        val start = today.withDayOfMonth(1)
        return Envelope(
            id = 0,
            name = action.name.trim(),
            type = if (type == EnvelopeType.ANNUAL && frequency != Frequency.YEARLY) EnvelopeType.RECURRING else type,
            frequency = frequency,
            amount = action.amount,
            status = type.defaultStatus,
            group = action.category?.trim()?.takeIf { it.isNotEmpty() },
            start = start,
            firstReset = if (rare) action.reset else null,
            repeats = action.repeats,
            carryOver = rare && action.repeats,
            fundingStart = if (rare) YearMonth.from(today) else null,
            anchor = if (type == EnvelopeType.PLANNED) start else null,
        )
    }

    private fun status(row: fr.baptiste.bank.data.local.ExportRow): String = when {
        row.transferAccountId != null -> "vers mes comptes"
        row.ignored -> "hors budget"
        row.envelopeId != null -> "rangée"
        else -> "à trier"
    }

    private fun familyWord(type: EnvelopeType): String = when (type) {
        EnvelopeType.DAILY -> "quotidien"
        EnvelopeType.FIXED -> "charge"
        EnvelopeType.SAVINGS -> "épargne"
        else -> "rare"
    }

    private fun rhythm(frequency: Frequency): String {
        val unit = when (frequency.scale) {
            Scale.DAY -> "jour"
            Scale.WEEK -> "semaine"
            Scale.MONTH -> "mois"
            Scale.YEAR -> "an"
        }
        return if (frequency.every == 1) "chaque $unit" else "tous les ${frequency.every} $unit"
    }

    private fun euros(cents: Long): Double = cents / 100.0

    private fun JSONObject.text(key: String): String? = optString(key).takeIf { has(key) && !isNull(key) && it.isNotBlank() }

    private fun JSONObject.amount(key: String): Money? = when {
        !has(key) || isNull(key) -> null
        else -> optDouble(key).takeIf { !it.isNaN() }?.let { Money(Math.round(it * 100)) } ?: parseMoney(optString(key))
    }
}
