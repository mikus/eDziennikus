/*
 * Copyright (c) Mikolaj Olszewski 2026-9-2.
 */

package eu.mikus.edziennik.ui.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.R
import eu.mikus.edziennik.compat.blendColors
import eu.mikus.edziennik.compat.getColorFromAttr
import eu.mikus.edziennik.ui.compose.IconicsIcon
import eu.mikus.edziennik.utils.Themes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * `?colorFab` and `?colorOnFab` (`styles.xml:111,113` light, `:130,132` dark), inlined app-side on
 * purpose. Both attrs are **declared by navlib**, so reading them through the theme would leave the
 * FAB depending on the AAR a later phase removes; and an unforced M3 FAB would take its container
 * from the scheme, where `appColorScheme` (`ui/compose/theme/Theme.kt`) keeps the brand Blue
 * `primaryContainer` on every theme instead of the app's green. A one-off `Color` constant is
 * this repo's pattern (`ui/settings/SettingsScreen.kt:51`), so nothing is added to `colors.xml`.
 *
 * Deliberately **not** `?colorFabIcon` (`#c8e6c9`) for the foreground: navlib does write it onto the
 * `IconicsDrawable`, but Material 1.6.1's `iconTint` (`?colorOnSecondary`) overrides it, so today's
 * label *and* icon are both white. Taking the pale green literally would ship 2.07:1 contrast.
 */
private val FabContainerColor = Color(0xFF4CAF50)
private val FabContentColor = Color(0xFFFFFFFF)

/**
 * The bar's icons are white today in **every** theme. navlib tints both of them - the hamburger
 * (`nav_menu`) and the sheet opener (`nav_dots_vertical`) - as `IconicsDrawable.colorAttr(context,
 * R.attr.colorOnPrimary)` (`NavBottomBar$create$3` and `create$$inlined$apply$lambda$1`), and that
 * attr measures `#ffffff` in both `NavView.Light` (navlib `values.xml:98`) and `NavView.Dark`
 * (`:53`), neither of which any app theme overrides.
 *
 * Inlined rather than read back through `?attr/colorOnPrimary`, because the **app never sets that
 * attr** - navlib's themes do, and Material's own dark default for it is `#000000`. Reading it would
 * flip the dark bar's icons to black the moment the AAR's themes go. Same value as [FabContentColor],
 * different provenance (`?colorOnFab`), so deliberately not one constant.
 */
private val BarContentColor = Color(0xFFFFFFFF)

/**
 * navlib's `BadgeDrawable` hardcodes both: `mBadgePaint.setColor(-49920)` = `#FF3D00` and
 * `mTextPaint.setColor(-1)` = `#FFFFFF`, neither of them themed. M3's `Badge` would default to
 * `colorScheme.error`, which `appColorScheme` keeps at the brand Blue value on every theme, so the
 * badge is pinned for the same reason the FAB is: no palette change inside the shell swap.
 */
private val BadgeContainerColor = Color(0xFFFF3D00)
private val BadgeContentColor = Color(0xFFFFFFFF)

/** navlib posts the sheet ripple 2 s after a screen asks for it (`MainActivity.kt:1021-1027`). */
private const val SheetHintDelayMs = 2_000L

/**
 * The pulse borrows navlib's own ripple timings (`nav_view.xml:128-129`, `mrl_rippleDuration` and
 * `mrl_rippleFadeDuration`), so it is one short emphasis of the same length, not a repeating one.
 */
private const val SheetHintGrowMs = 350
private const val SheetHintShrinkMs = 200
private const val SheetHintScale = 1.3f

/** `gainAttentionFAB()`'s +1000 ms extend and +3000 ms collapse (`MainActivity.kt:1029-1039`). */
private const val FabExtendDelayMs = 1_000L
private const val FabExtendedMs = 2_000L

/**
 * The M3 replacement for navlib's `nv_bottomBar` (§7.2 of the N4a design): the drawer hamburger with
 * the unread total, the screen's FAB docked in the centre, and the bottom sheet's opener.
 *
 * M3 has no bottom bar that owns a menu button *and* a centre-docked FAB, hence this small one. The
 * FAB is rendered **here**, not in `Scaffold`'s `floatingActionButton` slot: navlib docks it into the
 * bar (`fabGravity = Gravity.CENTER`, `MainActivity.kt:235`) and the slot would float it above.
 *
 * The trailing sheet button is load-bearing. navlib's bar registered its own menu action for it
 * (`NavBottomBar$create$5` -> `NavBottomSheet.toggle()`) and **no app code opens the sheet**, so
 * without this button [ShellState.sheetVisible] is never written and all 25 contextual rows across 12
 * fragments plus the base sync row become unreachable.
 *
 * Nothing composes this yet; `AppScaffold` does, in a later task.
 *
 * @param onMenuClick opens the drawer - what `NavBottomBar$create$4` did via `NavDrawer.toggle()`.
 * @param sheetHintEnabled whether the sheet-discoverability hint may play for the current screen;
 *   pass `!app.config.ui.bottomSheetOpened`, the gate `gainAttention()` applies at
 *   `MainActivity.kt:1022`. It is a parameter because no [ShellState] field carries it and the config
 *   is not observable - see [SheetButton] for what the hint became.
 */
@Composable
fun AppBottomBar(
    state: ShellState,
    onMenuClick: () -> Unit,
    sheetHintEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    FabAttentionEffect(state)

    BottomAppBar(
        modifier = modifier,
        containerColor = barContainerColor(),
        contentColor = BarContentColor,
    ) {
        MenuButton(total = state.badges.total, onClick = onMenuClick)
        Spacer(Modifier.weight(1f))
        PrimaryActionFab(state)
        Spacer(Modifier.weight(1f))
        SheetButton(state, sheetHintEnabled)
    }
}

/**
 * Today's bar background, kept as-is in both themes, branching on the same `Themes.isDark` the app
 * branches on at `MainActivity.kt:236` so the two agree by construction:
 *
 * - **light** - `?attr/colorPrimary`, which is what `Widget.MaterialComponents.BottomAppBar.Colored`
 *   (`nav_view.xml:111`) resolves its `backgroundTint` to. That attr is the app's own
 *   (`styles.xml:106` light, `:125` dark) and every palette theme sets it, so it is read, not inlined.
 * - **dark** - `blendColors(?attr/colorSurface, R.color.colorSurface_4dp)`, the override at
 *   `MainActivity.kt:237-240`, resolved the same way for the same reason (`colorSurface` differs per
 *   palette: `#333333`, `#121212` black, and one per colour theme).
 *
 * The 4 dp elevation at `MainActivity.kt:241` has no knob to port: M3's [BottomAppBar] exposes only
 * `tonalElevation`, a colour lift that `Surface` applies **only** when the container colour is
 * `colorScheme.surface`, so it would be inert against an explicit colour. Nothing is lost -
 * `colorSurface_4dp` *is* the elevation overlay for 4 dp, so the blend already carries that lift.
 *
 * NOTE for the AAR-removal phase: `R.color.colorSurface_1dp ... _24dp` are **navlib's own**
 * resources, not the app's. This is now their second app-side reader, after `MainActivity.kt:239`.
 */
@Composable
private fun barContainerColor(): Color {
    val context = LocalContext.current
    // Resolved once per context: a theme change goes through the Activity-recreate path (see
    // `ui/compose/theme/Theme.kt`'s `appColorScheme` KDoc), so a new theme always brings a new one.
    return remember(context) {
        if (Themes.isDark)
            Color(
                blendColors(
                    getColorFromAttr(context, R.attr.colorSurface),
                    ContextCompat.getColor(context, R.color.colorSurface_4dp),
                )
            )
        else
            Color(getColorFromAttr(context, R.attr.colorPrimary))
    }
}

/**
 * `gainAttentionFAB()`, moved off the view. The original is three uncancelled `postDelayed` calls on
 * the `NavView` (`MainActivity.kt:1029-1039`) that **outlive the screen that started them**, so a
 * pulse can land on the next screen's FAB; keying the effect on [ShellState.selectedTarget] means
 * navigating away cancels it. That is a deliberate behaviour fix.
 *
 * It waits for the first non-null [ShellState.fab] of the current screen instead of keying on the
 * fab itself. Keying on the fab would replay the pulse every time a screen re-arms one, which
 * `TimetableFragment.kt:202` does on **every page swipe** (`setScreenFab(todayFab.takeIf { ... })`)
 * and which `MessageFragment.kt:98-99` already carries an `armedFor` latch to prevent - a
 * user-visible replay this project has been bitten by once.
 *
 * Behaviour change to note: 6 of the 7 `setScreenFab` callers also call `gainAttentionFAB()`, so for
 * them this is identical; `TimetableFragment` is the seventh and now gets one pulse per visit. The
 * seam method itself therefore has nothing left to do - wiring it up is a later task's call.
 */
@Composable
private fun FabAttentionEffect(state: ShellState) {
    LaunchedEffect(state.selectedTarget) {
        state.fabExtended = false
        snapshotFlow { state.fab != null }.first { it }
        delay(FabExtendDelayMs)
        state.fabExtended = true
        delay(FabExtendedMs)
        state.fabExtended = false
    }
}

/** The hamburger, badged with the unread total. */
@Composable
private fun MenuButton(total: Int, onClick: () -> Unit) {
    // `totalBadgeText` and not `badgeText`: the hamburger total is the one badge navlib does NOT
    // clamp to "99+" - it passes `String.valueOf(total)` to its `BadgeDrawable` (§7.9).
    val badge = totalBadgeText(total)

    IconButton(onClick = onClick) {
        BadgedBox(
            badge = {
                if (badge != null)
                    Badge(
                        containerColor = BadgeContainerColor,
                        contentColor = BadgeContentColor,
                    ) { Text(badge) }
            },
        ) {
            // No content description: navlib's bar calls `setNavigationIcon(Drawable)` and never
            // `setNavigationContentDescription`, so this button is unlabelled today as well, and the
            // app owns no string that fits. Adding one means touching `strings.xml`, which is outside
            // this task - so it is reported rather than invented.
            IconicsIcon(icon = CommunityMaterial.Icon3.cmd_menu, contentDescription = null)
        }
    }
}

/** The current screen's primary action, from `setScreenFab`; `null` renders nothing. */
@Composable
private fun PrimaryActionFab(state: ShellState) {
    val fab = state.fab ?: return
    val label = stringResource(fab.labelRes)
    val extended = state.fabExtended

    ExtendedFloatingActionButton(
        text = { Text(label) },
        // Collapsed, the label is not rendered, so the icon has to carry it; expanded, the text
        // already does and a description would make TalkBack say it twice.
        icon = { IconicsIcon(icon = fab.icon, contentDescription = label.takeUnless { extended }) },
        onClick = fab.onClick,
        expanded = extended,
        containerColor = FabContainerColor,
        contentColor = FabContentColor,
    )
}

/**
 * The sheet opener, and the app's only route into the bottom sheet.
 *
 * It also carries what `gainAttentionOnBottomBar()` used to do. That call rippled a
 * `MaterialRippleLayout` (`nav_view.xml:120-124`: 100 dp x `?actionBarSize`, `bottom|end`) at
 * `(width - 28 dp, height - 28 dp)` - i.e. exactly over **this** button, the sheet opener, not the
 * hamburger. There is no Compose equivalent of a point-targeted ripple (§7.10), so the affordance
 * becomes a short scale pulse on the same button: same place, same purpose (sheet discoverability),
 * same 2 s delay, and it cancels on navigation instead of firing into the next screen.
 */
@Composable
private fun SheetButton(state: ShellState, hintEnabled: Boolean) {
    var hinting by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (hinting) SheetHintScale else 1f,
        animationSpec = tween(if (hinting) SheetHintGrowMs else SheetHintShrinkMs),
    )

    // Keyed on the current screen, so leaving cancels a pending hint. `hinting` is cleared at the
    // start rather than in a `finally`: a cancelled body cannot suspend, and a restart of this same
    // effect is the only way the button can still be scaled up when the pulse did not finish.
    LaunchedEffect(state.selectedTarget, hintEnabled) {
        hinting = false
        if (!hintEnabled)
            return@LaunchedEffect
        delay(SheetHintDelayMs)
        hinting = true
        delay(SheetHintGrowMs.toLong())
        hinting = false
    }

    IconButton(onClick = { state.sheetVisible = true }, modifier = Modifier.scale(scale)) {
        // navlib titled its own menu action with a hardcoded English "Menu"
        // (`Menu.add(0, -1, 0, "Menu")`); `R.string.more` is the app's own, localized, equivalent.
        IconicsIcon(
            icon = CommunityMaterial.Icon.cmd_dots_vertical,
            contentDescription = stringResource(R.string.more),
        )
    }
}
