package fr.baptiste.bank.ui.components

import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import fr.baptiste.bank.ui.theme.LocalGlassStyle

/**
 * En style Liquid Glass, floute l'écran derrière la fenêtre ouverte (dialogue ou panneau du bas).
 *
 * Une fenêtre est dessinée à part de l'écran principal : l'app ne peut pas la réfracter elle-même.
 * Android 12 sait en revanche flouter ce qu'il y a derrière une fenêtre (« blur behind »), quand
 * l'appareil le permet. À placer n'importe où dans le contenu de la fenêtre ; sans effet ailleurs.
 */
@Composable
fun BlurBehindWindow(radius: Dp = 48.dp) {
    if (!LocalGlassStyle.current || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val view = LocalView.current
    val radiusPx = with(LocalDensity.current) { radius.roundToPx() }
    DisposableEffect(view, radiusPx) {
        val window = generateSequence(view.parent) { (it as? View)?.parent }
            .filterIsInstance<DialogWindowProvider>()
            .firstOrNull()
            ?.window
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply { blurBehindRadius = radiusPx }
            window.setDimAmount(0.15f)
        }
        onDispose { }
    }
}
