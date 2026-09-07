/*
 * Copyright (c) Mikolaj Olszewski 2026-9-2.
 */

package eu.mikus.edziennik.ui.shell

import android.content.res.Configuration
import androidx.annotation.StringRes
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.enums.FeatureType
import eu.mikus.edziennik.data.db.enums.MetadataType
import eu.mikus.edziennik.ui.base.ScreenAction
import eu.mikus.edziennik.ui.base.enums.NavTarget
import eu.mikus.edziennik.ui.base.enums.NavTargetLocation
import eu.mikus.edziennik.utils.models.UnreadCounter
import kotlin.math.roundToInt

/**
 * Every decision the M3 app shell makes, as pure functions over explicit inputs - written before
 * any composable exists so that all of it is unit-testable. The composables that consume this file
 * have NO automated coverage (Robolectric 4.14.1 + compose-bom 2026.06.00 cannot host
 * `createComposeRule`, and there is no `app/src/androidTest`), so behaviour that can live here
 * must live here.
 *
 * Several of these reproduce decisions the navlib AAR made internally and that no app code
 * expressed; those are documented per function. Not Android-free - [NavTarget] carries a
 * `Class<out Fragment>?` - but nothing here touches that field, so this is all plain-JVM runnable.
 */

// ---- types ----

/** Modal drawer, icon rail + modal drawer, or a permanently visible drawer. */
enum class DrawerMode { Modal, Mini, Permanent }

/** What a back press means for the shell. [Content] delegates to `NavStackPolicy`, unchanged. */
sealed interface ShellBack {
    data object CloseSheet : ShellBack
    data object CloseDrawer : ShellBack
    data object OpenDrawer : ShellBack
    data object Content : ShellBack
}

/** What a tap inside the drawer means. */
sealed interface DrawerAction {
    /** Dismiss the drawer and do nothing else - what navlib does for an already-selected row. */
    data object CloseOnly : DrawerAction
    data class Navigate(val target: NavTarget) : DrawerAction
    data class SwitchProfile(val profileId: Int) : DrawerAction
    data object ToggleExpandable : DrawerAction
}

/** One drawer row. [target] is null for the expandable header, which navigates nowhere. */
data class DrawerRow(
    val target: NavTarget?,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int?,
    val icon: IIcon?,
    val badge: MetadataType?,
    val level: Int,
    val hiddenInRail: Boolean,
)

sealed interface DrawerEntry {
    data class Row(val row: DrawerRow) : DrawerEntry
    data object Divider : DrawerEntry
    data class Expandable(
        @StringRes val nameRes: Int,
        val icon: IIcon?,
        val children: List<DrawerRow>,
    ) : DrawerEntry
}

/**
 * Two lists, deliberately. [profileSettings] renders inside the drawer's expanded profile list,
 * which no [DrawerEntry] variant can express, and it carries the app's only in-app add-profile
 * route (`requestHandler.requestLogin()` has exactly two references in the whole app).
 */
data class DrawerEntries(
    val main: List<DrawerEntry>,
    val profileSettings: List<DrawerRow>,
)

/**
 * Raw unread counts for all five badge surfaces: [perTarget] (keyed by `NavTarget.id`, feeding
 * both the drawer rows and the rail), [perProfile] (keyed by profile id, feeding the header), and
 * [total] (feeding the bottom-bar hamburger and the toolbar subtitle). Counts are unclamped here;
 * [badgeText] and [totalBadgeText] apply navlib's display rules.
 */
data class Badges(
    val perTarget: Map<Int, Int>,
    val perProfile: Map<Int, Int>,
    val total: Int,
)

/** The toolbar's sync state. `Done` needs an explicit ~2 s lifetime back to [Idle] at the call site. */
sealed interface SyncSubtitle {
    data object Idle : SyncSubtitle
    data class Syncing(val progress: Float, val text: String?) : SyncSubtitle
    data object Done : SyncSubtitle
}

/**
 * What moves the toolbar's sync subtitle. One signal per `ApiService` event the shell reacts to,
 * plus [ProfileChanged], which exists because of a bug: the three per-profile signals are ignored
 * when they name a profile other than the active one, so a sync in flight when the user switches
 * profile posts its [Finished] against the *old* id and is dropped - leaving the subtitle on
 * "Syncing…" with nothing able to clear it.
 */
sealed interface SyncSignal {
    data class Started(val profileId: Int) : SyncSignal
    data class Progress(val profileId: Int, val progress: Float, val text: String?) : SyncSignal
    data class Finished(val profileId: Int) : SyncSignal
    /** A failed sync ends the subtitle exactly as a finished one does, whatever profile it was for. */
    data object Failed : SyncSignal
    data object ProfileChanged : SyncSignal
}

/**
 * The subtitle's transition half, next to [subtitleOf], which is its rendering half. Pure, so
 * `ShellPolicyTest` can cover a protocol whose call sites live in `MainActivity` and are therefore
 * unreachable from the JVM suite.
 *
 * Signals naming a non-active profile leave [current] alone - that is deliberate, so a background
 * sync of another profile does not hijack the toolbar - and it is exactly why [SyncSignal.ProfileChanged]
 * has to reset explicitly.
 */
fun nextSubtitle(current: SyncSubtitle, signal: SyncSignal, activeProfileId: Int): SyncSubtitle =
    when (signal) {
        is SyncSignal.Started ->
            if (signal.profileId == activeProfileId) SyncSubtitle.Syncing(progress = -1f, text = null)
            else current

        is SyncSignal.Progress ->
            if (signal.profileId == activeProfileId) SyncSubtitle.Syncing(signal.progress, signal.text)
            else current

        is SyncSignal.Finished ->
            if (signal.profileId == activeProfileId) SyncSubtitle.Done else current

        SyncSignal.Failed -> SyncSubtitle.Done

        SyncSignal.ProfileChanged -> SyncSubtitle.Idle
    }

/**
 * A resource descriptor, not a `String` - the house pattern for testable text
 * (`ui/home/LuckyNumberMessage.kt:17-19`). [quantity] non-null means [res] is a plural, resolved
 * with `pluralStringResource`.
 *
 * [res] carries no `@StringRes`/`@PluralsRes` annotation on purpose: it is one or the other
 * depending on [quantity], and either annotation makes lint's `ResourceType` check fail at one of
 * the two ends.
 */
data class SubtitleText(
    val res: Int,
    val args: List<Any> = emptyList(),
    val quantity: Int? = null,
)

/** One row of the Compose bottom sheet. [separatorBefore] draws a separator ABOVE this row. */
data class SheetRow(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int?,
    val icon: IIcon,
    val separatorBefore: Boolean,
    val onClick: () -> Unit,
)

// ---- back handling ----

/**
 * navlib's `onBackPressed()` chain, plus the `config.ui.openDrawerOnBackPressed` gate that
 * `MainActivity.kt:1144-1156` wraps around it.
 *
 * The [OpenDrawer] outcome is the reason this takes four parameters: with the setting on and
 * nothing open, back OPENS the drawer. Resolve that to [ShellBack.Content] instead and it becomes
 * `navigateUp()` -> `popBackStack()` false -> `finish()` - the app exits and a shipped, togglable
 * setting turns into a visible no-op.
 *
 * Sheet-first, deliberately: navlib checks the drawer first, but no app code opens the sheet
 * programmatically, so the two orders are indistinguishable today, and dismissing the topmost
 * overlay is the correct M3 behaviour.
 *
 * @param drawerDismissible false in [DrawerMode.Permanent]; models navlib's own
 *   `fixedDrawerEnabled()` guard, which is why a permanent drawer is never closed by back.
 * @param drawerOpen as navlib's `isOpen()` reports it - which is `true` for the whole time the
 *   drawer is permanent, since a permanent drawer has no closed state.
 */
fun shellBackPolicy(
    sheetOpen: Boolean,
    drawerOpen: Boolean,
    drawerDismissible: Boolean,
    openDrawerOnBack: Boolean,
): ShellBack = when {
    sheetOpen -> ShellBack.CloseSheet
    openDrawerOnBack -> if (drawerOpen) ShellBack.Content else ShellBack.OpenDrawer
    drawerOpen && drawerDismissible -> ShellBack.CloseDrawer
    else -> ShellBack.Content
}

// ---- drawer mode ----

/**
 * navlib's `decideDrawerMode`, decoded from the AAR: **orientation first**, then width, and the
 * two orientations read different fields - `miniDrawerVisiblePortrait` (the app's
 * `config.ui.miniMenuVisible`) and `miniDrawerVisibleLandscape`, which `MainActivity.kt:288` pins
 * to null forever. Hence: portrait honours the setting and can never be permanent, while landscape
 * ignores it entirely.
 *
 * | orientation | width | mode |
 * |---|---|---|
 * | portrait | any | [DrawerMode.Mini] when [miniMenuVisible], else [DrawerMode.Modal] |
 * | landscape | < 480 | [DrawerMode.Modal] |
 * | landscape | 480-899 | [DrawerMode.Mini] |
 * | landscape | >= 900 | [DrawerMode.Permanent] |
 *
 * A width-only model would show the rail AND the permanent drawer together on a tablet, because
 * `LoginActivity.kt:45` seeds `miniMenuVisible = true` on any tablet.
 *
 * @param orientation one of `Configuration.ORIENTATION_*`; anything but
 *   `ORIENTATION_PORTRAIT` takes the landscape branch, as navlib's own comparison does.
 */
fun drawerMode(orientation: Int, screenWidthDp: Int, miniMenuVisible: Boolean): DrawerMode = when {
    orientation == Configuration.ORIENTATION_PORTRAIT ->
        if (miniMenuVisible) DrawerMode.Mini else DrawerMode.Modal
    screenWidthDp >= 900 -> DrawerMode.Permanent
    screenWidthDp >= 480 -> DrawerMode.Mini
    else -> DrawerMode.Modal
}

// ---- drawer contents ----

/**
 * `MainActivity.setDrawerItems()` as data: the `DRAWER` rows in [NavTarget] order, then the "more"
 * expandable holding the `DRAWER_MORE` rows at level 2, then a divider, then the `DRAWER_BOTTOM`
 * rows - and the `PROFILE_LIST` rows in their own list (see [DrawerEntries]).
 *
 * @param profileFeatures the current profile's `loginStoreType.features`; the
 *   `isUIAlwaysAvailable` half of `Profile.hasUIFeature` is applied here, not by the caller.
 * @param miniMenuButtons `config.ui.miniMenuButtons`; its complement is [DrawerRow.hiddenInRail].
 */
fun buildDrawerEntries(
    targets: List<NavTarget>,
    devMode: Boolean,
    profileFeatures: Set<FeatureType>,
    miniMenuButtons: Set<NavTarget>,
): DrawerEntries {
    val main = mutableListOf<DrawerEntry>()
    val more = mutableListOf<DrawerRow>()
    val bottom = mutableListOf<DrawerEntry>()
    val profileSettings = mutableListOf<DrawerRow>()

    fun rowOf(target: NavTarget, level: Int) = DrawerRow(
        target = target,
        nameRes = target.nameRes,
        descriptionRes = target.descriptionRes,
        icon = target.icon,
        badge = target.badgeType,
        level = level,
        hiddenInRail = target !in miniMenuButtons,
    )

    for (target in targets) {
        if (target.devModeOnly && !devMode)
            continue
        if (target.featureType != null && !hasUIFeature(target.featureType, profileFeatures))
            continue

        when (target.location) {
            NavTargetLocation.DRAWER -> main += DrawerEntry.Row(rowOf(target, level = 1))
            NavTargetLocation.DRAWER_MORE -> more += rowOf(target, level = 2)
            NavTargetLocation.DRAWER_BOTTOM -> bottom += DrawerEntry.Row(rowOf(target, level = 1))
            NavTargetLocation.PROFILE_LIST -> profileSettings += rowOf(target, level = 1)
            else -> continue
        }
    }

    main += DrawerEntry.Expandable(
        nameRes = R.string.menu_more,
        icon = CommunityMaterial.Icon.cmd_dots_horizontal,
        children = more,
    )
    main += DrawerEntry.Divider
    main += bottom

    return DrawerEntries(main = main, profileSettings = profileSettings)
}

/** `Profile.hasUIFeature`, over the profile's feature set rather than the entity. */
private fun hasUIFeature(feature: FeatureType, profileFeatures: Set<FeatureType>) =
    feature.isUIAlwaysAvailable || feature in profileFeatures

/**
 * navlib's item listener returns before reaching the app's one when the tapped row is already
 * selected (`NavDrawer$init$4$1`, offset 23), so tapping the current row is a no-op today. Calling
 * `navigate()` there would make `decideNavigation` return `RELOAD`, and `navigateImpl` destroys
 * and rebuilds the visible fragment - a visible regression on a gesture that does nothing now.
 */
fun drawerRowAction(row: DrawerRow, selectedTarget: NavTarget?): DrawerAction = when {
    row.target == null -> DrawerAction.ToggleExpandable
    row.target == selectedTarget -> DrawerAction.CloseOnly
    else -> DrawerAction.Navigate(row.target)
}

/**
 * Same swallowing for the current profile (`NavDrawer$init$3$1`, offset 72). Here the naive
 * version is worse: `navigate(profileId = ...)` passes `navTarget = null`, so `canNavigate()`
 * runs, and on `MessagesComposeFragment` that pops the discard-draft dialog.
 */
fun profileAction(id: Int, currentProfileId: Int): DrawerAction =
    if (id == currentProfileId) DrawerAction.CloseOnly else DrawerAction.SwitchProfile(id)

// ---- badges ----

/**
 * navlib's `updateBadges()` as data, for all five surfaces it drove. Per-target counts and the
 * total cover the current profile only; per-profile counts cover every rendered profile.
 *
 * The total sums only the types that map to a drawer row - navlib accumulates it inside the loop
 * that skips unmapped types, so e.g. unread lucky numbers never reach the hamburger.
 *
 * @param targets the [NavTarget]s whose `badgeType` maps a metadata type onto a row.
 * @param currentProfileId `App.profileId`; navlib read it from its own `currentProfile`.
 */
fun deriveBadges(
    unreadCounts: List<UnreadCounter>,
    profiles: List<Profile>,
    targets: List<NavTarget>,
    currentProfileId: Int,
): Badges {
    val targetIdByType = targets.mapNotNull { target ->
        target.badgeType?.let { badge -> badge.id to target.id }
    }.toMap()

    val perTarget = mutableMapOf<Int, Int>()
    var total = 0
    for (counter in unreadCounts) {
        if (counter.profileId != currentProfileId)
            continue
        val targetId = targetIdByType[counter.type] ?: continue
        perTarget[targetId] = (perTarget[targetId] ?: 0) + counter.count
        total += counter.count
    }

    val perProfile = profiles.associate { profile ->
        profile.id to unreadCounts.filter { it.profileId == profile.id }.sumOf { it.count }
    }

    return Badges(perTarget = perTarget, perProfile = perProfile, total = total)
}

/**
 * navlib's badge text for drawer rows, rail items and profile headers: nothing at zero, `"99+"`
 * from 99 up. Kept out of [Badges] so the raw counts stay summable; a user with 100+ unread must
 * still read "99+" on the rows, as they do today.
 */
fun badgeText(count: Int): String? = when {
    count == 0 -> null
    count >= 99 -> "99+"
    else -> count.toString()
}

/**
 * The hamburger total is the documented exception: navlib passes it to
 * `BadgeDrawable.setCount(String.valueOf(total))` unclamped, hiding it only at zero.
 */
fun totalBadgeText(total: Int): String? = if (total == 0) null else total.toString()

// ---- toolbar subtitle ----

/**
 * The steady-state subtitle nobody wrote: navlib's `updateBadges()` produced it from two format
 * resources, and the sync path suppressed it by nulling those formats. Now explicit.
 *
 * `R.string.toolbar_subtitle` is navlib's own `"%1$s"` pass-through, reused here for both the
 * no-unread name and an indeterminate sync's progress text.
 */
fun subtitleOf(state: SyncSubtitle, unreadTotal: Int, profileName: String): SubtitleText =
    when (state) {
        is SyncSubtitle.Idle ->
            if (unreadTotal > 0)
                SubtitleText(
                    res = R.plurals.toolbar_subtitle_with_unread,
                    args = listOf(profileName, unreadTotal),
                    quantity = unreadTotal,
                )
            else
                SubtitleText(R.string.toolbar_subtitle, listOf(profileName))

        is SyncSubtitle.Syncing ->
            if (state.progress < 0f)
                state.text?.let { SubtitleText(R.string.toolbar_subtitle, listOf(it)) }
                    ?: SubtitleText(R.string.toolbar_subtitle_syncing)
            else
                SubtitleText(
                    res = R.string.toolbar_subtitle_syncing_format,
                    args = listOf(state.progress.roundToInt(), state.text ?: ""),
                )

        is SyncSubtitle.Done -> SubtitleText(R.string.sync_status_done)
    }

// ---- bottom sheet ----

/**
 * The app-side half of the old `toBottomSheetItems` mapper. The separator stays a flag on the row
 * it precedes rather than becoming a row of its own - navlib needed a real separator item, the
 * Compose sheet does not.
 */
fun List<ScreenAction>.toSheetRows(onClick: (ScreenAction) -> Unit): List<SheetRow> = map { action ->
    SheetRow(
        titleRes = action.titleRes,
        descriptionRes = action.descriptionRes,
        icon = action.icon,
        separatorBefore = action.separatorBefore,
        onClick = { onClick(action) },
    )
}
