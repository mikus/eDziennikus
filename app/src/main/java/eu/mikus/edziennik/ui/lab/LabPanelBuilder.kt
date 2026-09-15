/*
 * Copyright (c) Mikolaj Olszewski 2026-9-15.
 */

package eu.mikus.edziennik.ui.lab

/**
 * Pure, Android-free assembly of Lab's control panel, in `lab_fragment.xml` order.
 *
 * The roster used to live in two files that disagreed: nine controls hidden by
 * `LabPageFragment.kt@53a07964:61-71`, `openChucker` hidden by its own `android:visibility="gone"`
 * (`lab_fragment.xml@53a07964:56`, flipped at `LabPageFragment.kt@53a07964:125-126`), and two checkboxes with no
 * `android:id` that exist only as two-way data binding (`lab_fragment.xml@53a07964:139-149`) and appear in no
 * handler at all. Building the roster here makes dropping one a deliberate deletion.
 *
 * Labels stay hardcoded English, as the XML's `tools:ignore="HardcodedText"` already had them: LAB is
 * `devModeOnly` and no ordinary user can reach it. See the plan's deviation D-c.
 */
object LabPanelBuilder {

    fun build(s: LabSnapshot): List<LabControl> = buildList {
        add(LabControl.Check(false, LabToggle.CHUCKER, "Chucker", s.chuckerEnabled))
        if (s.chuckerEnabled) add(button(false, LabAction.OpenChucker, "Open Chucker"))

        if (!s.profileIsZero) {
            add(button(false, LabAction.Last10Unseen, "Set last 10 as unseen"))
            add(button(true, LabAction.FullSync, "Full sync and empty profile"))
            add(button(false, LabAction.ClearProfile, "Clear all profile data"))
            add(button(false, LabAction.ClearEndpointTimers, "Clear endpoint timers (force full sync)"))
            add(button(true, LabAction.Rodo, "Duh rodo button"))
            add(button(false, LabAction.RemoveHomework, "Remove all homework body (null)"))
            add(button(false, LabAction.ResetEventTypes, "Reset event types"))
        }

        add(button(true, LabAction.ClearCookies, "Clear all cookies"))
        add(LabControl.Cookies(false, LabCookieFormatter.format(s.cookies)))

        if (!s.profileIsZero) {
            add(button(true, LabAction.Unarchive, "Unarchive this profile"))
            add(LabControl.ProfilePicker(false, s.profiles, s.currentProfileId))
        }

        add(LabControl.Check(false, LabToggle.ARCHIVER_ENABLED, "Archiver enabled", s.archiverEnabled))
        add(LabControl.Check(false, LabToggle.API_AVAILABILITY_CHECK, "Availability check enabled", s.apiAvailabilityCheck))

        add(button(false, LabAction.ResetCert, "Reset API signature"))
        add(button(true, LabAction.DisableDevMode, "Disable Dev Mode", LabControlStyle.Danger))
        add(button(false, LabAction.RebuildConfig, "Rebuild App.config", LabControlStyle.Outlined))
    }

    private fun button(
        gapBefore: Boolean,
        action: LabAction,
        label: String,
        style: LabControlStyle = LabControlStyle.Filled,
    ) = LabControl.Button(gapBefore, action, label, style)
}
