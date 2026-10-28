package fr.baptiste.bank.banking

import fr.baptiste.bank.domain.bank.BankOperation
import fr.baptiste.bank.domain.bank.decimalToCents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.PrivateKey
import java.security.Signature
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64

/*
 * Client de l'API Enable Banking (https://enablebanking.com/docs/api/latest/).
 *
 * Parcours :
 * 1. GET /aspsps : liste des banques d'un pays ;
 * 2. POST /auth : adresse de la page d'authentification de la banque ;
 * 3. la banque renvoie vers l'adresse de retour avec un paramètre « code » ;
 * 4. POST /sessions : échange ce code contre une session et la liste des comptes autorisés ;
 * 5. GET /accounts/{uid}/balances et /transactions : lecture, tant que le consentement est valide.
 *
 * Chaque requête porte un jeton JWT signé avec la clé privée de l'application (RS256).
 */

/** Une banque proposée par Enable Banking. */
data class Aspsp(
    val name: String,
    val country: String,
    /** Durée maximale du consentement accepté par cette banque, en secondes. */
    val maximumConsentSeconds: Long?,
)

/** Un compte autorisé par l'utilisateur pendant l'authentification. */
data class LinkedAccount(
    val uid: String,
    val iban: String?,
    val name: String?,
    val currency: String?,
    /** Type ISO 20022 : CACC compte courant, SVGS épargne... (null si la banque ne le dit pas). */
    val cashType: String? = null,
) {
    /** « Compte courant ••1234 » : de quoi le reconnaître sans afficher tout l'IBAN. */
    val label: String
        get() = listOfNotNull(name?.takeIf { it.isNotBlank() } ?: "Compte", iban?.takeLast(4)?.let { "••$it" })
            .joinToString(" ")
}

data class BankSession(
    val id: String,
    val validUntil: Instant?,
    val accounts: List<LinkedAccount>,
)

/** Erreur renvoyée par l'API, avec le code HTTP et le message lisible quand il existe. */
class EnableBankingException(val status: Int, message: String) : IOException(message)

class EnableBankingClient(
    private val applicationId: String,
    private val privateKey: PrivateKey,
    private val baseUrl: String = "https://api.enablebanking.com",
) {

    suspend fun aspsps(country: String = "FR"): List<Aspsp> {
        val json = request("GET", "/aspsps?country=${encode(country)}&psu_type=personal")
        val list = json.optJSONArray("aspsps") ?: JSONArray()
        return (0 until list.length()).map { index ->
            val item = list.getJSONObject(index)
            Aspsp(
                name = item.getString("name"),
                country = item.optString("country", country),
                maximumConsentSeconds = item.optLong("maximum_consent_validity", 0).takeIf { it > 0 },
            )
        }.sortedBy { it.name.lowercase() }
    }

    /** Démarre l'authentification ; renvoie l'adresse de la page de la banque. */
    suspend fun startAuthorization(aspsp: Aspsp, redirectUrl: String, state: String, validUntil: Instant): String {
        val body = JSONObject()
            .put("access", JSONObject().put("valid_until", iso(validUntil)))
            .put("aspsp", JSONObject().put("name", aspsp.name).put("country", aspsp.country))
            .put("state", state)
            .put("redirect_url", redirectUrl)
            .put("psu_type", "personal")
        return request("POST", "/auth", body).getString("url")
    }

    suspend fun createSession(code: String): BankSession {
        val json = request("POST", "/sessions", JSONObject().put("code", code))
        val accounts = json.optJSONArray("accounts") ?: JSONArray()
        return BankSession(
            id = json.getString("session_id"),
            validUntil = json.optJSONObject("access")?.optString("valid_until")?.let(::parseInstant),
            accounts = (0 until accounts.length()).map { index ->
                val account = accounts.getJSONObject(index)
                LinkedAccount(
                    uid = account.getString("uid"),
                    iban = account.optJSONObject("account_id")?.optString("iban")?.takeIf { it.isNotBlank() },
                    name = account.optString("name").takeIf { it.isNotBlank() },
                    currency = account.optString("currency").takeIf { it.isNotBlank() },
                    cashType = account.optString("cash_account_type").takeIf { it.isNotBlank() },
                )
            },
        )
    }

    suspend fun deleteSession(sessionId: String) {
        request("DELETE", "/sessions/${encode(sessionId)}")
    }

    /**
     * Solde du compte, en centimes. Parmi les soldes fournis, on préfère le solde comptable de
     * clôture (CLBD), puis le disponible (ITAV, CLAV), puis le premier venu.
     */
    suspend fun balanceCents(accountUid: String): Long? {
        val balances = request("GET", "/accounts/${encode(accountUid)}/balances").optJSONArray("balances") ?: return null
        val items = (0 until balances.length()).map { balances.getJSONObject(it) }
        val preferred = listOf("CLBD", "ITAV", "CLAV", "XPCD", "ITBD")
        val chosen = preferred.firstNotNullOfOrNull { type -> items.firstOrNull { it.optString("balance_type") == type } }
            ?: items.firstOrNull()
            ?: return null
        return chosen.optJSONObject("balance_amount")?.optString("amount")?.let(::decimalToCents)
    }

    /**
     * Opérations comptabilisées depuis [from] inclus, toutes pages confondues. Les opérations en
     * attente (non comptabilisées) sont ignorées : leur montant ou leur libellé peut encore changer.
     */
    suspend fun bookedTransactions(accountUid: String, from: LocalDate): List<BankOperation> {
        val operations = mutableListOf<BankOperation>()
        var continuation: String? = null
        var pages = 0
        do {
            val query = buildString {
                append("/accounts/${encode(accountUid)}/transactions?date_from=$from")
                continuation?.let { append("&continuation_key=${encode(it)}") }
            }
            val json = request("GET", query)
            val transactions = json.optJSONArray("transactions") ?: JSONArray()
            for (index in 0 until transactions.length()) {
                transactions.getJSONObject(index).toOperation()?.let(operations::add)
            }
            continuation = json.optString("continuation_key").takeIf { it.isNotBlank() && it != "null" }
            pages++
        } while (continuation != null && pages < MAX_PAGES)
        return operations
    }

    private fun JSONObject.toOperation(): BankOperation? {
        val status = optString("status")
        if (status.isNotEmpty() && status != "BOOK") return null
        val amountObject = optJSONObject("transaction_amount") ?: return null
        val rawAmount = amountObject.optString("amount")
        val cents = decimalToCents(rawAmount) ?: return null
        val debit = optString("credit_debit_indicator") == "DBIT"
        val signed = if (rawAmount.trim().startsWith("-")) cents else if (debit) -cents else cents
        val date = listOf("booking_date", "value_date", "transaction_date")
            .firstNotNullOfOrNull { key -> optString(key).takeIf { it.isNotBlank() && it != "null" } }
            ?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
            ?: return null
        val remittance = optJSONArray("remittance_information")
            ?.let { array -> (0 until array.length()).map { array.optString(it) } }
            ?.filter { it.isNotBlank() }
            ?.joinToString(" ")
            .orEmpty()
        val counterpart = (if (debit) optJSONObject("creditor") else optJSONObject("debtor"))?.optString("name").orEmpty()
        val label = remittance.ifBlank { counterpart }.ifBlank { optString("note") }.ifBlank { "Opération bancaire" }
        val reference = listOf("entry_reference", "transaction_id")
            .firstNotNullOfOrNull { key -> optString(key).takeIf { it.isNotBlank() && it != "null" } }
        return BankOperation(reference = reference, date = date, amountCents = signed, rawLabel = label)
    }

    private suspend fun request(method: String, path: String, body: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.setRequestProperty("Authorization", "Bearer ${jwt()}")
                connection.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw EnableBankingException(code, errorMessage(code, text))
                if (text.isBlank()) JSONObject() else JSONObject(text)
            } finally {
                connection.disconnect()
            }
        }

    /** Jeton d'une heure, signé avec la clé privée de l'application. */
    private fun jwt(): String {
        val now = Instant.now().epochSecond
        val header = JSONObject().put("typ", "JWT").put("alg", "RS256").put("kid", applicationId)
        val payload = JSONObject()
            .put("iss", "enablebanking.com")
            .put("aud", "api.enablebanking.com")
            .put("iat", now)
            .put("exp", now + 3600)
        val unsigned = "${base64Url(header.toString().toByteArray())}.${base64Url(payload.toString().toByteArray())}"
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update(unsigned.toByteArray(Charsets.US_ASCII))
            sign()
        }
        return "$unsigned.${base64Url(signature)}"
    }

    private fun errorMessage(code: Int, body: String): String {
        val detail = runCatching {
            val json = JSONObject(body)
            listOf("message", "detail", "error_description", "error").firstNotNullOfOrNull { key ->
                json.opt(key)?.toString()?.takeIf { it.isNotBlank() && it != "null" }
            }
        }.getOrNull() ?: body.take(200)
        val hint = when (code) {
            401 -> " Vérifie l'identifiant de l'application et la clé importée."
            403 -> " Accès refusé : le compte est-il bien lié à l'application dans Enable Banking ?"
            429 -> " Trop de requêtes : la banque limite les synchronisations, réessaie plus tard."
            else -> ""
        }
        return "Erreur $code : $detail.$hint".replace("..", ".")
    }

    private companion object {
        const val TIMEOUT_MS = 30_000
        const val MAX_PAGES = 50

        fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

        fun iso(instant: Instant): String = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(instant.atOffset(ZoneOffset.UTC))

        fun parseInstant(text: String): Instant? =
            runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
                ?: runCatching { Instant.parse(text) }.getOrNull()
    }
}
