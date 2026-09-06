/*
 * Copyright (c) Mikolaj Olszewski 2026-9-2.
 */

package eu.mikus.edziennik.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.mikus.edziennik.R
import eu.mikus.edziennik.compat.getColorFromAttr
import eu.mikus.edziennik.ui.compose.IconicsIcon

// ---- rail metrics ----

/**
 * `material_drawer_item_mini.xml` read against `materialdrawer-8.3.3` `values.xml`:
 *
 * | | |
 * |---|---|
 * | item | `material_mini_drawer_item` = 72 dp, used for **both** width and height - the item is square |
 * | item padding | `..._item_padding_sides` = 8 dp horizontal, `..._item_padding` = 4 dp vertical |
 * | icon view | `..._item_icon` = 56 dp with `..._item_icon_padding` = 16 dp, so the drawn glyph is 56 - 2*16 = **24 dp** |
 * | badge | `layout_gravity="top|end"`, text `material_drawer_item_badge_text` = 12 sp |
 *
 * `MiniDrawerItem.bindView` then re-applies the same insets in code (offsets 445-481:
 * `setPadding(material_drawer_padding = 8dp, material_mini_drawer_item_padding = 4dp, ...)`), so the
 * layout and the code agree and the padded content region is exactly 56 x 64 dp. That region is what
 * [RailItemContentWidth] / [RailItemContentHeight] are: the 56 dp icon view sits `center` inside it and
 * the badge sits at its top-end, which is the whole of the mini item's layout.
 *
 * **Rail width.** `nav_view.xml`'s `nv_miniDrawerContainerPortrait` is `wrap_content` and
 * `NavDrawer.decideDrawerMode$navlib_release` adds the slider with a bare `addView(View)` (offset 270),
 * i.e. `FrameLayout`'s default `MATCH_PARENT` params - so the container wraps its 72 dp items, exactly
 * as the layout's own `tools:layout_width="72dp"` annotation says. The same 72 dp is the drawer drag
 * margin navlib installs while the rail is up (offsets 283-298, `72 * density`, against 20 dp with the
 * rail off).
 *
 * M3's own numbers differ and are deliberately overridden from outside, the way `AppDrawer` overrides
 * [androidx.compose.material3.NavigationDrawerItem]'s height: [NavigationRail]'s container is
 * `widthIn(min = NavigationRailCollapsedTokens.NarrowContainerWidth)` = 80 dp and each
 * [NavigationRailItem] is `defaultMinSize(minHeight = 56 dp).widthIn(min = 80 dp)`. In both cases the
 * caller's `modifier` is the **outermost** link of the chain
 * (`DefaultNavigationRailOverride.NavigationRail` puts it on the `Surface`; `NavigationRailItem` starts
 * its chain at the `modifier` parameter), so a fixed `width` / `size` wins.
 */
private val RailWidth = 72.dp
private val RailItemSize = 72.dp
private val RailItemContentWidth = 56.dp
private val RailItemContentHeight = 64.dp

/** The glyph inside the 56 dp icon view - see [RailWidth]. Equal to M3's own `IconSize` token. */
private const val RailIconSize = 24

/**
 * `nv_miniDrawerElevation`: a 4 dp `View` backgrounded with navlib's `drawable/shadow_right`, a linear
 * gradient at `angle="0"` (start to end) from **`#40000000`** to **`#00000000`**.
 *
 * It belongs to the rail: `decideDrawerMode$navlib_release` (offsets 886-912) sets its visibility to
 * `VISIBLE` when `drawerMode` is `1` (mini) or `2` (permanent) and `GONE` otherwise. The permanent-mode
 * half is `AppDrawer`'s side of the same shim and is not reproduced here.
 */
private val RailShadowWidth = 4.dp
private val RailShadowColors = listOf(Color(0x40000000), Color(0x00000000))

// ---- badges ----

/**
 * The rail's badge is the **same single `BadgeStyle`** `AppDrawer` documents: navlib builds one in
 * `NavDrawer.init` (offsets 33-57) as `#FFFFFF` on `#D32F2F` and `MainActivity.kt:1076` hands that one
 * instance to every badgeable row. `MiniDrawerItem(PrimaryDrawerItem)` copies both `badge` and
 * `badgeStyle` off the drawer item it mirrors (ctor offsets 40-51) and `bindView` applies it through
 * `BadgeStyle.style` (offsets 300-318), which writes `paddingLeftRight` = 3 dp, `paddingTopBottom` =
 * 2 dp and `minWidth` = 20 dp, leaves the chip at `drawable/material_drawer_badge`'s 5 dp radius, and
 * sets the text size **only** if `textSizeSp != null` - navlib never sets it, so the size comes from
 * the host layout: `material_drawer_item_badge_text` = 12 sp.
 *
 * Hardcoded there, so hardcoded here, for the reason [colorAttr] gives.
 *
 * Duplicated from `AppDrawer` rather than shared because that file's copies are `private` (file-scoped)
 * and this task may not edit it; promoting them to `internal` is a follow-up, not a behaviour change.
 */
private val RailBadgeContainerColor = Color(0xFFD32F2F)
private val RailBadgeContentColor = Color(0xFFFFFFFF)
private val RailBadgeShape = RoundedCornerShape(5.dp)
private val RailBadgePaddingHorizontal = 3.dp
private val RailBadgePaddingVertical = 2.dp
private val RailBadgeMinWidth = 20.dp
private val RailBadgeTextSize = 12.sp

/**
 * The M3 replacement for MaterialDrawer's `MiniDrawerSliderView` (§7.6 of the N4a design): the 72 dp
 * icon rail shown in [DrawerMode.Mini], holding the nav rows the user picked in `MiniMenuConfigDialog`.
 *
 * Nothing composes this yet; `AppScaffold` does, in a later task, as a conditional `Row` sibling of the
 * `Scaffold` (design §5 - which calls it `AppNavigationRail`; this is that composable). It renders the
 * rail *container*, not just its contents, so the caller only has to place it.
 *
 * **A plain [NavigationRail], not `WideNavigationRail`.** §7.6 asks for the wide one to be evaluated
 * first, and it is genuinely available - material3 1.4.0 ships `WideNavigationRail`,
 * `WideNavigationRailItem` and `ModalWideNavigationRail`, all public and none experimental (they
 * compile with no `@OptIn`), and `WideNavigationRailItem`'s `label` is nullable, so an icon-only wide
 * rail is expressible. It is still the wrong fit, and every reason is a metric:
 * `WideNavigationRailKt.CollapsedRailWidth` is `NavigationRailCollapsedTokens.ContainerWidth` =
 * **96 dp** against this rail's 72, `WNRVerticalPadding` is that token's `TopSpace` = **44 dp** of
 * dead space above the first item, and `ItemHorizontalPadding` = **20 dp** eats into a 56 dp content
 * box - none of the three is a parameter. On top of that `railExpanded` is a *required* parameter
 * with no default, and there is nothing here to drive it: navlib's rail never expands, because
 * tapping an item navigates and only the (dropped, see below) profile avatar opened the drawer.
 * So §7.6's fallback applies.
 *
 * **Contents** are the top-level [DrawerEntry.Row]s with `hiddenInRail == false`, which is exactly what
 * `generateMiniDrawerItem` keeps: it maps a `PrimaryDrawerItem` unless `hiddenInMiniDrawer`, a
 * `SecondaryDrawerItem` only when `includeSecondaryDrawerItems` - which `NavDrawer.init` sets to
 * `false` (offset 211) - and returns `null` for everything else, so `DividerDrawerItem` and the "More"
 * `ExpandableDrawerItem` (a `BaseDescribeableDrawerItem`, neither primary nor secondary) both vanish.
 * Iterating only [DrawerEntry.Row] reproduces that without a second filter: `buildDrawerEntries` sorts
 * by `NavTargetLocation`, so `DRAWER_MORE` rows are only ever [DrawerEntry.Expandable] children and can
 * never reach the rail even if a stale `miniMenuButtons` set names one.
 *
 * **No profile avatar, deliberately.** MaterialDrawer puts one at the top of its rail
 * (`createItems` feeds `accountHeader.activeProfile` through `generateMiniDrawerItem`, which builds a
 * `MiniProfileDrawerItem`, and navlib's own rail listener answers a tap on it with
 * `profileSelectionOpen()` + `open()`). §7.6 drops it because `AppTopBar`'s avatar is kept in every
 * mode and already does that job. **Do not "restore" it** - it would be a second, redundant entry point
 * to the same profile switcher.
 *
 * @param onAction receives [DrawerAction.Navigate] and [DrawerAction.CloseOnly]. Unlike `AppDrawer`'s
 *   there is no drawer to close here, so `CloseOnly` is the no-op arm - which is the point: MaterialDrawer
 *   gates the rail's already-selected tap itself (`MiniDrawerSliderView$createItems$2`,
 *   `169: isSelected -> 174: ifne`, returning before `setSelection` ever fires the app's listener), and
 *   [drawerRowAction] is where that decision lives.
 */
@Composable
fun AppRail(
    state: ShellState,
    onAction: (DrawerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = colorAttr(R.attr.colorSurface)
    val unselectedIcon = colorAttr(android.R.attr.textColorSecondary)
    val selectedIcon = colorAttr(R.attr.colorPrimary)

    // The container colour is painted here, not handed to `NavigationRail`, because navlib's rail is a
    // `match_parent`-height container with a *scrolling* list inside it: the `verticalScroll` below
    // measures the rail's own `Surface` against an unbounded height, so it wraps its items and would
    // leave everything under a short list unpainted. `Row` also puts the elevation shim beside the rail
    // the way `nav_view.xml` does, as a sibling in a horizontal `LinearLayout`.
    Row(
        modifier
            .fillMaxHeight()
            .background(container),
    ) {
        NavigationRail(
            // Up to 12 rows can be enabled (9 `DRAWER` + 3 `DRAWER_BOTTOM` targets, and
            // `MiniMenuConfigDialog` offers every one of them), i.e. 864 dp of items - so the rail
            // scrolls, as navlib's `RecyclerView` does. `NavigationRail`'s own `Column` cannot, hence
            // the modifier.
            modifier = Modifier
                .width(RailWidth)
                .verticalScroll(rememberScrollState()),
            containerColor = Color.Transparent,
            // Passed explicitly: `contentColorFor(Transparent)` is `Color.Unspecified`. This is the
            // unselected icon colour, which is also what tints the item ripple.
            contentColor = unselectedIcon,
            // None, not `NavigationRailDefaults.windowInsets`. navlib padded its rail vertically
            // (`MiniDrawerSliderView`'s listener sets the recycler's top/bottom padding from the
            // system window insets) because its rail spanned the window; this one sits inside
            // `Scaffold`'s content, whose padding is the top and bottom bar heights and already
            // includes those insets. `Scaffold` does not consume them, so asking for them here
            // applies them twice - a status-bar height of dead space above the first rail icon.
            windowInsets = WindowInsets(0),
        ) {
            for (entry in state.drawerEntries)
                if (entry is DrawerEntry.Row && !entry.row.hiddenInRail)
                    RailItem(entry.row, state, onAction, selectedIcon, unselectedIcon)
        }

        Box(
            Modifier
                .width(RailShadowWidth)
                .fillMaxHeight()
                .background(Brush.horizontalGradient(RailShadowColors)),
        )
    }
}

/**
 * One `MiniDrawerItem`.
 *
 * The badge is [badgeText] - the rail clamps at `"99+"` and vanishes at zero like the drawer rows, not
 * like the bottom bar's unclamped hamburger total (§7.9).
 *
 * **`indicatorColor` is transparent because navlib's rail has no selected background at all.**
 * `MiniDrawerSliderView.enableSelectedMiniDrawerItemBackground` defaults to `false` (its constructor
 * assigns only `enableProfileClick`) and navlib never sets it, so `generateMiniDrawerItem` builds every
 * item `withEnableSelectedBackground(false)` and `MiniDrawerItem.bindView` skips `themeDrawerItem`
 * entirely (offset 237). The selected state shows *only* in the icon tint: `bindView` reads
 * `getIconColor(ctx)` (offset 225) = `BaseDrawerItem.getIconColor` = `UtilsKt.getPrimaryDrawerIconColor`
 * = `createDrawerItemColorStateList(ctx, materialDrawerPrimaryIcon)`, i.e.
 * `?android:textColorSecondary` with the selected and checked states replaced by `?colorPrimary`
 * (`UtilsKt` offsets 50-65) - the same pair `AppDrawer`'s row icons use.
 *
 * **Not** reproduced: the mini item's `FrameLayout` has no background whatsoever (no `themeDrawerItem`,
 * no `getSelectableBackground`), so navlib's rail gives no touch feedback at all. [NavigationRailItem]'s
 * ripple is kept - it is drawn from the rail's `contentColor`, i.e. the `?android:textColorSecondary`
 * resolved above, not a scheme role, so it cannot drift; an icon-only rail with no press feedback is not
 * worth reproducing. M3's `Arrangement.spacedBy(4 dp)` between items and its 4 dp of container padding
 * are also kept: navlib's items are flush, and neither is a [NavigationRail] parameter.
 */
@Composable
private fun RailItem(
    row: DrawerRow,
    state: ShellState,
    onAction: (DrawerAction) -> Unit,
    selectedIcon: Color,
    unselectedIcon: Color,
) {
    val selected = row.target != null && row.target == state.selectedTarget
    val badge = badgeText(row.target?.let { state.badges.perTarget[it.id] } ?: 0)
    // The rail has no labels, so unlike `AppDrawer`'s rows - where `material_drawer_icon` carries no
    // description because the label beside it names the row - the name has to ride on the icon.
    val name = stringResource(row.nameRes)

    NavigationRailItem(
        selected = selected,
        onClick = { onAction(drawerRowAction(row, state.selectedTarget)) },
        icon = {
            // The mini item's padded content region - see RailWidth's table. Handing it to the icon slot
            // whole, inside a 72 dp item, is what places the glyph and the badge where navlib's
            // `layout_gravity="center"` and `"top|end"` place them.
            Box(Modifier.size(width = RailItemContentWidth, height = RailItemContentHeight)) {
                if (row.icon != null)
                    IconicsIcon(
                        icon = row.icon,
                        contentDescription = name,
                        sizeDp = RailIconSize,
                        tint = if (selected) selectedIcon else unselectedIcon,
                        modifier = Modifier.align(Alignment.Center),
                    )
                if (badge != null)
                    Box(Modifier.align(Alignment.TopEnd)) { RailBadge(badge) }
            }
        },
        modifier = Modifier.size(RailItemSize),
        colors = NavigationRailItemDefaults.colors(
            selectedIconColor = selectedIcon,
            unselectedIconColor = unselectedIcon,
            indicatorColor = Color.Transparent,
        ),
    )
}

/** navlib's one `BadgeStyle` at the mini item's own text size - see [RailBadgeContainerColor]. */
@Composable
private fun RailBadge(text: String) {
    Text(
        text = text,
        color = RailBadgeContentColor,
        fontSize = RailBadgeTextSize,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .widthIn(min = RailBadgeMinWidth)
            .background(RailBadgeContainerColor, RailBadgeShape)
            .padding(horizontal = RailBadgePaddingHorizontal, vertical = RailBadgePaddingVertical),
    )
}

/**
 * One theme attr as a [Color], resolved once per context - the same reader `AppDrawer`, `AppSheet` and
 * `AppBottomBar` use. The container is resolved from the XML theme rather than the Compose scheme;
 * since Phase 34 the two agree on `surface`, so that read is belt-and-braces rather than load-bearing.
 *
 * `?colorSurface` is where the rail's container colour actually comes from:
 * `MiniDrawerSliderView`'s constructor reads `materialDrawerBackground` out of
 * `Widget.MaterialDrawerStyle` (offsets 20-50) and that attr is `?colorSurface`, the same attr
 * `AppDrawer`'s sheet reads. navlib *also* gives `nv_miniDrawerContainerPortrait` a
 * `@color/colorSurface_4dp` = **`#17FFFFFF`** background, but the slider is added `MATCH_PARENT` over it
 * and `?colorSurface` is opaque in every theme, so that white wash never shows - recorded here rather
 * than dropped silently, and rather than composited in as an "improvement".
 */
@Composable
private fun colorAttr(attr: Int): Color {
    val context = LocalContext.current
    return remember(context, attr) { Color(getColorFromAttr(context, attr)) }
}
