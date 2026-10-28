package fr.baptiste.bank.ui.assistant

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/*
 * Ouvrir Claude avec un message déjà écrit, sans clé d'API : le message passe par l'app Claude
 * de l'utilisateur, avec son abonnement. Il est toujours copié aussi, pour pouvoir le coller si
 * l'app Claude ne le reprend pas d'elle-même.
 *
 * - Message court : lien claude.ai/new?q=..., que l'app Claude ouvre si elle est installée
 *   (sinon le navigateur). Au-delà d'environ 2 000 caractères, un lien devient peu fiable.
 * - Message long : partage Android (ACTION_SEND) vers l'app Claude ; à défaut, le choix d'une app.
 */

private const val CLAUDE_PACKAGE = "com.anthropic.claude"
private const val MAX_LINK_LENGTH = 1800

/** Copie [text] et ouvre Claude avec ce message. Rend vrai si une app a pu s'ouvrir. */
fun openClaudeWith(context: Context, text: String): Boolean {
    copyToClipboard(context, text)
    if (text.length <= MAX_LINK_LENGTH) {
        val link = Intent(Intent.ACTION_VIEW, Uri.parse("https://claude.ai/new?q=" + Uri.encode(text)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (tryStart(context, link)) return true
    }
    val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    if (tryStart(context, Intent(share).setPackage(CLAUDE_PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return true
    return tryStart(
        context,
        Intent.createChooser(share, "Envoyer à Claude").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Message pour Claude", text))
}

fun readClipboard(context: Context): String? =
    context.getSystemService(ClipboardManager::class.java)
        ?.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()

private fun tryStart(context: Context, intent: Intent): Boolean =
    try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
