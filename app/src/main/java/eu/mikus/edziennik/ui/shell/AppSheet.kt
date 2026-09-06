/*
 * Copyright (c) Mikolaj Olszewski 2026-9-2.
 */

package eu.mikus.edziennik.ui.shell

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.material.elevation.ElevationOverlayProvider
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.R
import eu.mikus.edziennik.compat.getColorFromAttr
import eu.mikus.edziennik.ui.compose.IconicsIcon
import kotlinx.coroutines.launch

/**
 * `nav_bs_item_primary.xml`, measured: the row is a fixed `48dp` tall, `16dp` of horizontal padding,
 * a `24dp` icon with `16dp` of end margin. The icon size is doubly confirmed - the layout says 24 dp
 * *and* `BottomSheetPrimaryItem.bindViewHolder` calls `setSizeDp(24)` on the `IconicsDrawable`.
 *
 * [RowMinHeight] is a `heightIn(min = ...)`, not the layout's fixed `height`: identical at the
 * default font scale (a two-line row measures ~36 dp of text, well inside 48 dp), and it lets the
 * four `ScreenAction`s that do carry a `descriptionRes` (`HomeworkFragment.kt:73`,
 * `AgendaFragment.kt:69`, `TimetableFragment.kt:158,165`) grow instead of clipping at large font
 * scales, which the fixed height does today.
 */
private val RowMinHeight = 48.dp
private val RowPadding = 16.dp
private const val IconSize = 24
private val IconGap = 16.dp

/** `bs_content`'s own `paddingHorizontal="8dp"`, so a row's icon starts 8 + 16 dp from the edge. */
private val ContentPadding = 8.dp

/**
 * `nav_bs_item_separator.xml`: a `17dp` block with a centred `1dp` line, coloured
 * `@color/material_drawer_divider`.
 *
 * That colour is **MaterialDrawer's**, `#1F000000` (`materialdrawer-8.3.3` `values.xml:3`), with no
 * `values-night` variant - so navlib's separator is 12 % *black* in the dark themes too, where it is
 * all but invisible. Reproduced as the literal it is rather than mapped to `colorScheme.outline`
 * (or `HorizontalDivider`'s `outlineVariant` default): `appColorScheme` keeps both at the brand Blue
 * values on every theme, neither of which is 12 % black, and a shell swap must not repaint.
 */
private val SeparatorBlockHeight = 17.dp
private val SeparatorThickness = 1.dp
private val SeparatorColor = Color(0x1F000000)

/**
 * `bs_scrim`'s `android:background="#99000000"` (`nav_bottom_sheet.xml`) - 60 % black. M3's default
 * is `BottomSheetDefaults.ScrimColor`, i.e. `colorScheme.scrim` at 32 %; `appColorScheme` keeps that
 * role black on every theme, so only the opacity differs. Pinned as [SeparatorColor] is.
 */
private val ScrimColor = Color(0x99000000)

/**
 * `bottom_sheet_background.xml`: `16dp` top corners only. M3 would use
 * `BottomSheetDefaults.ExpandedShape` (`extraLarge`, 28 dp), a visible change in a phase that is
 * only meant to swap the mechanism.
 */
private val SheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)

/**
 * `@style/width_max_600dp` - `match_parent` in `values/`, `600dp` in `values-w600dp-v13/`. Below
 * 600 dp of width the two agree, so one value covers both. M3's default is 640 dp.
 */
private val SheetMaxWidth = 600.dp

/**
 * The elevation navlib composites its container colour at: `NavBottomSheet.create` tints
 * `bs_content`'s background with `PorterDuffColorFilter(elevateSurface(context, 8), SRC_ATOP)`.
 */
private const val SheetElevationDp = 8f

/**
 * The M3 replacement for navlib's `nav_bottom_sheet` (§7.3 of the N4a design).
 *
 * **The sheet has two row lists, not one**, because navlib's `isContextual` flag - the thing that
 * let `removeAllContextual()` wipe the screen's own rows while keeping the shell's - dies with the
 * mapper:
 *
 * - **contextual** rows come from [ShellState.actions] (`setScreenActions`, which 12 fragments call)
 *   and are replaced wholesale on every navigation. They render **first**, because
 *   `setScreenActions` *prepends* today (`MainActivity.kt:1179`).
 * - **base** rows are the shell's own, rebuilt on navigation because they depend on the current
 *   target: the sync row, a separator, then the `BOTTOM_SHEET` `NavTarget`s
 *   (`MainActivity.kt:432-451`).
 *
 * The **sync row is rendered here, from [onSyncClick]**, rather than arriving inside [baseRows]:
 * `MainActivity.kt:440` is the only call site of `SyncViewListDialog` in the whole app, this project
 * has already shipped a regression on exactly that row, and a row the caller cannot forget to pass
 * cannot be dropped again. [baseRows] therefore carries only the `BOTTOM_SHEET` targets - see the
 * parameter docs for who filters them.
 *
 * Nothing composes this yet; `AppScaffold` does, in a later task. It takes callbacks rather than the
 * `MainActivity` so that it compiles before `MainActivity` is rewired - `SyncViewListDialog`'s
 * constructor needs the activity, and so does `navigate()`.
 *
 * Per §7.10 the drag-up-from-the-bottom-bar gesture is retired and [ModalBottomSheet]'s own drag
 * handle stands in for `bs_dragBar` (measured, for the record: 32 x 4 dp, 2 dp radius, `#DEDEDE`,
 * 8 dp above / 16 dp below). That is an accepted trade, not an oversight.
 *
 * @param baseRows the `BOTTOM_SHEET` `NavTarget` rows, in `NavTarget` order, `devModeOnly` ones
 *   already dropped when `!App.devMode` - the caller owns that filter, exactly as it owns
 *   `buildDrawerEntries`' inputs. Today that list is at most one row (`NavTarget.DEBUG`, dev-only),
 *   so it is empty in a release build.
 * @param onSyncClick shows `SyncViewListDialog(activity, navTarget)`. The close happens here; this
 *   must not close the sheet itself.
 * @param onDismissed latches `app.config.ui.bottomSheetOpened = true` **and** clears
 *   [ShellState.sheetVisible]. It gates the bottom-bar attention hint (`AppBottomBar`'s
 *   `sheetHintEnabled`), so a sheet that never reports its dismissal makes that hint fire forever.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(
    state: ShellState,
    baseRows: List<SheetRow>,
    onSyncClick: () -> Unit,
    onDismissed: () -> Unit,
) {
    if (!state.sheetVisible)
        return

    // `open()` is `setState(STATE_EXPANDED)` and `close()` is `setState(STATE_HIDDEN)` - navlib
    // never rests at the half-expanded height, so neither does this.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // navlib's `onCloseListener` fires from the behaviour callback on STATE_HIDDEN
    // (`NavBottomSheet$create$2`, offset 7), i.e. once the sheet is gone, and for *every* close
    // path - row tap, scrim tap, swipe-down, back. Both paths below land here, once each.
    val dismiss = {
        onDismissed()
        state.sheetVisible = false
    }

    // The close-before-onClick rule (`MainActivity.kt:1178`: `bottomSheet.close(); it.onClick()`),
    // in the one place every row goes through. The close is *requested*, not awaited - as
    // `setState(STATE_HIDDEN)` is - so the click body still runs on the same frame and the sync
    // dialog appears as immediately as it does today.
    val closeThen: (() -> Unit) -> Unit = { onClick ->
        scope.launch { sheetState.hide() }.invokeOnCompletion { dismiss() }
        onClick()
    }

    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = sheetState,
        sheetMaxWidth = SheetMaxWidth,
        shape = SheetShape,
        containerColor = sheetContainerColor(),
        scrimColor = ScrimColor,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ContentPadding),
        ) {
            // `toSheetRows`' lambda is the identity here on purpose: the close belongs to the row
            // body below, so that base and contextual rows cannot diverge on it.
            SheetRows(state.actions.toSheetRows { it.onClick() }, closeThen)

            SheetItem(
                titleRes = R.string.menu_sync,
                descriptionRes = null,
                icon = CommunityMaterial.Icon.cmd_download_outline,
                onClick = { closeThen(onSyncClick) },
            )

            // `BottomSheetSeparatorItem(false)` (`MainActivity.kt:445`), but only when something
            // follows it. navlib appends it unconditionally, so a release build shows a stray line
            // under the sync row today; that is the one thing here not reproduced.
            if (baseRows.isNotEmpty())
                Separator()

            SheetRows(baseRows, closeThen)
        }
    }
}

/**
 * `elevateSurface(context, 8)` = `ElevationOverlayProvider(context)` compositing
 * `?attr/elevationOverlayColor` over `?attr/colorSurface` at the M2 overlay alpha for 8 dp
 * (`(4.5 * ln(1 + 8) + 2) / 100` = 11.89 %). The drawable's own `#ffffff` never shows: the
 * `SRC_ATOP` filter replaces it and keeps only its rounded-rect alpha.
 *
 * Calling Material's provider rather than blending by hand, unlike `AppBottomBar`'s
 * `barContainerColor()`: there the *app* writes the blend (`MainActivity.kt:237-240`), so the app's
 * own expression was the faithful one. Here navlib does, and this is its literal code path - so it
 * cannot drift, and `com.google.android.material:material:1.6.1` is a direct app dependency that
 * outlives the AAR. `R.color.colorSurface_8dp` (`#1fffffff`) is navlib's tabulated stand-in for the
 * same overlay and would be 1/255 of alpha off *and* disappear with the AAR.
 *
 * Not `colorScheme.surfaceContainerLow`, [ModalBottomSheet]'s default: `colorSurface` is overridden
 * by every one of the 20-odd palette themes (`styles.xml:147+`), and the M3 role has no equal here -
 * `appColorScheme` derives it off `surface` along a luminance ramp, not from navlib's overlay blend.
 *
 * Note that navlib calls `compositeOverlay`, not `compositeOverlayIfNeeded`, so the overlay is
 * applied in the light themes too. That used to be invisible - navlib's `elevationOverlayColor` was
 * `#ffffff` over a white-ish `colorSurface` - but Phase 33b dropped that pin, so the light themes
 * now take M3's `?attr/colorPrimary` and the light sheet carries a faint brand tint like the dark
 * one. Reproducing the *call* keeps the sheet in step with whatever a palette sets.
 */
@Composable
private fun sheetContainerColor(): Color {
    val context = LocalContext.current
    // Resolved once per context, as `barContainerColor()` is: a theme change goes through the
    // Activity-recreate path, so a new theme always brings a new context.
    return remember(context) {
        val provider = ElevationOverlayProvider(context)
        val elevationPx = SheetElevationDp * context.resources.displayMetrics.density
        Color(provider.compositeOverlay(provider.themeSurfaceColor, elevationPx))
    }
}

/**
 * One [SheetRow] list. [SheetRow.separatorBefore] draws the separator *above* its row, which is how
 * `toBottomSheetItems` emitted `BottomSheetSeparatorItem` + item as a pair
 * (`MainActivity.kt:1256-1259`).
 */
@Composable
private fun SheetRows(rows: List<SheetRow>, closeThen: (() -> Unit) -> Unit) {
    for (row in rows) {
        if (row.separatorBefore)
            Separator()
        SheetItem(
            titleRes = row.titleRes,
            descriptionRes = row.descriptionRes,
            icon = row.icon,
            onClick = { closeThen(row.onClick) },
        )
    }
}

/**
 * `BottomSheetPrimaryItem`, measured from `bindViewHolder` rather than read off the layout, because
 * the code overrules it:
 *
 * - the **title** is set to `getColorFromAttr(context, android.R.attr.textColorPrimary)` explicitly
 *   at bind time (offset 222), which beats every style in the chain;
 * - the **icon** is `colorAttr(context, android.R.attr.textColorSecondary)` (`bindViewHolder$1`,
 *   offset 40) at `setSizeDp(24)` - *not* `?colorIcon`, and not tinted by the row's content colour;
 * - the **description** keeps its layout appearance, `@style/NavView.TextView.Small` (parent
 *   `TextAppearance.AppCompat.Small` -> `?android:textColorSecondary`) with the view's own
 *   `textSize=12sp` / `fontFamily=sans-serif` overriding the style's 14 sp medium.
 *
 * Both text colours are **read**, not inlined: they are the app's (`#db000000` / `#99000000` light,
 * `#ffffffff` / `#99ffffff` dark, navlib `values.xml:50-51,90-91`), every theme in the app inherits
 * them, and unlike navlib's `colorOnPrimary` they are platform attrs with sane framework defaults -
 * so they survive the AAR's removal.
 *
 * The two type roles do line up with M3 for once, so they are used as roles: `bodyMedium` is 14 sp
 * (title, plus [FontWeight.Medium] for `sans-serif-medium`) and `bodySmall` is 12 sp (description).
 */
@Composable
private fun SheetItem(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int?,
    icon: IIcon,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val titleColor = remember(context) {
        Color(getColorFromAttr(context, android.R.attr.textColorPrimary))
    }
    val secondaryColor = remember(context) {
        Color(getColorFromAttr(context, android.R.attr.textColorSecondary))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = RowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // No content description: navlib's `item_icon` carries `tools:ignore="ContentDescription"`
        // and the title beside it already labels the row.
        IconicsIcon(
            icon = icon,
            contentDescription = null,
            sizeDp = IconSize,
            tint = secondaryColor,
        )
        Spacer(Modifier.width(IconGap))
        Column {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (descriptionRes != null)
                Text(
                    text = stringResource(descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
        }
    }
}

/** `nav_bs_item_separator.xml` - see [SeparatorBlockHeight]. */
@Composable
private fun Separator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SeparatorBlockHeight),
        contentAlignment = Alignment.Center,
    ) {
        HorizontalDivider(thickness = SeparatorThickness, color = SeparatorColor)
    }
}
