/*
 * Copyright (c) Mikolaj Olszewski 2026-9-2.
 */

package eu.mikus.edziennik.ui.shell

import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.ui.base.ScreenAction
import eu.mikus.edziennik.ui.base.ScreenFab
import eu.mikus.edziennik.ui.base.enums.NavTarget

/**
 * Every piece of chrome state the M3 app shell renders, in one holder owned by `MainActivity`, so
 * the composables *read* state instead of being poked imperatively the way navlib's views were.
 *
 * The public seam methods (`setScreenActions`, `setScreenFab`, `openDrawer`,
 * `setDrawerHeaderBackground`, `updateDrawerBadges`, `setMiniDrawerVisible`, ...) keep their exact
 * signatures and write these fields; nothing outside this package needs to know they exist.
 *
 * Nothing writes this class yet - the wiring is a later task. Do not delete a field because it
 * looks unused.
 */
@Stable
class ShellState {

    // ---- toolbar ----

    /** Already resolved text, as `navView.toolbar.setTitle(navTarget.titleRes ?: nameRes)` produced. */
    var title: String by mutableStateOf("")

    /** Feeds `subtitleOf(...)`; `Done` needs an explicit ~2 s lifetime back to [SyncSubtitle.Idle]. */
    var subtitle: SyncSubtitle by mutableStateOf(SyncSubtitle.Idle)

    /** The current screen's own menu, from `setScreenActions` - replace-semantics, not additive. */
    var actions: List<ScreenAction> by mutableStateOf(emptyList())

    // ---- primary action ----

    /** From `setScreenFab`; `null` means the screen has no primary action, so no FAB is rendered. */
    var fab: ScreenFab? by mutableStateOf(null)

    var fabExtended: Boolean by mutableStateOf(false)

    // ---- navigation ----

    var selectedTarget: NavTarget? by mutableStateOf(null)

    /** [DrawerEntries.main] - the drawer rows, the "more" expandable, the divider, the bottom rows. */
    var drawerEntries: List<DrawerEntry> by mutableStateOf(emptyList())

    /**
     * [DrawerEntries.profileSettings] - the `PROFILE_LIST` rows that render inside the drawer's
     * expanded profile list, which no [DrawerEntry] variant can express.
     *
     * **Never drop this as "unused".** It carries the app's only in-app add-profile route: the
     * `PROFILE_ADD` row calls `requestHandler.requestLogin()`, which has exactly two references in
     * the whole app (its declaration and that click). Losing the row makes adding a profile
     * impossible without reinstalling, and it compiles clean.
     */
    var profileSettings: List<DrawerRow> by mutableStateOf(emptyList())

    /** The Room entity, consumed directly by the drawer header - there is no `DrawerProfile` type. */
    var profiles: List<Profile> by mutableStateOf(emptyList())

    /** Recomputed by `updateDrawerBadges()` via `deriveBadges(...)`; feeds all five badge surfaces. */
    var badges: Badges by mutableStateOf(
        Badges(perTarget = emptyMap(), perProfile = emptyMap(), total = 0)
    )

    /**
     * The user's mini-menu **setting**, mirroring `app.config.ui.miniMenuVisible`.
     *
     * The mirror is the whole reason this field exists: the config is *not* observable, so a config
     * write alone leaves the rail's on/off switch inert. `setMiniDrawerVisible(visible)` writes
     * `app.config.ui.miniMenuVisible` **and** assigns `visible` here **verbatim** - never a derived
     * value.
     *
     * **This is not "the rail is showing".** That is
     * `drawerMode(orientation, screenWidthDp, miniMenuVisible) == DrawerMode.Mini`, computed in the
     * composition (`AppScaffold`) and **never written back**: writing it back would be a write/read
     * loop, and it would latch, because landscape 480-899 dp is [DrawerMode.Mini] *regardless* of
     * the setting - so a derived `true` stored here would survive a rotation into portrait and show
     * a rail the user had turned off.
     */
    var miniMenuVisible: Boolean by mutableStateOf(false)

    // ---- overlays ----

    /** Written by the bottom bar's sheet button; the contextual [SheetRow]s come from [actions]. */
    var sheetVisible: Boolean by mutableStateOf(false)

    /**
     * The drawer's expanded profile list. `openProfileSelection()` must set this **and** open the
     * drawer - navlib's own implementation did both, and a flag alone leaves the profile switcher
     * reachable only via drawer -> chevron.
     */
    var profileSelectionOpen: Boolean by mutableStateOf(false)

    /**
     * Backs `setDrawerHeaderBackground(String?)`. `null` must fall back to `R.drawable.header`, as
     * navlib's `setAccountHeaderBackground(null)` did.
     *
     * Assigning the *same* value does not recompose, and `SettingsFragment` deliberately sets
     * null-then-value to force a refresh - so the header's image loader must key on the path
     * (`remember(path)`) rather than rely on the write alone.
     */
    var headerBackground: String? by mutableStateOf(null)

    /**
     * Bumped by every `setDrawerHeaderBackground` call so `AppDrawer` re-decodes the file even when
     * the path is unchanged - which it always is, because every pick overwrites the same
     * `filesDir/header.<ext>`. `SettingsFragment` writes null-then-value to force the reload, but
     * both writes land in one frame, so the composition only ever reads the final value.
     */
    var headerBackgroundToken: Int by mutableStateOf(0)

    // ---- component state ----

    /**
     * `ModalNavigationDrawer`'s own state, constructed here rather than by `rememberDrawerState`
     * so the non-composable `openDrawer()` delegate has something to call.
     *
     * Safe in material3 1.4.0: `DrawerState(DrawerValue, Function1)` is `ACC_PUBLIC` with no
     * experimental opt-in, and the drawer composable injects the `Density` this state needs.
     *
     * `openDrawer()` must guard on `drawerMode(...) != DrawerMode.Permanent` - navlib returned
     * early there too, since a permanent drawer has no closed state to open from.
     */
    val drawerState: DrawerState = DrawerState(DrawerValue.Closed)

    /**
     * **Exactly ONE instance, owned here.** `MainSnackbar`, `ErrorSnackbar` and the `Scaffold`'s
     * `snackbarHost` all receive *this* one. Handing any of them a second `SnackbarHostState`
     * compiles fine and silently leaves that host's snackbars permanently invisible - including the
     * error host, which is the app's only user-visible API-failure report.
     */
    val snackbarHostState: SnackbarHostState = SnackbarHostState()
}
