package fr.baptiste.bank.ui.glass

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import fr.baptiste.bank.R
import fr.baptiste.bank.data.UiStyle
import fr.baptiste.bank.ui.AppDialogs
import fr.baptiste.bank.ui.AppMenuItems
import fr.baptiste.bank.ui.BudgetViewModel
import fr.baptiste.bank.ui.OpenDialog
import fr.baptiste.bank.ui.Tab
import fr.baptiste.bank.ui.glass.kit.LiquidBottomTab
import fr.baptiste.bank.ui.glass.kit.LiquidBottomTabs
import fr.baptiste.bank.ui.glass.kit.LiquidButton
import fr.baptiste.bank.ui.rememberAppController
import fr.baptiste.bank.ui.rememberUpdateAvailable
import fr.baptiste.bank.ui.theme.BankTheme
import fr.baptiste.bank.ui.theme.GlassSurfaceColors
import fr.baptiste.bank.update.UpdateViewModel

/** Thème de l'interface Liquid Glass : palette bleu et blanc, fenêtres translucides. */
@Composable
fun GlassTheme(content: @Composable () -> Unit) {
    BankTheme(glass = true, content = content)
}

/**
 * Interface Liquid Glass. Tout flotte sur un fond liquide animé :
 * - le contenu défile sur toute la hauteur de l'écran, sous les éléments flottants ;
 * - les cartes réfractent le fond (couche [LocalBackgroundBackdrop]) ;
 * - la barre d'onglets, le bouton d'action et le bouton ⋮ réfractent le fond et le contenu
 *   qui passe dessous (couche [LocalScreenBackdrop]) ;
 * - l'indicateur d'onglet se déplace comme une goutte : on peut le faire glisser d'un onglet à l'autre.
 */
@Composable
fun GlassMainScreen(
    viewModel: BudgetViewModel,
    updateViewModel: UpdateViewModel,
    onStyleChange: (UiStyle) -> Unit,
) {
    val controller = rememberAppController()
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val operations by viewModel.operations.collectAsStateWithLifecycle()
    val archived by viewModel.archivedEnvelopes.collectAsStateWithLifecycle()
    val updateAvailable = rememberUpdateAvailable(updateViewModel)
    var menuOpen by remember { mutableStateOf(false) }

    val backgroundBackdrop = rememberLayerBackdrop()
    val screenBackdrop = rememberLayerBackdrop()

    CompositionLocalProvider(
        LocalBackgroundBackdrop provides backgroundBackdrop,
        LocalScreenBackdrop provides screenBackdrop,
    ) {
        GlassSurfaceColors(onBackground = true) {
        Box(Modifier.fillMaxSize()) {
            // Couche enregistrée pour les éléments flottants : le fond liquide et le contenu.
            Box(Modifier.fillMaxSize().layerBackdrop(screenBackdrop)) {
                LiquidBackground(Modifier.layerBackdrop(backgroundBackdrop))
                AnimatedContent(
                    targetState = controller.tab,
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.98f)) togetherWith fadeOut(tween(160))
                    },
                    label = "onglet",
                ) { tab ->
                    when (tab) {
                        Tab.HOME -> GlassHomeContent(
                            dashboard = dashboard,
                            onAddExpense = { controller.dialog = OpenDialog.Expense },
                            onEditIncome = { controller.dialog = OpenDialog.Income },
                            onOpenEnvelope = { controller.dialog = OpenDialog.EnvelopeDetail(it.envelope.id) },
                            onCover = { controller.dialog = OpenDialog.Cover(it.envelope.id) },
                        )
                        Tab.ENVELOPES -> GlassEnvelopesContent(
                            dashboard = dashboard,
                            archived = archived,
                            onOpen = { controller.dialog = OpenDialog.EnvelopeDetail(it.envelope.id) },
                            onCover = { controller.dialog = OpenDialog.Cover(it.envelope.id) },
                            onEdit = { controller.dialog = OpenDialog.EnvelopeEditor(it) },
                            onRestore = {
                                viewModel.restoreEnvelope(it.id)
                                controller.notify("${it.name} est de nouveau active.")
                            },
                        )
                        Tab.OPERATIONS -> GlassOperationsContent(
                            operations = operations,
                            onDelete = viewModel::deleteExpense,
                        )
                    }
                }
            }

            // Voile sous la barre d'état, pour que le contenu ne passe pas sous l'heure.
            StatusBarScrim(
                modifier = Modifier.align(Alignment.TopCenter),
                height = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp,
            )

            // En haut à droite : l'action de l'onglet (+) et le menu ⋮, comme dans les apps iOS.
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 8.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val (label, target) = controller.primaryAction
                LiquidButton(
                    onClick = { controller.dialog = target },
                    backdrop = screenBackdrop,
                    tint = Color(0xFF2F6BFF),
                    horizontalPadding = 0.dp,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_add),
                        contentDescription = "Ajouter : $label",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Box {
                    LiquidButton(
                        onClick = { menuOpen = true },
                        backdrop = screenBackdrop,
                        surfaceColor = Color(0xFF071233).copy(alpha = 0.35f),
                        horizontalPadding = 0.dp,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box {
                            Icon(
                                painterResource(R.drawable.ic_more),
                                contentDescription = "Plus d'options",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                            if (updateAvailable) Badge(Modifier.align(Alignment.TopEnd))
                        }
                    }
                    // Le menu s'affiche dans une fenêtre à part : il reprend les couleurs « sur verre ».
                    GlassSurfaceColors(onBackground = false) {
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            AppMenuItems(
                                controller = controller,
                                viewModel = viewModel,
                                updateAvailable = updateAvailable,
                                style = UiStyle.GLASS,
                                onStyleChange = onStyleChange,
                                onClose = { menuOpen = false },
                            )
                        }
                    }
                }
            }

            // Messages de confirmation, en verre, au-dessus de la barre d'onglets.
            SnackbarHost(
                hostState = controller.snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 92.dp),
            ) { data -> GlassSurfaceColors(onBackground = false) { GlassSnackbar(data) } }

            // Barre d'onglets flottante et centrée, comme celle d'iOS 26.
            LiquidBottomTabs(
                selectedTabIndex = { controller.tab.ordinal },
                onTabSelected = { controller.tab = Tab.entries[it] },
                backdrop = screenBackdrop,
                tabsCount = Tab.entries.size,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
                    .widthIn(max = 320.dp)
                    .fillMaxWidth(0.86f),
            ) {
                Tab.entries.forEach { tab ->
                    LiquidBottomTab(onClick = { controller.tab = tab }) {
                        Icon(painterResource(tab.icon), contentDescription = null, modifier = Modifier.size(24.dp))
                        Text(tab.title, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        }

        // Les fenêtres (dialogues, panneaux) reprennent les couleurs « sur verre » du thème.
        AppDialogs(controller, viewModel, updateViewModel)
    }
}

/** Message en bas de l'écran, dans une capsule de verre. */
@Composable
private fun GlassSnackbar(data: SnackbarData) {
    val backdrop = LocalScreenBackdrop.current
    val tokens = Glass.tokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(12.dp.toPx())
                    lens(16.dp.toPx(), 24.dp.toPx())
                },
                onDrawSurface = { drawRect(tokens.surfaceStrong) },
            )
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            data.visuals.message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
        )
        val action = data.visuals.actionLabel
        if (action != null) {
            TextButton(onClick = { data.performAction() }) { Text(action) }
        } else {
            TextButton(onClick = { data.dismiss() }) { Text("OK") }
        }
    }
}
