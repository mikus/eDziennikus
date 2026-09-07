/*
 * Copyright (c) Mikolaj Olszewski 2026-9-2.
 */
package eu.mikus.edziennik.ui.shell

import android.content.res.Configuration
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.enums.FeatureType
import eu.mikus.edziennik.data.db.enums.LoginType
import eu.mikus.edziennik.data.db.enums.MetadataType
import eu.mikus.edziennik.ui.base.ScreenAction
import eu.mikus.edziennik.ui.base.enums.NavTarget
import eu.mikus.edziennik.utils.models.UnreadCounter
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * The M3 shell's whole decision surface. Compose UI tests cannot run in this project (Robolectric
 * 4.14.1 + compose-bom 2026.06.00 cannot host `createComposeRule`, and there is no
 * `app/src/androidTest`), so this file is the shell swap's ONLY automated coverage - the
 * composables that consume these functions have none. Treat a red test here as a shipped
 * regression, not a stale expectation.
 *
 * Pure Jupiter, no Robolectric: the types reference [NavTarget], whose `fragmentClass` is a
 * `Class<out Fragment>?`, but nothing here ever touches that field.
 */
class ShellPolicyTest {

    // ---- shellBackPolicy: the full 16-row truth table ----

    private val bools = listOf(true, false)

    @Test
    fun `an open sheet closes first, whatever the drawer and the setting do`() {
        // 8 of the 16 rows. ModalBottomSheet is the topmost overlay, so it is always dismissed
        // first - deliberately sheet-first even though navlib's onBackPressed is drawer-first.
        for (drawerOpen in bools) for (dismissible in bools) for (openDrawerOnBack in bools) {
            assertEquals(
                ShellBack.CloseSheet,
                shellBackPolicy(
                    sheetOpen = true,
                    drawerOpen = drawerOpen,
                    drawerDismissible = dismissible,
                    openDrawerOnBack = openDrawerOnBack,
                ),
                "sheetOpen must win (drawerOpen=$drawerOpen, dismissible=$dismissible, " +
                    "openDrawerOnBack=$openDrawerOnBack)",
            )
        }
    }

    /**
     * The row the whole four-parameter signature exists for. `config.ui.openDrawerOnBackPressed`
     * is a shipped, user-togglable setting whose "on" branch OPENS the drawer
     * (`MainActivity.kt:1144-1156`). Resolve this to [ShellBack.Content] instead and back falls
     * through to `navigateUp()` -> `popBackStack()` false -> `finish()`: the app exits and the
     * setting becomes a visible no-op.
     */
    @Test
    fun `with the setting on and nothing open, back opens the drawer`() {
        assertEquals(
            ShellBack.OpenDrawer,
            shellBackPolicy(
                sheetOpen = false,
                drawerOpen = false,
                drawerDismissible = true,
                openDrawerOnBack = true,
            ),
        )
    }

    @Test
    fun `with the setting on and the drawer open, back goes to the content`() {
        assertEquals(
            ShellBack.Content,
            shellBackPolicy(
                sheetOpen = false,
                drawerOpen = true,
                drawerDismissible = true,
                openDrawerOnBack = true,
            ),
        )
    }

    @Test
    fun `with the setting on and a permanent drawer, back goes to the content`() {
        // navlib's own `isOpen()` returns true forever in permanent mode, and MainActivity:1147
        // reads it unguarded on this path - so a permanent drawer reports open and back navigates.
        assertEquals(
            ShellBack.Content,
            shellBackPolicy(
                sheetOpen = false,
                drawerOpen = true,
                drawerDismissible = false,
                openDrawerOnBack = true,
            ),
        )
    }

    @Test
    fun `with the setting on and a permanent drawer reported closed, back still opens the drawer`() {
        // The 16th row. Callers must report a permanent drawer as OPEN (navlib's isOpen()
        // semantics, spec 7.5); this row only fixes what happens if one does not.
        assertEquals(
            ShellBack.OpenDrawer,
            shellBackPolicy(
                sheetOpen = false,
                drawerOpen = false,
                drawerDismissible = false,
                openDrawerOnBack = true,
            ),
        )
    }

    @Test
    fun `with the setting off, an open dismissible drawer closes`() {
        assertEquals(
            ShellBack.CloseDrawer,
            shellBackPolicy(
                sheetOpen = false,
                drawerOpen = true,
                drawerDismissible = true,
                openDrawerOnBack = false,
            ),
        )
    }

    @Test
    fun `a non-dismissible drawer is never closed by back`() {
        // navlib guards its own close with fixedDrawerEnabled(); a permanent drawer has no
        // closed state to return to.
        val decision = shellBackPolicy(
            sheetOpen = false,
            drawerOpen = true,
            drawerDismissible = false,
            openDrawerOnBack = false,
        )
        assertNotEquals(ShellBack.CloseDrawer, decision)
        assertEquals(ShellBack.Content, decision)
    }

    @Test
    fun `with the setting off and a closed drawer, back goes to the content`() {
        assertEquals(
            ShellBack.Content,
            shellBackPolicy(
                sheetOpen = false,
                drawerOpen = false,
                drawerDismissible = true,
                openDrawerOnBack = false,
            ),
        )
    }

    @Test
    fun `with the setting off and nothing open at all, back goes to the content`() {
        assertEquals(
            ShellBack.Content,
            shellBackPolicy(
                sheetOpen = false,
                drawerOpen = false,
                drawerDismissible = false,
                openDrawerOnBack = false,
            ),
        )
    }

    // ---- drawerMode ----

    private val portrait = Configuration.ORIENTATION_PORTRAIT
    private val landscape = Configuration.ORIENTATION_LANDSCAPE

    @Test
    fun `portrait is never permanent, at any width`() {
        for (width in listOf(0, 320, 479, 480, 599, 600, 899, 900, 1200)) for (mini in bools) {
            assertNotEquals(
                DrawerMode.Permanent,
                drawerMode(portrait, screenWidthDp = width, miniMenuVisible = mini),
                "portrait must never be permanent (width=$width, miniMenuVisible=$mini)",
            )
        }
    }

    @Test
    fun `portrait honours the mini menu setting at any width`() {
        for (width in listOf(320, 479, 480, 899, 900, 1200)) {
            assertEquals(DrawerMode.Mini, drawerMode(portrait, width, miniMenuVisible = true), "w=$width")
            assertEquals(DrawerMode.Modal, drawerMode(portrait, width, miniMenuVisible = false), "w=$width")
        }
    }

    @Test
    fun `landscape from 480 up to 899 is a rail regardless of the setting`() {
        // navlib reads miniDrawerVisibleLandscape here, which MainActivity:288 pins to null
        // forever - so the portrait setting has no say in landscape.
        for (width in listOf(480, 600, 899)) for (mini in bools) {
            assertEquals(
                DrawerMode.Mini,
                drawerMode(landscape, width, miniMenuVisible = mini),
                "w=$width, miniMenuVisible=$mini",
            )
        }
    }

    @Test
    fun `landscape below 480 is modal regardless of the setting`() {
        for (mini in bools) {
            assertEquals(DrawerMode.Modal, drawerMode(landscape, 479, miniMenuVisible = mini))
            assertEquals(DrawerMode.Modal, drawerMode(landscape, 320, miniMenuVisible = mini))
        }
    }

    @Test
    fun `landscape from 900 up is permanent regardless of the setting`() {
        for (mini in bools) {
            assertEquals(DrawerMode.Permanent, drawerMode(landscape, 900, miniMenuVisible = mini))
            assertEquals(DrawerMode.Permanent, drawerMode(landscape, 1200, miniMenuVisible = mini))
        }
    }

    @Test
    fun `the landscape rail and the permanent drawer never overlap at the 900 boundary`() {
        assertEquals(DrawerMode.Mini, drawerMode(landscape, 899, miniMenuVisible = true))
        assertEquals(DrawerMode.Permanent, drawerMode(landscape, 900, miniMenuVisible = true))
    }

    // ---- buildDrawerEntries ----

    private val allFeatures = FeatureType.values().toSet()

    private fun entries(
        devMode: Boolean = false,
        profileFeatures: Set<FeatureType> = allFeatures,
        miniMenuButtons: Set<NavTarget> = emptySet(),
    ) = buildDrawerEntries(
        targets = NavTarget.values().toList(),
        devMode = devMode,
        profileFeatures = profileFeatures,
        miniMenuButtons = miniMenuButtons,
    )

    private fun List<DrawerEntry>.rowsBeforeTheExpandable() =
        takeWhile { it is DrawerEntry.Row }.map { (it as DrawerEntry.Row).row }

    @Test
    fun `main opens with the DRAWER targets, in NavTarget order, at level 1`() {
        val rows = entries().main.rowsBeforeTheExpandable()
        assertEquals(
            listOf(
                NavTarget.HOME,
                NavTarget.TIMETABLE,
                NavTarget.AGENDA,
                NavTarget.GRADES,
                NavTarget.MESSAGES,
                NavTarget.HOMEWORK,
                NavTarget.BEHAVIOUR,
                NavTarget.ATTENDANCE,
                NavTarget.ANNOUNCEMENTS,
            ),
            rows.map { it.target },
        )
        assertTrue(rows.all { it.level == 1 }, "DRAWER rows are level 1")
    }

    @Test
    fun `the More expandable follows the DRAWER rows and holds DRAWER_MORE at level 2`() {
        val main = entries().main
        val expandable = main[main.rowsBeforeTheExpandable().size] as DrawerEntry.Expandable
        assertEquals(R.string.menu_more, expandable.nameRes)
        assertEquals(listOf(NavTarget.NOTES, NavTarget.TEACHERS), expandable.children.map { it.target })
        assertTrue(expandable.children.all { it.level == 2 }, "DRAWER_MORE rows are level 2")
    }

    @Test
    fun `a divider separates the expandable from the DRAWER_BOTTOM rows`() {
        val main = entries().main
        val expandableAt = main.indexOfFirst { it is DrawerEntry.Expandable }
        assertEquals(DrawerEntry.Divider, main[expandableAt + 1])
        assertEquals(
            listOf(NavTarget.NOTIFICATIONS, NavTarget.SETTINGS),
            main.drop(expandableAt + 2).map { (it as DrawerEntry.Row).row.target },
        )
    }

    @Test
    fun `a devModeOnly target appears only in dev mode`() {
        val off = entries(devMode = false).main.mapNotNull { (it as? DrawerEntry.Row)?.row?.target }
        val on = entries(devMode = true).main.mapNotNull { (it as? DrawerEntry.Row)?.row?.target }
        assertFalse(NavTarget.LAB in off, "LAB is devModeOnly")
        assertTrue(NavTarget.LAB in on)
        assertEquals(off.size + 1, on.size, "dev mode adds LAB and nothing else to the drawer")
    }

    @Test
    fun `profileSettings holds exactly the three PROFILE_LIST targets`() {
        val profileSettings = entries().profileSettings
        assertEquals(3, profileSettings.size)
        assertEquals(
            listOf(NavTarget.PROFILE_ADD, NavTarget.PROFILE_MARK_AS_READ, NavTarget.PROFILE_SYNC_ALL),
            profileSettings.map { it.target },
        )
        assertEquals(R.string.drawer_add_new_profile_desc, profileSettings[0].descriptionRes)
    }

    /**
     * The guard against the phase's quietest possible regression: PROFILE_ADD renders inside the
     * expanded profile list, not in the main list, and `requestHandler.requestLogin()` has exactly
     * two references in the whole app - its declaration and that row. Drop it and adding a profile
     * becomes impossible without reinstalling.
     */
    @Test
    fun `no profile-settings row appears anywhere in main`() {
        val result = entries(devMode = true)
        val mainTargets = result.main.flatMap { entry ->
            when (entry) {
                is DrawerEntry.Row -> listOf(entry.row.target)
                is DrawerEntry.Expandable -> entry.children.map { it.target }
                DrawerEntry.Divider -> emptyList()
            }
        }
        for (row in result.profileSettings) {
            assertFalse(row.target in mainTargets, "${row.target} must render only in the profile list")
        }
    }

    @Test
    fun `a target whose feature the profile lacks is dropped`() {
        val rows = entries(profileFeatures = emptySet()).main.rowsBeforeTheExpandable()
        // AGENDA and HOMEWORK survive an empty feature set: both are isUIAlwaysAvailable, which is
        // half of Profile.hasUIFeature. HOME has no featureType at all.
        assertEquals(
            listOf(NavTarget.HOME, NavTarget.AGENDA, NavTarget.HOMEWORK),
            rows.map { it.target },
        )
    }

    @Test
    fun `hiddenInRail is the inverse of the mini menu button set`() {
        val rows = entries(miniMenuButtons = setOf(NavTarget.HOME, NavTarget.GRADES))
            .main.rowsBeforeTheExpandable()
        assertFalse(rows.first { it.target == NavTarget.HOME }.hiddenInRail)
        assertFalse(rows.first { it.target == NavTarget.GRADES }.hiddenInRail)
        assertTrue(rows.first { it.target == NavTarget.TIMETABLE }.hiddenInRail)
    }

    // ---- drawerRowAction / profileAction ----

    private fun row(target: NavTarget?) = DrawerRow(
        target = target,
        nameRes = target?.nameRes ?: R.string.menu_more,
        descriptionRes = null,
        icon = target?.icon,
        badge = target?.badgeType,
        level = 1,
        hiddenInRail = false,
    )

    /**
     * navlib swallows this tap entirely (`NavDrawer$init$4$1` returns at offset 23, before the
     * app's listener at 59). Calling `navigate()` instead makes `decideNavigation` return RELOAD,
     * and `navigateImpl` then destroys and rebuilds the visible fragment on a gesture that is a
     * no-op today.
     */
    @Test
    fun `tapping the row you are already on only closes the drawer`() {
        val action = drawerRowAction(row(NavTarget.GRADES), selectedTarget = NavTarget.GRADES)
        assertEquals(DrawerAction.CloseOnly, action)
        assertNotEquals(DrawerAction.Navigate(NavTarget.GRADES), action)
    }

    @Test
    fun `tapping another row navigates to it`() {
        assertEquals(
            DrawerAction.Navigate(NavTarget.GRADES),
            drawerRowAction(row(NavTarget.GRADES), selectedTarget = NavTarget.HOME),
        )
    }

    @Test
    fun `an expandable row toggles instead of navigating`() {
        assertEquals(
            DrawerAction.ToggleExpandable,
            drawerRowAction(row(target = null), selectedTarget = NavTarget.HOME),
        )
    }

    /**
     * navlib swallows this one too (`NavDrawer$init$3$1`, offset 72). `navigate(profileId = ...)`
     * passes `navTarget = null`, so `canNavigate()` runs - and on MessagesComposeFragment that
     * pops the discard-draft dialog.
     */
    @Test
    fun `tapping the profile you are already on only closes the drawer`() {
        val action = profileAction(id = 4, currentProfileId = 4)
        assertEquals(DrawerAction.CloseOnly, action)
        assertNotEquals(DrawerAction.SwitchProfile(4), action)
    }

    @Test
    fun `tapping another profile switches to it`() {
        assertEquals(DrawerAction.SwitchProfile(9), profileAction(id = 9, currentProfileId = 4))
    }

    // ---- deriveBadges ----

    private fun profile(id: Int) = Profile(
        id = id,
        loginStoreId = 1,
        loginStoreType = LoginType.LIBRUS,
    )

    private fun counter(profileId: Int, thingType: MetadataType, count: Int) =
        UnreadCounter().also {
            it.profileId = profileId
            it.thingType = thingType
            it.count = count
        }

    private val targets = NavTarget.values().toList()

    @Test
    fun `per-target badges are keyed by NavTarget id`() {
        val badges = deriveBadges(
            unreadCounts = listOf(
                counter(1, MetadataType.GRADE, 3),
                counter(1, MetadataType.MESSAGE, 5),
            ),
            profiles = listOf(profile(1)),
            targets = targets,
            currentProfileId = 1,
        )
        assertEquals(3, badges.perTarget[NavTarget.GRADES.id])
        assertEquals(5, badges.perTarget[NavTarget.MESSAGES.id])
        assertNull(badges.perTarget[NavTarget.HOME.id], "HOME declares no badgeType")
    }

    @Test
    fun `another profile's counts never leak into the per-target badges or the total`() {
        val badges = deriveBadges(
            unreadCounts = listOf(
                counter(1, MetadataType.GRADE, 3),
                counter(2, MetadataType.GRADE, 40),
                counter(2, MetadataType.MESSAGE, 7),
            ),
            profiles = listOf(profile(1), profile(2)),
            targets = targets,
            currentProfileId = 1,
        )
        assertEquals(3, badges.perTarget[NavTarget.GRADES.id])
        assertEquals(3, badges.total)
        assertNull(badges.perTarget[NavTarget.MESSAGES.id])
    }

    @Test
    fun `per-profile badges sum every type of that one profile`() {
        val badges = deriveBadges(
            unreadCounts = listOf(
                counter(1, MetadataType.GRADE, 3),
                counter(1, MetadataType.MESSAGE, 5),
                counter(2, MetadataType.GRADE, 40),
            ),
            profiles = listOf(profile(1), profile(2)),
            targets = targets,
            currentProfileId = 1,
        )
        assertEquals(8, badges.perProfile[1])
        assertEquals(40, badges.perProfile[2])
    }

    @Test
    fun `the total counts only the types a drawer row can show`() {
        // LUCKY_NUMBER is no NavTarget's badgeType, and navlib accumulates the hamburger total
        // inside the loop that skips unmapped types (offset 851, before the += at 1019).
        val badges = deriveBadges(
            unreadCounts = listOf(
                counter(1, MetadataType.GRADE, 3),
                counter(1, MetadataType.LUCKY_NUMBER, 100),
            ),
            profiles = listOf(profile(1)),
            targets = targets,
            currentProfileId = 1,
        )
        assertEquals(3, badges.total)
    }

    @Test
    fun `badge text hides a zero and clamps at 99`() {
        assertNull(badgeText(0))
        assertEquals("1", badgeText(1))
        assertEquals("98", badgeText(98))
        assertEquals("99+", badgeText(99), "navlib clamps at >= 99, not > 99")
        assertEquals("99+", badgeText(1234))
    }

    @Test
    fun `the hamburger total is hidden at zero but never clamped`() {
        assertNull(totalBadgeText(0))
        assertEquals("100", totalBadgeText(100), "BadgeDrawable.setCount gets the raw total")
        assertEquals("1234", totalBadgeText(1234))
    }

    // ---- subtitleOf ----

    @Test
    fun `idle with unread renders the plural with the profile name and the count`() {
        val subtitle = subtitleOf(SyncSubtitle.Idle, unreadTotal = 3, profileName = "Jan Kowalski")
        assertEquals(R.plurals.toolbar_subtitle_with_unread, subtitle.res)
        assertEquals(listOf<Any>("Jan Kowalski", 3), subtitle.args)
        assertEquals(3, subtitle.quantity)
    }

    @Test
    fun `idle with no unread renders the plain profile name`() {
        val subtitle = subtitleOf(SyncSubtitle.Idle, unreadTotal = 0, profileName = "Jan Kowalski")
        assertEquals(R.string.toolbar_subtitle, subtitle.res)
        assertEquals(listOf<Any>("Jan Kowalski"), subtitle.args)
        assertNull(subtitle.quantity)
    }

    @Test
    fun `a sync with no progress yet renders the syncing string`() {
        val subtitle = subtitleOf(SyncSubtitle.Syncing(-1f, null), unreadTotal = 3, profileName = "Jan")
        assertEquals(R.string.toolbar_subtitle_syncing, subtitle.res)
        assertEquals(emptyList<Any>(), subtitle.args)
    }

    @Test
    fun `an indeterminate sync passes its progress text through`() {
        val subtitle = subtitleOf(
            SyncSubtitle.Syncing(-1f, "Pobieranie ocen"),
            unreadTotal = 3,
            profileName = "Jan",
        )
        assertEquals(R.string.toolbar_subtitle, subtitle.res)
        assertEquals(listOf<Any>("Pobieranie ocen"), subtitle.args)
    }

    @Test
    fun `a sync with progress renders the percentage form`() {
        val subtitle = subtitleOf(
            SyncSubtitle.Syncing(42f, "Pobieranie ocen"),
            unreadTotal = 3,
            profileName = "Jan",
        )
        assertEquals(R.string.toolbar_subtitle_syncing_format, subtitle.res)
        assertEquals(listOf<Any>(42, "Pobieranie ocen"), subtitle.args)
    }

    @Test
    fun `a finished sync renders the done resource, not a Polish literal`() {
        val subtitle = subtitleOf(SyncSubtitle.Done, unreadTotal = 3, profileName = "Jan")
        assertEquals(R.string.sync_status_done, subtitle.res)
        assertEquals(emptyList<Any>(), subtitle.args)
    }

    // ---- toSheetRows: the three surviving ScreenChromeMappingTest cases ----

    private val icon = CommunityMaterial.Icon.cmd_cog_outline

    private fun action(
        titleRes: Int,
        descriptionRes: Int? = null,
        separatorBefore: Boolean = false,
        onClick: () -> Unit = {},
    ) = ScreenAction(titleRes, icon, descriptionRes, separatorBefore, onClick)

    @Test
    fun `preserves row order`() {
        val rows = listOf(action(1), action(2), action(3)).toSheetRows {}
        assertEquals(listOf(1, 2, 3), rows.map { it.titleRes })
    }

    @Test
    fun `carries separatorBefore on the row the separator precedes`() {
        val rows = listOf(action(1), action(2, separatorBefore = true)).toSheetRows {}
        assertEquals(2, rows.size, "the separator is a flag on its row, not a row of its own")
        assertFalse(rows[0].separatorBefore, "the separator must precede its row, not follow it")
        assertTrue(rows[1].separatorBefore)
    }

    @Test
    fun `applies descriptionRes only when non-null`() {
        val rows = listOf(action(1, descriptionRes = 99), action(2)).toSheetRows {}
        assertEquals(99, rows[0].descriptionRes)
        assertNull(rows[1].descriptionRes)
    }

    // ---- nextSubtitle: the subtitle's transition half ----

    @Test
    fun `a sync starting on the active profile shows the indeterminate subtitle`() {
        assertEquals(
            SyncSubtitle.Syncing(progress = -1f, text = null),
            nextSubtitle(SyncSubtitle.Idle, SyncSignal.Started(profileId = 3), activeProfileId = 3),
        )
    }

    @Test
    fun `progress on the active profile carries its values`() {
        assertEquals(
            SyncSubtitle.Syncing(50f, "Syncing timetable…"),
            nextSubtitle(
                SyncSubtitle.Syncing(progress = -1f, text = null),
                SyncSignal.Progress(profileId = 3, progress = 50f, text = "Syncing timetable…"),
                activeProfileId = 3,
            ),
        )
    }

    @Test
    fun `a sync finishing on the active profile ends the subtitle`() {
        assertEquals(
            SyncSubtitle.Done,
            nextSubtitle(
                SyncSubtitle.Syncing(progress = -1f, text = null),
                SyncSignal.Finished(profileId = 3),
                activeProfileId = 3,
            ),
        )
    }

    @Test
    fun `another profile's sync does not hijack the toolbar`() {
        // Deliberate: a background sync of a non-active profile leaves the subtitle alone.
        assertEquals(
            SyncSubtitle.Idle,
            nextSubtitle(SyncSubtitle.Idle, SyncSignal.Started(profileId = 2), activeProfileId = 3),
        )
        assertEquals(
            SyncSubtitle.Idle,
            nextSubtitle(
                SyncSubtitle.Idle,
                SyncSignal.Progress(profileId = 2, progress = 40f, text = "x"),
                activeProfileId = 3,
            ),
        )
    }

    @Test
    fun `another profile's finish cannot end the subtitle`() {
        // The drop that caused the stuck-"Syncing…" bug. Kept, because clearing on any profile's
        // finish would end a sync the toolbar is legitimately reporting; ProfileChanged is the
        // recovery instead - see the next test.
        val syncing = SyncSubtitle.Syncing(progress = -1f, text = null)
        assertEquals(syncing, nextSubtitle(syncing, SyncSignal.Finished(profileId = 2), activeProfileId = 3))
    }

    /**
     * The regression test. A pull-to-refresh on profile 3 followed by a switch to profile 1 left
     * `Finished(3)` arriving against an active id of 1, where it is dropped - so before the fix the
     * subtitle stayed on "Syncing…" for ever, with no other writer able to clear it.
     */
    @Test
    fun `switching profile mid-sync clears a subtitle its own finish can no longer clear`() {
        var subtitle: SyncSubtitle = SyncSubtitle.Idle

        subtitle = nextSubtitle(subtitle, SyncSignal.Started(profileId = 3), activeProfileId = 3)
        assertEquals(SyncSubtitle.Syncing(progress = -1f, text = null), subtitle)

        // the user switches to profile 1; App.profileId is 1 from here on
        subtitle = nextSubtitle(subtitle, SyncSignal.ProfileChanged, activeProfileId = 1)
        assertEquals(SyncSubtitle.Idle, subtitle)

        // the old profile's sync lands late and is still ignored - and must not resurrect Syncing
        subtitle = nextSubtitle(subtitle, SyncSignal.Finished(profileId = 3), activeProfileId = 1)
        assertEquals(SyncSubtitle.Idle, subtitle)
    }

    @Test
    fun `a profile change resets the subtitle from every state`() {
        for (state in listOf(
            SyncSubtitle.Idle,
            SyncSubtitle.Syncing(progress = -1f, text = null),
            SyncSubtitle.Syncing(72f, "Syncing grades…"),
            SyncSubtitle.Done,
        )) {
            assertEquals(
                SyncSubtitle.Idle,
                nextSubtitle(state, SyncSignal.ProfileChanged, activeProfileId = 1),
                "from $state",
            )
        }
    }

    @Test
    fun `a failure ends the subtitle whatever profile it was for`() {
        // onApiTaskErrorEvent is not profile-gated, and the error snackbar reports the failure.
        val syncing = SyncSubtitle.Syncing(progress = -1f, text = null)
        assertEquals(SyncSubtitle.Done, nextSubtitle(syncing, SyncSignal.Failed, activeProfileId = 3))
        assertEquals(SyncSubtitle.Done, nextSubtitle(syncing, SyncSignal.Failed, activeProfileId = 99))
    }
}
