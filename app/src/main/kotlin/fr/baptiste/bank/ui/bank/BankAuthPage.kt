package fr.baptiste.bank.ui.bank

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Page d'authentification de la banque, ouverte dans l'app.
 *
 * Quand la banque renvoie vers l'adresse de retour (par exemple https://localhost/bank-callback),
 * cette page n'est jamais chargée : l'app intercepte l'adresse et en lit le code. L'adresse n'a
 * donc pas besoin d'exister. Les liens vers une autre app (l'app CIC pour valider l'opération)
 * sont ouverts normalement ; une fois la validation faite, il suffit de revenir ici.
 *
 * Si la banque refuse de s'afficher dans l'app, « Ouvrir dans le navigateur » fait la même
 * chose dans Chrome : à la fin, Chrome affiche une erreur sur l'adresse de retour ; on copie cette
 * adresse (elle contient le code) et on la colle ici.
 */
@Composable
fun BankAuthPage(
    url: String,
    redirectUrl: String,
    onRedirect: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var pasteMode by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf("") }
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(Modifier.fillMaxSize().background(Color.White)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) { Text("Annuler") }
                Text(
                    "Connexion à la banque",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                TextButton(onClick = {
                    pasteMode = true
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }) { Text("Navigateur") }
            }
            if (pasteMode) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(16.dp),
                ) {
                    Text(
                        "Termine la connexion dans le navigateur. À la fin, il affiche une page d'erreur : " +
                            "copie son adresse (elle commence par $redirectUrl) et colle-la ici.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = pasted,
                        onValueChange = { pasted = it },
                        label = { Text("Adresse de la page finale") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    TextButton(
                        onClick = { onRedirect(pasted) },
                        enabled = pasted.contains("code=") || pasted.contains("error="),
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("Valider") }
                }
            }
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { viewContext -> authWebView(viewContext, url, redirectUrl, onRedirect) },
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun authWebView(
    context: android.content.Context,
    url: String,
    redirectUrl: String,
    onRedirect: (String) -> Unit,
): WebView = WebView(context).apply {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    var finished = false

    fun intercept(target: String): Boolean {
        if (target.startsWith(redirectUrl)) {
            if (!finished) {
                finished = true
                onRedirect(target)
            }
            return true
        }
        val scheme = Uri.parse(target).scheme?.lowercase()
        if (scheme == "http" || scheme == "https") return false
        // Lien vers une autre app (validation dans l'app de la banque) : on le confie à Android.
        val intent = runCatching {
            if (scheme == "intent") Intent.parseUri(target, Intent.URI_INTENT_SCHEME) else Intent(Intent.ACTION_VIEW, Uri.parse(target))
        }.getOrNull() ?: return true
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            intent.getStringExtra("browser_fallback_url")?.let(::loadUrl)
        }
        return true
    }

    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            request.isForMainFrame && intercept(request.url.toString())

        override fun onPageStarted(view: WebView, pageUrl: String, favicon: Bitmap?) {
            if (pageUrl.startsWith(redirectUrl)) {
                view.stopLoading()
                intercept(pageUrl)
            } else {
                super.onPageStarted(view, pageUrl, favicon)
            }
        }
    }
    loadUrl(url)
}
