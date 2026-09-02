/*
 * Copyright (c) Mikolaj Olszewski 2026-9-2.
 */

package eu.mikus.edziennik.ui.shell

import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.R
import eu.mikus.edziennik.compat.getColorFromAttr
import eu.mikus.edziennik.compat.getDrawableFromRes
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.ui.compose.IconicsIcon
import pl.droidsonroids.gif.GifDrawable

// ---- widths ----

/**
 * navlib uses two different widths, so [AppDrawer] takes one rather than assuming:
 *
 * - **modal** - `MaterialDrawerSliderView.onMeasure` clamps to
 *   `DrawerUtils.getOptimalDrawerWidth = min(screenWidth - actionBarHeight, material_drawer_width)`,
 *   and `material_drawer_width` is `320dp` (`materialdrawer-8.3.3` `values.xml:59`), so any screen
 *   wider than 376 dp gets exactly 320 dp. Confirmed against a shipped screenshot: the drawer covers
 *   0.778 of a 411 dp screen = 320 dp. M3's [androidx.compose.material3.DrawerDefaults]
 *   `MaximumDrawerWidth` is 360 dp, so the default would be visibly wider.
 * - **permanent** - `NavDrawer.decideDrawerMode$navlib_release` builds
 *   `FrameLayout.LayoutParams(UIUtils.convertDpToPixel(300f, context), MATCH_PARENT)` (offset 42) for
 *   `nv_drawerContainerLandscape`, i.e. **300 dp**, as design §5 records.
 */
internal val ModalDrawerWidth = 320.dp
internal val PermanentDrawerWidth = 300.dp

// ---- row metrics ----

/**
 * `material_drawer_item_primary.xml` / `_secondary.xml` and MaterialDrawer's dimens:
 * a level-1 row is `material_drawer_item_primary` = 48 dp tall with a `material_drawer_item_primary_icon`
 * = 56 dp icon column whose `..._icon_padding_right` = 32 dp leaves a **24 dp** icon; a level-2 row is
 * `material_drawer_item_secondary` = 42 dp with `..._secondary_icon_padding_right` = 36 dp, leaving
 * **20 dp**. `ExtensionsKt.setDrawerVerticalPadding(view, level)` then sets
 * `paddingRelative(16dp * level, 0, 16dp, 0)` (`BaseDescribeableDrawerItem.bindView` offset 357), which
 * is where [LevelIndent] comes from.
 *
 * The selected background is **not** M3's pill: `DrawerUtils.themeDrawerItem$default` insets it by
 * `material_drawer_item_background_padding_start_end` = 8 dp and `..._top_bottom` = 4 dp, and
 * `AbstractDrawerItem.getShapeAppearanceModel` gives it `withCornerSize(material_drawer_item_corner_radius)`
 * = **4 dp**. [NavigationDrawerItem]'s own default is `CircleShape`.
 */
private val RowHeight = 48.dp
private val SubRowHeight = 42.dp
private const val RowIconSize = 24
private const val SubRowIconSize = 20
private val RowInsetHorizontal = 8.dp
private val RowInsetVertical = 4.dp
private val RowShape = RoundedCornerShape(4.dp)
private val LevelIndent = 16.dp

/** `material_drawer_item_primary_text` / `..._primary_description`, both 14 sp. */
private val RowTextSize = 14.sp

/**
 * `material_drawer_padding` = 8 dp, MaterialDrawer's general-purpose gap. It is the divider's
 * `layout_margin` (`material_drawer_item_divider.xml`, a `1dp` `View`) and the badge's
 * `layout_marginStart` in both profile layouts, so it is one constant.
 *
 * The same divider block sits under the account header: `MaterialDrawerSliderView`'s `_headerDivider`
 * defaults to `true` (constructor offset 83) and navlib never clears it.
 */
private val DrawerPadding = 8.dp
private val DividerThickness = 1.dp

// ---- badges ----

/**
 * **Every** badge in this drawer - rows, profile rows and the header's per-profile counts - is
 * navlib's one `BadgeStyle`, built in `NavDrawer.init` (offsets 33-57) as
 * `textColor = ColorHolder.fromColor(-1)` and `color = ColorHolder.fromColor(4292030255)`, i.e.
 * `#FFFFFF` on **`#D32F2F`**. `updateProfileList` hands the same instance to every
 * `ProfileDrawerItem` (`withBadgeStyle`, offset 330) and `MainActivity.kt:1079` to every badgeable row.
 *
 * Hardcoded there, so hardcoded here: M3's [androidx.compose.material3.Badge] would default to
 * `colorScheme.error`, which is wallpaper-derived under the dynamic colour `ui/compose/theme/Theme.kt:41`
 * applies on API >= 31. Note this is a *different* red from `AppBottomBar`'s `#FF3D00`: that one is
 * navlib's `BadgeDrawable`, a separate hardcode for the hamburger only.
 *
 * The chip itself is `drawable/material_drawer_badge.xml` (a `5dp`-radius rect) plus `BadgeStyle`'s own
 * constructor defaults - `paddingLeftRight = 3dp`, `paddingTopBottom = 2dp`, `minWidth = 20dp`. Text
 * size comes from whichever layout hosts it, hence the three [DrawerBadge] call sites.
 */
private val BadgeContainerColor = Color(0xFFD32F2F)
private val BadgeContentColor = Color(0xFFFFFFFF)
private val BadgeShape = RoundedCornerShape(5.dp)
private val BadgePaddingHorizontal = 3.dp
private val BadgePaddingVertical = 2.dp
private val BadgeMinWidth = 20.dp

// ---- account header ----

/**
 * `res/layout/material_drawer_header.xml`, the app's own override of MaterialDrawer's header, read
 * against `materialdrawer-8.3.3` `values.xml` for its dimens:
 *
 * | | |
 * |---|---|
 * | block | `material_drawer_account_header_height` = 160 dp, background `centerCrop` |
 * | current avatar | `..._header_selected` = 56 dp, `marginStart` = `material_drawer_vertical_padding` = 16 dp, `marginTop` = `..._horizontal_top` = 16 dp |
 * | other avatars | `..._header_secondary` = 40 dp, same 16 dp top, chained end-to-start with 16 dp between and after |
 * | name | `..._header_title` = 20 sp `sans-serif-medium` |
 * | subname | `..._header_subtext` = 14 sp `sans-serif`, `marginBottom` = `material_drawer_padding` = 8 dp |
 * | text block top | `..._header_dropdown_guideline` = 80 dp from the bottom |
 * | chevron | `..._header_dropdown` = 22 dp, `marginBottom` = `..._dropdown_margin_bottom` = 18 dp |
 * | badge text | current `material_drawer_item_badge_text` = 12 sp, others `..._badge_small_text` = 10 sp |
 *
 * `material_drawer_vertical_padding` is the one dimen with a qualified variant - `24dp` in
 * `values-sw600dp-v13` - so on a tablet the real header pads 24 dp, not 16. Not reproduced: it would
 * mean reading MaterialDrawer's `R.dimen` from Compose, which pins new code to the AAR that N5 removes,
 * and 8 dp of header padding on tablets is not worth that. Reported rather than silently dropped.
 */
private val HeaderHeight = 160.dp

/**
 * `material_drawer_vertical_padding` = 16 dp, the horizontal edge padding shared by the header, the nav
 * rows and both profile layouts (its name is MaterialDrawer's, not a description).
 */
private val EdgePadding = 16.dp
private val HeaderBottomPadding = 8.dp
private val HeaderAvatarSize = 56.dp
private val HeaderSmallAvatarSize = 40.dp
private val HeaderNameSize = 20.sp
private val HeaderSubtextSize = 14.sp
private val HeaderBadgeTextSize = 12.sp
private val HeaderSmallBadgeTextSize = 10.sp
private const val HeaderChevronSize = 22
private val HeaderChevronBottomPadding = 18.dp

/** `AccountHeaderView.calculateProfiles` fills three secondary `BezelImageView`s and no more. */
private const val HeaderSmallAvatarCount = 3

// ---- profile rows ----

/**
 * `res/layout/material_drawer_item_profile.xml` (the app's second override) and
 * `material_drawer_item_profile_setting.xml`: both are `material_drawer_item_profile` = 72 dp tall with
 * `material_drawer_vertical_padding` = 16 dp of horizontal padding.
 *
 * A profile row's avatar is `..._item_profile_icon` = 40 dp, followed by
 * `..._item_profile_icon_padding_right` = 8 dp plus the name's own `material_drawer_padding` = 8 dp
 * margin, so the text starts 16 dp after the avatar. Name and subname are both
 * `..._item_profile_text` / `..._item_profile_description` = 14 sp.
 *
 * A profile-**setting** row's icon column is `material_drawer_item_primary` = 48 dp wide with
 * `..._item_profile_setting_icon_padding` = 24 dp of padding on three sides, so the icon is 24 dp and
 * the text starts 24 dp after it.
 */
private val ProfileRowHeight = 72.dp
private val ProfileAvatarSize = 40.dp
private val ProfileAvatarGap = 16.dp
private const val ProfileSettingIconSize = 24
private val ProfileSettingIconGap = 24.dp

/**
 * The M3 replacement for navlib's `NavDrawer` slider (§7.4/§7.5 of the N4a design): the account header,
 * the nav rows with their unread badges, and - when [ShellState.profileSelectionOpen] - the profile
 * switcher with [ShellState.profileSettings] beneath it.
 *
 * Nothing composes this yet; `AppScaffold` does, in a later task. It renders the drawer *sheet*, not
 * just its contents, so the caller can drop it straight into `ModalNavigationDrawer`'s `drawerContent`
 * or - in [DrawerMode.Permanent] - as the first child of the content `Row`, with
 * `width = `[PermanentDrawerWidth] (design §5; a second `PermanentNavigationDrawer` call site around the
 * content would re-run the fragment container's `AndroidView` factory on every 900 dp crossing).
 *
 * It takes callbacks rather than the `MainActivity` so that it compiles before `MainActivity` is
 * rewired - [onProfileLongClick] ends up showing `ProfileConfigDialog`, whose constructor needs the
 * activity, and every [DrawerAction] needs `navigate()`.
 *
 * **The two swallowed taps are not decided here.** [drawerRowAction] and [profileAction] own them, so
 * this file cannot re-derive them: navlib returns before reaching the app's listener both for the row
 * that is already selected (`NavDrawer$init$4$1`) and for the profile that is already current
 * (`NavDrawer$init$3$1`, offsets 72-95: `close()`, `profileSelectionClose()`, `return true`).
 * Reproducing either as a `navigate()` call destroys and rebuilds the visible fragment on a gesture
 * that does nothing today.
 *
 * @param profileImages resolves a profile id to its avatar, as `Profile.getImageDrawable(Context)`
 *   does; `null` renders a placeholder icon. The caller owns it because the drawable is a
 *   `GifDrawable` for a `.gif` avatar and must not be shared with a `View`.
 * @param onAction receives [DrawerAction.Navigate], [DrawerAction.SwitchProfile] and
 *   [DrawerAction.CloseOnly]; **it must close the drawer for all three**, which is the one thing §7.5's
 *   table asks of every arm but `ToggleExpandable`. That last variant never reaches here - the "More"
 *   group's open state is local to this composable, because no [ShellState] field carries it and
 *   navlib's own expandable listener returns `false`, so nothing app-side happened either.
 * @param onProfileSettingClick runs `MainActivity`'s `profileSettingClickListener` action
 *   (`PROFILE_ADD` -> `requestLogin()`, `PROFILE_SYNC_ALL`, `PROFILE_MARK_AS_READ`) and then closes the
 *   drawer, per §7.5.
 */
@Composable
fun AppDrawer(
    state: ShellState,
    currentProfileId: Int,
    profileImages: (Int) -> Drawable?,
    onAction: (DrawerAction) -> Unit,
    onProfileLongClick: (Int) -> Unit,
    onProfileSettingClick: (DrawerRow) -> Unit,
    width: Dp = ModalDrawerWidth,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(
        modifier = modifier.width(width),
        // `material_drawer_slider.xml` is a square-cornered `ScrimInsetsRelativeLayout`; M3 would
        // round the end corners (`DrawerDefaults.shape`, `extraLarge` = 28 dp), and in permanent mode
        // that rounding would cut into the content. `materialDrawerBackground` is `?colorSurface`
        // (`Widget.MaterialDrawerStyle`), read rather than inlined because all ~20 palette themes
        // override it (`styles.xml:147+`). `drawerTonalElevation` is left alone: like `Surface`
        // everywhere, it only lifts a colour that *is* `colorScheme.surface`, so it is inert here.
        drawerShape = RectangleShape,
        drawerContainerColor = colorAttr(R.attr.colorSurface),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            AccountHeader(state, currentProfileId, profileImages, onAction, onProfileLongClick)
            Divider()

            // navlib swaps the slider's item adapter wholesale when the profile list opens
            // (`AccountHeaderView.toggleSelectionList` -> `buildDrawerSelectionList` ->
            // `switchDrawerContent`), which is what `MainActivity.kt:1136`'s comment is about - so the
            // nav rows are *replaced*, not pushed down.
            if (state.profileSelectionOpen)
                ProfileSelection(
                    state = state,
                    currentProfileId = currentProfileId,
                    profileImages = profileImages,
                    onAction = onAction,
                    onProfileLongClick = onProfileLongClick,
                    onProfileSettingClick = onProfileSettingClick,
                )
            else
                NavItems(state, onAction)
        }
    }
}

/** [ShellState.drawerEntries], i.e. `DrawerEntries.main`: rows, the "More" group, a divider, rows. */
@Composable
private fun NavItems(state: ShellState, onAction: (DrawerAction) -> Unit) {
    for (entry in state.drawerEntries) {
        when (entry) {
            is DrawerEntry.Divider -> Divider()
            is DrawerEntry.Row -> NavItem(entry.row, state, onAction)
            is DrawerEntry.Expandable -> ExpandableSection(entry, state, onAction)
        }
    }
}

/**
 * One nav row. `selected` follows [ShellState.selectedTarget], and the tap is whatever
 * [drawerRowAction] says it is.
 *
 * The badge is [badgeText], **not** [totalBadgeText]: rows clamp at `"99+"` and vanish at zero
 * (`NavDrawer.updateBadges` offsets 975-1013); only the bottom bar's hamburger total is unclamped.
 */
@Composable
private fun NavItem(row: DrawerRow, state: ShellState, onAction: (DrawerAction) -> Unit) {
    val badge = badgeText(row.target?.let { state.badges.perTarget[it.id] } ?: 0)

    DrawerRowItem(
        nameRes = row.nameRes,
        descriptionRes = row.descriptionRes,
        icon = row.icon,
        level = row.level,
        selected = row.target != null && row.target == state.selectedTarget,
        onClick = { onAction(drawerRowAction(row, state.selectedTarget)) },
        trailing = badge?.let { { DrawerBadge(it, RowTextSize) } },
    )
}

/**
 * The "More" group: `ExpandableDrawerItem` with `identifier = -1L`, `isSelectable = false`
 * (`MainActivity.kt:1129-1136`) and a 16 dp trailing arrow (`material_drawer_item_expandable.xml`)
 * that `ExpandableDrawerItem`'s own listener rotates by 180°.
 *
 * The open state is `remember`ed **keyed on [ShellState.drawerEntries]**, which reproduces navlib
 * exactly: `setDrawerItems()` builds a fresh `ExpandableDrawerItem` (`isExpanded = false`) and calls
 * `drawer.setItems(...)`, so the group collapses whenever the entries are rebuilt - on startup, on a
 * profile switch (`MainActivity.kt:887`) and from `MiniMenuConfigDialog` - and survives navigation,
 * which never rebuilds them.
 */
@Composable
private fun ExpandableSection(
    entry: DrawerEntry.Expandable,
    state: ShellState,
    onAction: (DrawerAction) -> Unit,
) {
    var expanded by remember(state.drawerEntries) { mutableStateOf(false) }

    DrawerRowItem(
        nameRes = entry.nameRes,
        descriptionRes = null,
        icon = entry.icon,
        level = 1,
        selected = false,
        onClick = { expanded = !expanded },
        trailing = {
            IconicsIcon(
                icon = CommunityMaterial.Icon.cmd_chevron_down,
                contentDescription = null,
                sizeDp = ExpandableArrowSize,
                tint = colorAttr(android.R.attr.textColorSecondary),
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
            )
        },
    )

    if (expanded)
        for (child in entry.children)
            NavItem(child, state, onAction)
}

/** `material_drawer_item_expandable.xml`'s `material_drawer_arrow` is 16 x 16 dp. */
private const val ExpandableArrowSize = 16

/**
 * navlib's `PrimaryDrawerItem` / `SecondaryDrawerItem` on an M3 [NavigationDrawerItem].
 *
 * Colours, all measured rather than picked, and all *read* from the theme so they survive the AAR:
 *
 * - selected container - `UtilsKt.getSelectedColor` = `materialDrawerSelectedBackgroundColor`
 *   (`?colorPrimary`) at `setAlphaComponent(color, (255 * material_drawer_selected_background_alpha).toInt())`
 *   = 30/255. M3's default is the opaque `colorScheme.secondaryContainer`.
 * - selected text and icon - `UtilsKt.createDrawerItemColorStateList` replaces the *selected* state of
 *   both state lists with the same `?colorPrimary`.
 * - unselected text - `materialDrawerPrimaryText` = `?android:textColorPrimary`; unselected icon -
 *   `materialDrawerPrimaryIcon` = `?android:textColorSecondary` (`Widget.MaterialDrawerStyle`, which
 *   navlib installs as `materialDrawerStyle` and the app never overrides).
 * - description - `getDescriptionTextColor() ?: getSecondaryDrawerTextColor` =
 *   `?android:textColorSecondary` (`BaseDescribeableDrawerItem.bindView` offset 103).
 *
 * `?colorPrimary` is the app's own attr (`styles.xml:106` light, `:125` dark, plus every palette theme);
 * the two text attrs are platform attrs with sane framework defaults, which is why `AppSheet` reads
 * them too. The label colours are passed to [Text] explicitly rather than inherited from
 * [NavigationDrawerItem]'s content colour - the component provides only one for the whole label slot,
 * and a two-line row needs two.
 *
 * **Not** reproduced: navlib's text starts `material_drawer_vertical_padding` + the 56 dp icon column =
 * 72 dp from the edge, while [NavigationDrawerItemDefaults]'s `ItemPadding` (16 dp) plus its fixed
 * icon-label gap puts it at ~52 dp. Reproducing it means not using the component §7.4 asks for.
 */
@Composable
private fun DrawerRowItem(
    @StringRes nameRes: Int,
    @StringRes descriptionRes: Int?,
    icon: IIcon?,
    level: Int,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)?,
) {
    val primaryText = colorAttr(android.R.attr.textColorPrimary)
    val secondaryText = colorAttr(android.R.attr.textColorSecondary)
    val selectedColor = colorAttr(R.attr.colorPrimary)
    val textColor = if (selected) selectedColor else primaryText
    val iconSize = if (level > 1) SubRowIconSize else RowIconSize

    NavigationDrawerItem(
        label = {
            Column {
                Text(
                    text = stringResource(nameRes),
                    color = textColor,
                    fontSize = RowTextSize,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (descriptionRes != null)
                    Text(
                        text = stringResource(descriptionRes),
                        color = secondaryText,
                        fontSize = RowTextSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
            }
        },
        selected = selected,
        onClick = onClick,
        // The fixed height is applied *outside* the component, so it constrains the `heightIn(min = )`
        // M3 applies inside; the insets then place the 4 dp pill inside that slot exactly as
        // `themeDrawerItem` places its `InsetDrawable`.
        modifier = Modifier
            .height(if (level > 1) SubRowHeight else RowHeight)
            .padding(
                start = RowInsetHorizontal + LevelIndent * (level - 1),
                end = RowInsetHorizontal,
                top = RowInsetVertical,
                bottom = RowInsetVertical,
            ),
        icon = icon?.let {
            {
                // No content description: navlib's `material_drawer_icon` has none and the label
                // beside it already names the row.
                IconicsIcon(
                    icon = it,
                    contentDescription = null,
                    sizeDp = iconSize,
                    tint = if (selected) selectedColor else secondaryText,
                )
            }
        },
        badge = trailing,
        shape = RowShape,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = selectedColor.copy(alpha = SelectedBackgroundAlpha),
            unselectedContainerColor = Color.Transparent,
            selectedIconColor = selectedColor,
            unselectedIconColor = secondaryText,
            selectedTextColor = textColor,
            unselectedTextColor = primaryText,
            selectedBadgeColor = textColor,
            unselectedBadgeColor = primaryText,
        ),
    )
}

/** `material_drawer_selected_background_alpha` = `0.12`, applied as `(255 * 0.12).toInt() = 30`. */
private const val SelectedBackgroundAlpha = 30 / 255f

/** `material_drawer_item_divider.xml` - see [DividerThickness]. */
@Composable
private fun Divider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = DrawerPadding),
        thickness = DividerThickness,
        // `UtilsKt.getDividerColor` = `materialDrawerDividerColor` = `?android:textColorHint`
        // (`Widget.MaterialDrawerStyle`). `HorizontalDivider`'s own default is `outlineVariant`, which
        // is wallpaper-derived under `Theme.kt:41`'s dynamic colour.
        color = colorAttr(android.R.attr.textColorHint),
    )
}

/**
 * The account header, rebuilt from `res/layout/material_drawer_header.xml` - see [HeaderHeight] for the
 * measurement table.
 *
 * Three taps live here:
 *
 * - **the name/subname/chevron section** toggles [ShellState.profileSelectionOpen]. That is
 *   `AccountHeaderView`'s `onSelectionClickListener` -> `toggleSelectionList`, which animates
 *   `material_drawer_ico_menu_down` to `rotation(180f)` on open (offset 51) and back on
 *   `resetDrawerContent` - so binding the chevron's rotation to the same flag is what navlib does,
 *   not an embellishment.
 * - **any avatar** is a profile tap, resolved by [profileAction]. Tapping the current one is the
 *   swallowed case: navlib runs `resetDrawerContent()` before the listener either way, hence the
 *   unconditional flag clear here, and then `init$3$1` closes the drawer and returns.
 * - **a long press on any avatar** is `drawerProfileImageLongClickListener`, which
 *   `MainActivity.kt:285` aliases to `drawerProfileLongClickListener` - the same
 *   `ProfileConfigDialog` route as a long press in the profile list.
 */
@Composable
private fun AccountHeader(
    state: ShellState,
    currentProfileId: Int,
    profileImages: (Int) -> Drawable?,
    onAction: (DrawerAction) -> Unit,
    onProfileLongClick: (Int) -> Unit,
) {
    val current = state.profiles.firstOrNull { it.id == currentProfileId }
    val others = state.profiles
        .filter { it.id != currentProfileId }
        .take(HeaderSmallAvatarCount)
    val switcherDescription = stringResource(R.string.choose_profile)

    val onProfileClick = { id: Int ->
        state.profileSelectionOpen = false
        onAction(profileAction(id, currentProfileId))
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(HeaderHeight),
    ) {
        Image(
            painter = headerBackground(state.headerBackground, state.headerBackgroundToken),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        if (current != null)
            ProfileAvatar(
                profile = current,
                image = profileImages(current.id),
                size = HeaderAvatarSize,
                badge = badgeText(state.badges.perProfile[current.id] ?: 0),
                badgeTextSize = HeaderBadgeTextSize,
                scale = ContentScale.Crop,
                onClick = { onProfileClick(current.id) },
                onLongClick = { onProfileLongClick(current.id) },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = EdgePadding, top = EdgePadding),
            )

        // `setDisplayBadgesOnSmallProfileImages(true)` (`NavDrawer$init$3`, offset 25), so the small
        // avatars carry their own counts too - at the smaller 10 sp of `..._badge_small_text`.
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = EdgePadding, end = EdgePadding),
            horizontalArrangement = Arrangement.spacedBy(EdgePadding),
        ) {
            for (profile in others)
                ProfileAvatar(
                    profile = profile,
                    image = profileImages(profile.id),
                    size = HeaderSmallAvatarSize,
                    badge = badgeText(state.badges.perProfile[profile.id] ?: 0),
                    badgeTextSize = HeaderSmallBadgeTextSize,
                    scale = ContentScale.Fit,
                    onClick = { onProfileClick(profile.id) },
                    onLongClick = { onProfileLongClick(profile.id) },
                )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .clickable { state.profileSelectionOpen = !state.profileSelectionOpen },
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = EdgePadding, bottom = HeaderBottomPadding),
            ) {
                Text(
                    // `Widget.MaterialDrawerHeaderStyle` sets `materialDrawerHeaderSelectionText` to
                    // `?android:textColorPrimary` and the subtext to `?android:textColorSecondary`;
                    // `AccountHeaderView` reads them through `getHeaderSelection[Sub]TextColor`.
                    text = current?.name.orEmpty(),
                    color = colorAttr(android.R.attr.textColorPrimary),
                    fontSize = HeaderNameSize,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = current?.subname.orEmpty(),
                    color = colorAttr(android.R.attr.textColorSecondary),
                    fontSize = HeaderSubtextSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconicsIcon(
                icon = CommunityMaterial.Icon.cmd_chevron_down,
                contentDescription = switcherDescription,
                sizeDp = HeaderChevronSize,
                tint = colorAttr(android.R.attr.textColorPrimary),
                modifier = Modifier
                    .padding(end = EdgePadding, bottom = HeaderChevronBottomPadding)
                    .rotate(if (state.profileSelectionOpen) 180f else 0f),
            )
        }
    }
}

/**
 * The header's background image.
 *
 * **The `remember(path)` key is the refresh mechanism, not an optimisation.**
 * `setDrawerHeaderBackground(String?)` writes [ShellState.headerBackground], and every pick saves to
 * the same `filesDir/header.<ext>`, so the second pick assigns a value equal to the current one and a
 * `mutableStateOf` write with structural equality does not recompose at all. That is exactly why
 * `SettingsFragment.kt:167-171` sets null and then the value: keyed here, the null pass loads
 * [R.drawable.header] and the second pass re-decodes the file, so a same-value write still reloads.
 * A loader that cached the decoded bitmap across key changes, or one that read
 * `app.config.ui.headerBackground` directly (the config is not observable, so that path never
 * recomposes at all), would keep showing the previous header forever.
 *
 * `null` falls back to `R.drawable.header`, as `NavDrawer.setAccountHeaderBackground(null)` does via
 * `ImageHolder(pl.szczodrzynski.navlib.R.drawable.header)` - resource merging makes that the app's own
 * `res/drawable/header.webp`, which is why the app id is used here and survives the AAR.
 *
 * The `.gif` branch is navlib's: `ImageHolder.applyTo` builds a `pl.droidsonroids.gif.GifDrawable` for
 * a gif uri, and `MainActivityRequestHandler.kt:117`'s `shouldCrop` deliberately skips cropping gifs so
 * that an animated header stays animated. [DrawableAvatarPainter] is what keeps it animating in
 * Compose - a `BitmapPainter` would silently freeze it, exactly as `SettingsScreen.kt:174-176` does.
 */
@Composable
private fun headerBackground(path: String?, reloadToken: Int): Painter {
    val context = LocalContext.current
    return remember(path, reloadToken) {
        val loaded = when {
            path == null -> null
            path.endsWith(".gif", ignoreCase = true) -> runCatching { GifDrawable(path) }.getOrNull()
            else -> BitmapFactory.decodeFile(path)?.let { BitmapDrawable(context.resources, it) }
        }
        DrawableAvatarPainter(loaded ?: context.getDrawableFromRes(R.drawable.header))
    }
}

/**
 * The expanded profile switcher: every profile, then [ShellState.profileSettings].
 *
 * That order is navlib's own - `addProfileSettings` appends the three `ProfileSettingDrawerItem`s to
 * the very list `buildDrawerSelectionList` renders (`AccountHeaderView.profiles`), and
 * `currentHiddenInList` stays `false`, so the current profile appears in the list as well, selected.
 *
 * The settings rows are **not** [DrawerEntry]s and must never reach the main list: `PROFILE_ADD` is the
 * app's only in-app add-profile route (`requestHandler.requestLogin()` has exactly two references in
 * the whole app), and dropping the row compiles clean.
 */
@Composable
private fun ProfileSelection(
    state: ShellState,
    currentProfileId: Int,
    profileImages: (Int) -> Drawable?,
    onAction: (DrawerAction) -> Unit,
    onProfileLongClick: (Int) -> Unit,
    onProfileSettingClick: (DrawerRow) -> Unit,
) {
    for (profile in state.profiles)
        ProfileRow(
            profile = profile,
            image = profileImages(profile.id),
            badge = badgeText(state.badges.perProfile[profile.id] ?: 0),
            selected = profile.id == currentProfileId,
            onClick = {
                state.profileSelectionOpen = false
                onAction(profileAction(profile.id, currentProfileId))
            },
            onLongClick = { onProfileLongClick(profile.id) },
        )

    for (row in state.profileSettings)
        ProfileSettingRow(row, onClick = { onProfileSettingClick(row) })
}

/**
 * One `ProfileDrawerItem`: a 72 dp row with a circular avatar, name, subname and badge.
 *
 * Name **and** subname are `getColor(ctx)` = `?android:textColorPrimary` - `ProfileDrawerItem.bindView`
 * falls the description back to `getColor` (offset 220), not to the secondary colour the nav rows use
 * (offset 103 of `BaseDescribeableDrawerItem`), so the two row types genuinely differ here.
 *
 * The current profile's row is selected, and `themeDrawerItem$default` gives it the same 8/4 dp inset
 * and 4 dp corner as a nav row (offset 257).
 */
@Composable
private fun ProfileRow(
    profile: Profile,
    image: Drawable?,
    badge: String?,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val textColor =
        if (selected) colorAttr(R.attr.colorPrimary) else colorAttr(android.R.attr.textColorPrimary)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ProfileRowHeight)
            .padding(horizontal = RowInsetHorizontal, vertical = RowInsetVertical)
            .clip(RowShape)
            .background(
                if (selected)
                    colorAttr(R.attr.colorPrimary).copy(alpha = SelectedBackgroundAlpha)
                else
                    Color.Transparent,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = EdgePadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileImage(profile, image, ProfileAvatarSize, ContentScale.Crop)
        Spacer(Modifier.width(ProfileAvatarGap))
        Column(Modifier.weight(1f)) {
            Text(
                text = profile.name,
                color = textColor,
                fontSize = RowTextSize,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!profile.subname.isNullOrEmpty())
                Text(
                    text = profile.subname.orEmpty(),
                    color = textColor,
                    fontSize = RowTextSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
        }
        if (badge != null) {
            Spacer(Modifier.width(DrawerPadding))
            DrawerBadge(badge, RowTextSize)
        }
    }
}

/**
 * One `ProfileSettingDrawerItem` - `PROFILE_ADD`, `PROFILE_SYNC_ALL` or `PROFILE_MARK_AS_READ`.
 *
 * Unlike every other row type these are **not** shaped: `bindView` calls
 * `UtilsKt.getSelectableBackground` (offset 254) rather than `themeDrawerItem`, so the ripple is
 * full-bleed with no pill. Name and description are both `getColor(ctx)` =
 * `?android:textColorPrimary` (offsets 283, 307) and the icon is `getPrimaryDrawerIconColor` =
 * `?android:textColorSecondary` (offset 219).
 */
@Composable
private fun ProfileSettingRow(row: DrawerRow, onClick: () -> Unit) {
    val textColor = colorAttr(android.R.attr.textColorPrimary)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ProfileRowHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = EdgePadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (row.icon != null) {
            IconicsIcon(
                icon = row.icon,
                contentDescription = null,
                sizeDp = ProfileSettingIconSize,
                tint = colorAttr(android.R.attr.textColorSecondary),
            )
            Spacer(Modifier.width(ProfileSettingIconGap))
        }
        Column {
            Text(
                text = stringResource(row.nameRes),
                color = textColor,
                fontSize = RowTextSize,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.descriptionRes != null)
                Text(
                    text = stringResource(row.descriptionRes),
                    color = textColor,
                    fontSize = RowTextSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
        }
    }
}

/** A tappable circular avatar with its unread badge pinned to the avatar's bottom-start corner. */
@Composable
private fun ProfileAvatar(
    profile: Profile,
    image: Drawable?,
    size: Dp,
    badge: String?,
    badgeTextSize: TextUnit,
    scale: ContentScale,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        ProfileImage(profile, image, size, scale)
        if (badge != null)
            Box(Modifier.align(Alignment.BottomStart)) {
                DrawerBadge(badge, badgeTextSize)
            }
    }
}

/**
 * A profile avatar. Circular **explicitly**.
 *
 * Both layouts render these through `@style/BezelImageView`, whose circle is a
 * `materialDrawerMaskDrawable = @drawable/material_drawer_circle_mask` nothing in the layout mentions.
 * Phase 30 had to restore that circle by hand in two other places after exactly this trap, and Task 4
 * hit it a third time with the toolbar avatar - so there is no version of this that infers the shape.
 *
 * [scale] is **not** uniform, and the two overrides are the only place that says so: the app sets
 * `scaleType="centerCrop"` on the header's current avatar and on the profile row's
 * (`material_drawer_item_profile.xml`), but leaves the three small header avatars at `fitCenter`.
 * Identical for a square source - the crop pipeline forces `setAspectRatio(1, 1)` on picked avatars -
 * and visibly different for one that is not, which a `.gif` avatar can be, since
 * `MainActivityRequestHandler.kt:117` skips cropping gifs.
 *
 * Rendered via [DrawableAvatarPainter] so that gif keeps animating.
 */
@Composable
private fun ProfileImage(
    profile: Profile,
    image: Drawable?,
    size: Dp,
    scale: ContentScale,
) {
    val painter = remember(image) { image?.let(::DrawableAvatarPainter) }
    val circle = Modifier
        .size(size)
        .clip(CircleShape)

    if (painter != null)
        Image(
            painter = painter,
            contentDescription = profile.name,
            contentScale = scale,
            modifier = circle,
        )
    else
        IconicsIcon(
            icon = CommunityMaterial.Icon.cmd_account_circle,
            contentDescription = profile.name,
            sizeDp = size.value.toInt(),
            modifier = Modifier.clip(CircleShape),
        )
}

/** navlib's one `BadgeStyle`, in Compose - see [BadgeContainerColor]. */
@Composable
private fun DrawerBadge(text: String, textSize: TextUnit) {
    Text(
        text = text,
        color = BadgeContentColor,
        fontSize = textSize,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .widthIn(min = BadgeMinWidth)
            .background(BadgeContainerColor, BadgeShape)
            .padding(horizontal = BadgePaddingHorizontal, vertical = BadgePaddingVertical),
    )
}

/**
 * One theme attr as a [Color], resolved once per context - a theme change goes through the
 * Activity-recreate path (see `ui/compose/theme/Theme.kt`'s `appColorScheme` KDoc), so a new theme
 * always brings a new context. Same pattern as `AppSheet`'s and `AppBottomBar`'s colour readers, and
 * the reason every colour above is an attr rather than an M3 role: `Theme.kt:41` enables dynamic colour
 * on API >= 31, so a role would drift to a wallpaper-derived value inside a shell swap.
 */
@Composable
private fun colorAttr(attr: Int): Color {
    val context = LocalContext.current
    return remember(context, attr) { Color(getColorFromAttr(context, attr)) }
}
