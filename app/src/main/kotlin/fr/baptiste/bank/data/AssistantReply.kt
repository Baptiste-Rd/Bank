package fr.baptiste.bank.data

import fr.baptiste.bank.domain.bank.AssistantProposal
import fr.baptiste.bank.domain.bank.extractJsonArray
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lit la réponse collée depuis Claude : le tableau JSON demandé en phase 3, un objet par opération.
 * Un objet sans numéro ou sans enveloppe est ignoré ; le reste est facultatif. Rend null si la
 * réponse ne contient aucun tableau lisible.
 */
fun parseAssistantReply(reply: String): List<AssistantProposal>? {
    val json = extractJsonArray(reply) ?: return null
    val array = runCatching { JSONArray(json) }.getOrNull() ?: return null
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val ref = item.optInt("n", -1).takeIf { it > 0 } ?: return@mapNotNull null
        val envelope = item.text("enveloppe") ?: return@mapNotNull null
        AssistantProposal(
            ref = ref,
            name = item.text("nom"),
            envelope = envelope,
            isNew = item.optBoolean("nouvelle", false),
            family = item.text("famille"),
            keyword = item.text("motcle"),
            confidence = item.text("confiance"),
            reason = item.text("raison"),
            category = item.text("categorie") ?: item.text("catégorie"),
        )
    }.distinctBy { it.ref }
}

private fun JSONObject.text(key: String): String? =
    if (isNull(key)) null else optString(key, "").trim().takeIf { it.isNotEmpty() }
