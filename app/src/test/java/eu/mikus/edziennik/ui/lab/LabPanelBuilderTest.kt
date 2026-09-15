/*
 * Copyright (c) Mikolaj Olszewski 2026-9-15.
 */

package eu.mikus.edziennik.ui.lab

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LabPanelBuilderTest {

    private fun snapshot(
        profileIsZero: Boolean = false,
        chuckerEnabled: Boolean = true,
        archiverEnabled: Boolean = true,
        apiAvailabilityCheck: Boolean = false,
    ) = LabSnapshot(
        profileIsZero = profileIsZero,
        chuckerEnabled = chuckerEnabled,
        archiverEnabled = archiverEnabled,
        apiAvailabilityCheck = apiAvailabilityCheck,
        profiles = listOf(LabProfileEntry(1, "Jan", false), LabProfileEntry(4, "Ola", true)),
        currentProfileId = 4,
        cookies = emptyList(),
    )

    private fun List<LabControl>.actions() = filterIsInstance<LabControl.Button>().map { it.action }
    private fun List<LabControl>.toggles() = filterIsInstance<LabControl.Check>().map { it.toggle }

    @Test
    fun `the full roster is seventeen interactive controls plus one readout`() {
        val controls = LabPanelBuilder.build(snapshot())
        assertEquals(18, controls.size)
        assertEquals(1, controls.filterIsInstance<LabControl.Cookies>().size)
        // A per-kind breakdown, not `size - cookies`: that would restate the two lines above and could
        // never fail on its own. Buttons are pinned against LabAction.entries in the next test.
        assertEquals(3, controls.toggles().size)
        assertEquals(1, controls.filterIsInstance<LabControl.ProfilePicker>().size)
    }

    @Test
    fun `every LabAction has exactly one button when nothing is gated out`() {
        // Anti-transcription-miss: the enum and the roster are checked against each other.
        assertEquals(LabAction.entries.toSet(), LabPanelBuilder.build(snapshot()).actions().toSet())
        assertEquals(LabAction.entries.size, LabPanelBuilder.build(snapshot()).actions().size)
    }

    @Test
    fun `the profile gate hides exactly nine controls`() {
        // LabPageFragment.kt@53a07964:61-71 - last10unseen, fullSync, clearProfile, clearEndpointTimers, rodo,
        // removeHomework, resetEventTypes, unarchive, profile.
        val full = LabPanelBuilder.build(snapshot(profileIsZero = false))
        val gated = LabPanelBuilder.build(snapshot(profileIsZero = true))
        assertEquals(9, full.size - gated.size)
        assertEquals(
            setOf(
                LabAction.Last10Unseen, LabAction.FullSync, LabAction.ClearProfile,
                LabAction.ClearEndpointTimers, LabAction.Rodo, LabAction.RemoveHomework,
                LabAction.ResetEventTypes, LabAction.Unarchive,
            ),
            full.actions().toSet() - gated.actions().toSet(),
        )
        assertTrue(gated.none { it is LabControl.ProfilePicker })
    }

    @Test
    fun `the profile gate leaves five buttons, all three checkboxes and the readout`() {
        val gated = LabPanelBuilder.build(snapshot(profileIsZero = true))
        assertEquals(
            listOf(
                LabAction.OpenChucker, LabAction.ClearCookies, LabAction.ResetCert,
                LabAction.DisableDevMode, LabAction.RebuildConfig,
            ),
            gated.actions(),
        )
        assertEquals(3, gated.filterIsInstance<LabControl.Check>().size)
        assertEquals(1, gated.filterIsInstance<LabControl.Cookies>().size)
    }

    @Test
    fun `Open Chucker is present exactly when Chucker is enabled, independently of the profile gate`() {
        // lab_fragment.xml@53a07964:56 android:visibility="gone", flipped only at LabPageFragment.kt@53a07964:125-126.
        assertTrue(LabAction.OpenChucker in LabPanelBuilder.build(snapshot(chuckerEnabled = true)).actions())
        assertTrue(LabAction.OpenChucker !in LabPanelBuilder.build(snapshot(chuckerEnabled = false)).actions())
        assertTrue(
            LabAction.OpenChucker in
                LabPanelBuilder.build(snapshot(chuckerEnabled = true, profileIsZero = true)).actions(),
        )
    }

    @Test
    fun `both id-less checkboxes are in the roster with their current state`() {
        // lab_fragment.xml@53a07964:142 and :148 have no android:id and appear in no handler.
        val checks = LabPanelBuilder.build(snapshot()).filterIsInstance<LabControl.Check>().associateBy { it.toggle }
        assertEquals(LabToggle.entries.toSet(), checks.keys)
        assertTrue(checks.getValue(LabToggle.ARCHIVER_ENABLED).checked)
        assertTrue(!checks.getValue(LabToggle.API_AVAILABILITY_CHECK).checked)
        assertTrue(checks.getValue(LabToggle.CHUCKER).checked)
    }

    @Test
    fun `the profile picker carries every profile and the current selection`() {
        val picker = assertIs<LabControl.ProfilePicker>(
            LabPanelBuilder.build(snapshot()).single { it is LabControl.ProfilePicker },
        )
        assertEquals(listOf(1, 4), picker.profiles.map { it.id })
        assertEquals(4, picker.selectedId)
    }

    @Test
    fun `the danger and outlined styles land on the two controls that had them`() {
        val buttons = LabPanelBuilder.build(snapshot()).filterIsInstance<LabControl.Button>().associateBy { it.action }
        // lab_fragment.xml@53a07964:165 app:backgroundTint="@color/windowBackgroundRed"
        assertEquals(LabControlStyle.Danger, buttons.getValue(LabAction.DisableDevMode).style)
        // lab_fragment.xml@53a07964:169 style="…Button.OutlinedButton"
        assertEquals(LabControlStyle.Outlined, buttons.getValue(LabAction.RebuildConfig).style)
        assertEquals(LabControlStyle.Filled, buttons.getValue(LabAction.Rodo).style)
    }

    @Test
    fun `the five 24dp groupings survive as gapBefore`() {
        val controls = LabPanelBuilder.build(snapshot())
        val gapped = controls.filter { it.gapBefore }.map {
            when (it) {
                is LabControl.Button -> it.action.name
                is LabControl.Check -> it.toggle.name
                is LabControl.ProfilePicker -> "picker"
                is LabControl.Cookies -> "cookies"
            }
        }
        // marginTop=24dp on fullSync/clearCookies/unarchive/disableDebug, and marginBottom=24dp on
        // clearEndpointTimers, which reads as a gap before rodo.
        assertEquals(listOf("FullSync", "Rodo", "ClearCookies", "Unarchive", "DisableDevMode"), gapped)
    }

    @Test
    fun `the cookie readout carries the formatted groups`() {
        val cookies = listOf(
            okhttp3.Cookie.Builder().domain("a.pl").name("n").value("v").build(),
        )
        val readout = assertIs<LabControl.Cookies>(
            LabPanelBuilder.build(snapshot().copy(cookies = cookies)).single { it is LabControl.Cookies },
        )
        // Asserted against a literal, NOT against LabCookieFormatter.format(cookies): comparing the
        // implementation with itself is a test that cannot fail.
        assertEquals(listOf(CookieGroup("a.pl", listOf(CookieLine("n", "v", false)))), readout.groups)
    }
}
