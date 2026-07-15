/*
 * Copyright (c) Mikolaj Olszewski 2026-7-15.
 */

package eu.mikus.edziennik.ui.settings

import eu.mikus.edziennik.R
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsBuilderTest {

    private fun snapshot(
        snowfallWindow: Boolean = true, snowfall: Boolean = false,
        eggfallNear: Boolean = true, eggfall: Boolean = false,
        miniDrawer: Boolean = false, openDrawerOnBack: Boolean = false,
        syncEnabled: Boolean = true, onlyWifi: Boolean = false,
        quietHoursEnabled: Boolean = false, notifyUpdates: Boolean = true,
        sdkAtLeastKitKat: Boolean = true,
        hasTimetable: Boolean = true, hasAgenda: Boolean = true, hasGrades: Boolean = true,
        hasMessages: Boolean = true, hasAttendance: Boolean = true,
        countInSeconds: Boolean = false, isLibrus: Boolean = true,
        showTeacherAbsences: Boolean = true, devMode: Boolean = true, hideSticksFromOld: Boolean = false,
    ) = SettingsSnapshot(
        profileName = "Jan", profileSubname = "Klasa 1a",
        snowfallWindow = snowfallWindow, snowfall = snowfall,
        eggfallNear = eggfallNear, eggfall = eggfall,
        themeName = "Motyw jasny", miniDrawer = miniDrawer, openDrawerOnBack = openDrawerOnBack,
        syncEnabled = syncEnabled, syncInterval = "co 1 godzinę", onlyWifi = onlyWifi,
        quietHoursEnabled = quietHoursEnabled, quietHours = "22:30 - 6:30", notifyUpdates = notifyUpdates,
        sdkAtLeastKitKat = sdkAtLeastKitKat,
        hasTimetable = hasTimetable, hasAgenda = hasAgenda, hasGrades = hasGrades,
        hasMessages = hasMessages, hasAttendance = hasAttendance,
        bellSync = "wyłączone", countInSeconds = countInSeconds, isLibrus = isLibrus,
        showTeacherAbsences = showTeacherAbsences, devMode = devMode, hideSticksFromOld = hideSticksFromOld,
        versionText = "4.0, debug",
    )

    private fun SettingsCardUi.more(): SettingsItem.More = items.filterIsInstance<SettingsItem.More>().single()

    @Test
    fun `produces five cards in order Profile Theme Sync Register About`() {
        val cards = SettingsBuilder.build(snapshot())
        assertEquals(5, cards.size)
        assertNull(cards[0].titleRes)                                        // Profile
        assertEquals(R.string.settings_card_theme_title, cards[1].titleRes)
        assertEquals(R.string.settings_card_sync_title, cards[2].titleRes)
        assertEquals(R.string.settings_card_register_title, cards[3].titleRes)
        assertNull(cards[4].titleRes)                                        // About
        assertEquals(CardStyle.AboutBlueDark, cards[4].style)
    }

    @Test
    fun `profile card has a Profile row and an add-student action`() {
        val profile = SettingsBuilder.build(snapshot())[0]
        assertIs<SettingsItem.Profile>(profile.items[0])
        val add = profile.items[1] as SettingsItem.Action
        assertEquals(SettingsAction.AddStudent, add.action)
    }

    @Test
    fun `snowfall hidden outside its seasonal window`() {
        val on = SettingsBuilder.build(snapshot(snowfallWindow = true))[1]
        val off = SettingsBuilder.build(snapshot(snowfallWindow = false))[1]
        assertTrue(on.items.any { it is SettingsItem.Switch && it.toggle == SettingsToggle.SNOWFALL })
        assertTrue(off.items.none { it is SettingsItem.Switch && it.toggle == SettingsToggle.SNOWFALL })
    }

    @Test
    fun `eggfall hidden when not near Easter`() {
        val off = SettingsBuilder.build(snapshot(eggfallNear = false))[1]
        assertTrue(off.items.none { it is SettingsItem.Switch && it.toggle == SettingsToggle.EGGFALL })
    }

    @Test
    fun `theme action carries the dynamic theme name`() {
        val theme = SettingsBuilder.build(snapshot())[1]
        val action = theme.items.filterIsInstance<SettingsItem.Action>().single { it.action == SettingsAction.Theme }
        assertEquals("Motyw jasny", action.subText)
    }

    @Test
    fun `only-wifi visible only when sync enabled`() {
        val on = SettingsBuilder.build(snapshot(syncEnabled = true))[2]
        val off = SettingsBuilder.build(snapshot(syncEnabled = false))[2]
        assertTrue(on.items.any { it is SettingsItem.Switch && it.toggle == SettingsToggle.SYNC_ONLY_WIFI })
        assertTrue(off.items.none { it is SettingsItem.Switch && it.toggle == SettingsToggle.SYNC_ONLY_WIFI })
    }

    @Test
    fun `sync interval ActionSwitch carries enabled + dynamic interval`() {
        val sync = SettingsBuilder.build(snapshot(syncEnabled = true))[2]
        val item = sync.items.filterIsInstance<SettingsItem.ActionSwitch>().single { it.toggle == SettingsToggle.SYNC_ENABLED }
        assertTrue(item.checked)
        assertEquals("co 1 godzinę", item.subTextChecked)
        assertEquals(SettingsAction.SyncInterval, item.action)
    }

    @Test
    fun `quiet-hours ActionSwitch carries enabled + dynamic range`() {
        val item = SettingsBuilder.build(snapshot(quietHoursEnabled = true))[2]
            .items.filterIsInstance<SettingsItem.ActionSwitch>().single { it.toggle == SettingsToggle.QUIET_HOURS }
        assertEquals("22:30 - 6:30", item.subTextChecked)
        assertEquals(SettingsAction.QuietHours, item.action)
    }

    @Test
    fun `notification-system hidden below KitKat`() {
        val more = SettingsBuilder.build(snapshot(sdkAtLeastKitKat = false))[2].more()
        assertTrue(more.items.none { it is SettingsItem.Action && it.action == SettingsAction.NotificationSystem })
    }

    @Test
    fun `register config rows gated per feature`() {
        val none = SettingsBuilder.build(
            snapshot(hasTimetable = false, hasAgenda = false, hasGrades = false, hasMessages = false, hasAttendance = false),
        )[3]
        assertTrue(none.items.filterIsInstance<SettingsItem.Action>().none {
            it.action in setOf(
                SettingsAction.TimetableConfig, SettingsAction.AgendaConfig, SettingsAction.GradesConfig,
                SettingsAction.MessagesConfig, SettingsAction.AttendanceConfig,
            )
        })
    }

    @Test
    fun `bell-sync action carries dynamic summary`() {
        val more = SettingsBuilder.build(snapshot())[3].more()
        val bell = more.items.filterIsInstance<SettingsItem.Action>().single { it.action == SettingsAction.BellSync }
        assertEquals("wyłączone", bell.subText)
    }

    @Test
    fun `teacher-absences gated on Librus and hide-sticks gated on devMode plus grades`() {
        val libOff = SettingsBuilder.build(snapshot(isLibrus = false)).let { it[3].more() }
        assertTrue(libOff.items.none { it is SettingsItem.Switch && it.toggle == SettingsToggle.SHOW_TEACHER_ABSENCES })

        val devOff = SettingsBuilder.build(snapshot(devMode = false)).let { it[3].more() }
        assertTrue(devOff.items.none { it is SettingsItem.Switch && it.toggle == SettingsToggle.HIDE_STICKS_FROM_OLD })

        val noGrades = SettingsBuilder.build(snapshot(hasGrades = false)).let { it[3].more() }
        assertTrue(noGrades.items.none { it is SettingsItem.Switch && it.toggle == SettingsToggle.HIDE_STICKS_FROM_OLD })
    }

    @Test
    fun `about card version action and crash gated on devMode`() {
        val about = SettingsBuilder.build(snapshot(devMode = true))[4]
        val version = about.items.filterIsInstance<SettingsItem.Action>().single { it.action == SettingsAction.VersionTap }
        assertEquals("4.0, debug", version.subText)
        val lastMore = about.items.filterIsInstance<SettingsItem.More>().last()
        assertTrue(lastMore.items.any { it is SettingsItem.Action && it.action == SettingsAction.Crash })

        val prod = SettingsBuilder.build(snapshot(devMode = false))[4]
        val prodMore = prod.items.filterIsInstance<SettingsItem.More>().last()
        assertTrue(prodMore.items.none { it is SettingsItem.Action && it.action == SettingsAction.Crash })
    }
}
