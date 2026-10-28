package fr.baptiste.bank.banking

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import fr.baptiste.bank.data.BudgetRepository
import fr.baptiste.bank.data.ImportResult
import fr.baptiste.bank.domain.model.AccountType
import fr.baptiste.bank.domain.money.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Adresse de retour proposée : à déclarer telle quelle dans l'application Enable Banking. */
const val DEFAULT_REDIRECT_URL = "https://localhost/bank-callback"

/** État de la connexion bancaire, tel qu'affiché à l'écran. */
data class BankConnection(
    val applicationId: String?,
    val redirectUrl: String,
    val hasKey: Boolean,
    val bankName: String?,
    val sessionId: String?,
    val validUntil: Instant?,
    val accountUid: String?,
    val accountLabel: String?,
    val lastSync: Instant?,
    val balanceCents: Long?,
) {
    /** Identifiant et clé fournis : on peut parler à l'API. */
    val isConfigured: Boolean get() = !applicationId.isNullOrBlank() && hasKey

    /** Consentement en cours et compte choisi : on peut synchroniser. */
    val isConnected: Boolean
        get() = isConfigured && sessionId != null && accountUid != null && (validUntil == null || validUntil.isAfter(Instant.now()))

    /** Consentement présent mais expiré : il faut repasser par la banque. */
    val isExpired: Boolean get() = sessionId != null && validUntil != null && !validUntil.isAfter(Instant.now())
}

/**
 * Connexion à la banque via Enable Banking, et synchronisation des opérations.
 *
 * Les réglages (identifiant d'application, session, compte choisi) sont dans un DataStore à part ;
 * la clé privée est dans le [KeyVault]. Le mode démo a ses propres fichiers.
 */
class BankSyncRepository(
    private val context: Context,
    private val budget: BudgetRepository,
    private val vault: KeyVault,
    storeName: String = "bank",
) {
    private val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile(storeName) },
    )

    private object Keys {
        val applicationId = stringPreferencesKey("application_id")
        val redirectUrl = stringPreferencesKey("redirect_url")
        val bankName = stringPreferencesKey("bank_name")
        val bankCountry = stringPreferencesKey("bank_country")
        val pendingState = stringPreferencesKey("pending_state")
        val sessionId = stringPreferencesKey("session_id")
        val validUntil = longPreferencesKey("valid_until")
        val accountUid = stringPreferencesKey("account_uid")
        val accountLabel = stringPreferencesKey("account_label")
        val accountIbanSuffix = stringPreferencesKey("account_iban_suffix")
        val lastSync = longPreferencesKey("last_sync")
        val balance = longPreferencesKey("balance_cents")

        /** Comptes autorisés lors de la connexion, une ligne par compte (uid, libellé, fin d'IBAN, type). */
        val sessionAccounts = stringPreferencesKey("session_accounts")

        /** Comptes dont on suit seulement le solde (livrets) : les autres comptes autorisés. */
        val trackedAccounts = stringPreferencesKey("tracked_accounts")
    }

    private fun encodeAccounts(accounts: List<LinkedAccount>): String = accounts.joinToString("\n") { account ->
        listOf(account.uid, account.label, account.iban?.takeLast(4).orEmpty(), account.cashType.orEmpty())
            .joinToString("\t") { it.replace("\t", " ").replace("\n", " ") }
    }

    private fun decodeAccounts(text: String?): List<LinkedAccount> = text.orEmpty().lines().filter { it.isNotBlank() }.map { line ->
        val parts = line.split('\t')
        LinkedAccount(
            uid = parts[0],
            iban = parts.getOrNull(2)?.takeIf { it.isNotEmpty() },
            name = parts.getOrNull(1),
            currency = null,
            cashType = parts.getOrNull(3)?.takeIf { it.isNotEmpty() },
        )
    }

    /** La clé vit hors du DataStore : sa présence est suivie à part pour mettre l'écran à jour. */
    private val keyPresent = MutableStateFlow(vault.hasKey)

    val connection: Flow<BankConnection> = combine(store.data, keyPresent) { preferences, hasKey ->
        preferences.toConnection(hasKey)
    }

    private fun Preferences.toConnection(hasKey: Boolean) = BankConnection(
        applicationId = this[Keys.applicationId],
        redirectUrl = this[Keys.redirectUrl] ?: DEFAULT_REDIRECT_URL,
        hasKey = hasKey,
        bankName = this[Keys.bankName],
        sessionId = this[Keys.sessionId],
        validUntil = this[Keys.validUntil]?.let(Instant::ofEpochSecond),
        accountUid = this[Keys.accountUid],
        accountLabel = this[Keys.accountLabel],
        lastSync = this[Keys.lastSync]?.let(Instant::ofEpochMilli),
        balanceCents = this[Keys.balance],
    )

    suspend fun saveApplication(applicationId: String, redirectUrl: String) {
        store.edit {
            it[Keys.applicationId] = applicationId.trim()
            it[Keys.redirectUrl] = redirectUrl.trim().ifEmpty { DEFAULT_REDIRECT_URL }
        }
    }

    /** Lit le fichier .pem choisi par l'utilisateur et le range, chiffré, dans le coffre. */
    suspend fun importKey(uri: Uri) {
        withContext(Dispatchers.IO) {
            val pem = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: throw IllegalArgumentException("Fichier illisible.")
            vault.store(pem)
        }
        keyPresent.value = true
    }

    private suspend fun client(): EnableBankingClient {
        val applicationId = store.data.first()[Keys.applicationId]
            ?: throw IllegalStateException("Indique d'abord l'identifiant de l'application.")
        val key = withContext(Dispatchers.IO) { vault.privateKey() } ?: throw IllegalStateException("Importe d'abord la clé privée (.pem).")
        return EnableBankingClient(applicationId, key)
    }

    suspend fun banks(country: String = "FR"): List<Aspsp> = client().aspsps(country)

    /**
     * Prépare l'authentification auprès de [bank] et renvoie l'adresse de sa page. Le consentement
     * demandé dure le maximum accepté par la banque, dans la limite de 180 jours.
     */
    suspend fun startAuthorization(bank: Aspsp): String {
        val state = UUID.randomUUID().toString()
        val maxSeconds = minOf(bank.maximumConsentSeconds ?: MAX_CONSENT_SECONDS, MAX_CONSENT_SECONDS)
        val validUntil = Instant.now().plusSeconds(maxSeconds - 3600)
        val redirect = store.data.first()[Keys.redirectUrl] ?: DEFAULT_REDIRECT_URL
        val url = client().startAuthorization(bank, redirect, state, validUntil)
        store.edit {
            it[Keys.pendingState] = state
            it[Keys.bankName] = bank.name
            it[Keys.bankCountry] = bank.country
        }
        return url
    }

    /** Vrai si [url] est l'adresse de retour déclarée : l'authentification est terminée. */
    suspend fun isRedirect(url: String): Boolean {
        val redirect = store.data.first()[Keys.redirectUrl] ?: DEFAULT_REDIRECT_URL
        return url.startsWith(redirect)
    }

    /**
     * Termine l'authentification à partir de l'adresse de retour : vérifie le paramètre « state »
     * (protection contre une réponse qui ne viendrait pas de notre demande), échange le code contre
     * une session et renvoie les comptes autorisés.
     */
    suspend fun completeAuthorization(callbackUrl: String): List<LinkedAccount> {
        val uri = Uri.parse(callbackUrl.trim())
        uri.getQueryParameter("error")?.let { error ->
            val description = uri.getQueryParameter("error_description")
            throw IllegalStateException(description ?: if (error == "access_denied") "Accès refusé ou annulé." else error)
        }
        val code = uri.getQueryParameter("code") ?: throw IllegalArgumentException("Cette adresse ne contient pas de code.")
        val expectedState = store.data.first()[Keys.pendingState]
        val state = uri.getQueryParameter("state")
        if (expectedState != null && state != null && state != expectedState) {
            throw IllegalStateException("Réponse inattendue (state différent) : recommence la connexion.")
        }
        val session = client().createSession(code)
        store.edit {
            it[Keys.sessionId] = session.id
            session.validUntil?.let { until -> it[Keys.validUntil] = until.epochSecond }
            it.remove(Keys.pendingState)
            it.remove(Keys.accountUid)
            it.remove(Keys.accountLabel)
            it[Keys.sessionAccounts] = encodeAccounts(session.accounts)
        }
        if (session.accounts.isEmpty()) {
            throw IllegalStateException(
                "La banque n'a autorisé aucun compte. En mode restreint, seuls les comptes liés dans " +
                    "le panneau Enable Banking (« Link accounts ») sont accessibles.",
            )
        }
        return session.accounts
    }

    /** Choisit le compte à synchroniser, puis lance une première synchronisation. */
    suspend fun chooseAccount(account: LinkedAccount): ImportResult {
        store.edit {
            it[Keys.accountUid] = account.uid
            it[Keys.accountLabel] = account.label
            account.iban?.takeLast(4)?.let { suffix -> it[Keys.accountIbanSuffix] = suffix }
            it.remove(Keys.lastSync)
            // Les autres comptes autorisés (livrets...) : on suivra leur solde, sans importer leurs opérations.
            it[Keys.trackedAccounts] = encodeAccounts(decodeAccounts(it[Keys.sessionAccounts]).filter { other -> other.uid != account.uid })
        }
        budget.linkBankAccount(account.uid, account.label, account.iban?.takeLast(4))
        return sync()
    }

    /**
     * Récupère les opérations comptabilisées et les enregistre. La première fois : depuis le début
     * du mois. Ensuite : depuis 10 jours avant la dernière synchronisation, car une banque peut
     * comptabiliser une opération quelques jours après sa date ; les doublons sont écartés.
     */
    suspend fun sync(): ImportResult {
        val preferences = store.data.first()
        val uid = preferences[Keys.accountUid] ?: throw IllegalStateException("Aucun compte choisi.")
        val lastSync = preferences[Keys.lastSync]?.let(Instant::ofEpochMilli)
        val today = LocalDate.now()
        val from = lastSync?.atZone(ZoneId.systemDefault())?.toLocalDate()?.minusDays(10)
            ?: today.withDayOfMonth(1)
        val client = client()
        val operations = client.bookedTransactions(uid, from)
        val balance = runCatching { client.balanceCents(uid) }.getOrNull()
        val result = budget.importBankOperations(
            accountUid = uid,
            accountName = preferences[Keys.accountLabel] ?: "Compte courant",
            ibanSuffix = preferences[Keys.accountIbanSuffix],
            operations = operations,
        )
        store.edit {
            it[Keys.lastSync] = System.currentTimeMillis()
            if (balance != null) it[Keys.balance] = balance
        }
        // Le solde du compte courant, gardé daté : l'app le décompose (onglet Budget).
        if (balance != null) {
            budget.recordLinkedBalance(
                uid = uid,
                name = preferences[Keys.accountLabel] ?: "Compte courant",
                ibanSuffix = preferences[Keys.accountIbanSuffix],
                type = AccountType.CURRENT,
                balance = Money(balance),
            )
        }
        // Soldes des comptes suivis : un échec sur l'un n'empêche pas les autres.
        for (tracked in decodeAccounts(preferences[Keys.trackedAccounts])) {
            val cents = runCatching { client.balanceCents(tracked.uid) }.getOrNull() ?: continue
            budget.recordLinkedBalance(
                uid = tracked.uid,
                name = tracked.name ?: tracked.label,
                ibanSuffix = tracked.iban,
                type = if (tracked.cashType == "CACC") AccountType.CURRENT else AccountType.SAVINGS,
                balance = Money(cents),
            )
        }
        return result
    }

    /** Vrai si une synchronisation automatique est due (dernière il y a plus de 6 heures). */
    suspend fun isSyncDue(): Boolean {
        val connection = connection.first()
        if (!connection.isConnected) return false
        val last = connection.lastSync ?: return true
        return Duration.between(last, Instant.now()) > Duration.ofHours(6)
    }

    /** Ferme la session chez Enable Banking (et le consentement à la banque) puis l'oublie ici. */
    suspend fun disconnect() {
        val sessionId = store.data.first()[Keys.sessionId]
        if (sessionId != null) runCatching { client().deleteSession(sessionId) }
        store.edit {
            it.remove(Keys.sessionId)
            it.remove(Keys.validUntil)
            it.remove(Keys.accountUid)
            it.remove(Keys.accountLabel)
            it.remove(Keys.pendingState)
            it.remove(Keys.balance)
            it.remove(Keys.sessionAccounts)
            it.remove(Keys.trackedAccounts)
        }
    }

    /** Efface tout : session, identifiant d'application et clé privée. */
    suspend fun forgetAll() {
        disconnect()
        withContext(Dispatchers.IO) { vault.clear() }
        keyPresent.value = false
        store.edit { it.clear() }
    }

    private companion object {
        /** 180 jours : durée maximale d'un consentement d'accès aux comptes en Europe (DSP2). */
        const val MAX_CONSENT_SECONDS = 180L * 24 * 3600
    }
}
