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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ui.compose.IconicsIcon
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

    val colors = barColors()

    BottomAppBar(
        modifier = modifier,
        containerColor = colors.container,
        contentColor = colors.content,
    ) {
        MenuButton(total = state.badges.total, onClick = onMenuClick)
        Spacer(Modifier.weight(1f))
        PrimaryActionFab(state)
        Spacer(Modifier.weight(1f))
        SheetButton(state, sheetHintEnabled)
    }
}

/**
 * The bar's container and its ink as one value.
 *
 * They are produced together because [BottomAppBar] takes them as two independent arguments, and
 * moving only the container compiles, passes lint, passes every existing test and leaves
 * `theme-attrs-golden.txt` byte-identical while shipping ink the user cannot see. Grouping them is a
 * hint, not a gate, so the gate is `AppBottomBarColorsTest`. Both fields are the same type, so the
 * constructions below use named arguments - a positional transposition would otherwise compile.
 */
internal data class BarColors(val container: Color, val content: Color)

/**
 * The bar's colours, as a pure function of the scheme so `AppBottomBarColorsTest` can assert them.
 * [barColors] is its only caller.
 *
 * `surfaceContainer` is M3 1.4.0's own token for this component
 * (`BottomAppBarTokens.ContainerColor`), and since Phase 34 the scheme tracks the selected XML theme,
 * so the bar moves with the palette on all 18.
 *
 * **All 18, since the dark arm was retired.** Phase 38 moved only the 7 light themes and left the
 * other 11 on navlib's `blend(?colorSurface, colorSurface_4dp)`. That blend rendered `#454545` on the
 * Dark theme while Home cards render on `surfaceContainerHighest` `#454646` - **1.0125:1**, so the
 * card's bottom edge was invisible and the bar read as though it were covering content. Reported from
 * the field. On `surfaceContainer` `#383939` the same pair measures 1.2235:1. Retiring the branch also
 * fixes theme 13 ("Red"), which is flagged `isDark` but renders light: it took the dark arm and got
 * white ink on a pale red bar at 2.76:1, and now takes `onSurface` like everything else.
 *
 * The ink is passed explicitly rather than left to `contentColorFor`: it would be correct for a scheme
 * role, but returns `Color.Unspecified` for anything derived, and `Surface` then falls through to the
 * ambient `LocalContentColor` with no error. Passing it keeps that failure mode unreachable.
 */
internal fun barColorsFor(scheme: ColorScheme) =
    BarColors(container = scheme.surfaceContainer, content = scheme.onSurface)

@Composable
private fun barColors(): BarColors = barColorsFor(MaterialTheme.colorScheme)

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

/**
 * The hamburger, badged with the unread total.
 *
 * [BadgedBox] wraps the [IconButton] rather than sitting inside it, and that nesting is the whole
 * point of this shape. `BadgedBox` reports its own size as exactly the anchor's
 * (`Badge.kt:95-96`, `totalHeight = anchorPlaceable.height`) and then places the badge at a
 * *negative* offset - `badgeY = -badgeHeight + 14.dp`, `badgeX = anchorWidth - 12.dp`
 * (`Badge.kt:107-126`) - so the badge is designed to overflow its anchor. `IconButtonImpl` wraps its
 * content in `.clip(shape)` (`IconButton.kt`), and that shape is `CornerFull` over a 40 dp container
 * (`SmallIconButtonTokens.ContainerHeight = 40.dp`, `IconSize = 24.dp` + 2x`DefaultLeadingSpace`
 * 8 dp). With the badge inside, its anchor was the 24 dp icon, which put the badge's top-right
 * corner about 24.4 dp from the circle's 20 dp-radius centre - outside it, so the arc sliced the
 * corner off and the "1" of a two-digit count sat flush against the cut.
 *
 * Anchoring to the 40 dp button instead puts the badge at the button's corner with nothing clipping
 * it: neither the `Row` nor `BottomAppBar` clips, and the badge clears the bar's bounds by 18 dp.
 *
 * Passing `shape = RectangleShape` to [IconButton] would also stop the clipping, and was rejected:
 * the badge would then end exactly on the container's edge with no margin, and the ripple would turn
 * from a circle into a square - a visible regression to fix an invisible one.
 */
@Composable
private fun MenuButton(total: Int, onClick: () -> Unit) {
    // `totalBadgeText` and not `badgeText`: the hamburger total is the one badge navlib does NOT
    // clamp to "99+" - it passes `String.valueOf(total)` to its `BadgeDrawable` (§7.9).
    val badge = totalBadgeText(total)

    BadgedBox(
        badge = {
            if (badge != null)
                Badge(
                    containerColor = BadgeContainerColor,
                    contentColor = BadgeContentColor,
                ) { Text(badge) }
        },
    ) {
        IconButton(onClick = onClick) {
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
