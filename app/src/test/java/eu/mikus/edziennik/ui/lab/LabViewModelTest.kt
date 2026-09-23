/*
 * Copyright (c) Mikolaj Olszewski 2026-9-15.
 */

package eu.mikus.edziennik.ui.lab

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import eu.mikus.edziennik.config.BaseConfig
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

private class VmConfig(profileId: Int?) :
    BaseConfig(db = mockk(relaxed = true), profileId = profileId, entries = emptyList())

@OptIn(ExperimentalCoroutinesApi::class)
class LabViewModelTest {

    private val globalConfig = VmConfig(null).apply { values["theme"] = "1" }
    private val profileConfig = VmConfig(4).apply { values["theme"] = "2" }
    private val studentData = JsonObject().apply {
        addProperty("token", "abc")
        add("list", JsonArray().apply { add(1) })
    }

    private var snapshotCalls = 0
    private var panelCalls = 0
    private val writes = mutableListOf<Pair<Resolved, Any?>>()
    private val persisted = mutableListOf<LabRoot>()
    private val acted = mutableListOf<LabAction>()
    private val toggled = mutableListOf<Pair<LabToggle, Boolean>>()
    private var writeThrows = false
    /** Flipped mid-test so a refreshed panel really differs from the one the VM started with. */
    private var chuckerEnabled = true

    private val configPath = LabPath(LabRoot.CONFIG_PROFILE, listOf("theme"))
    private val tokenPath = LabPath(LabRoot.PROFILE_STUDENT_DATA, listOf("token"))
    private val arrayPath = LabPath(LabRoot.PROFILE_STUDENT_DATA, listOf("list", "0"))

    private fun json(): Map<LabRoot, JsonElement> = mapOf(
        LabRoot.PROFILE_STUDENT_DATA to studentData,
        LabRoot.CONFIG_GLOBAL to JsonObject().apply { addProperty("theme", "1") },
        LabRoot.CONFIG_PROFILE to JsonObject().apply { addProperty("theme", "2") },
    )

    private fun snapshot(chuckerEnabled: Boolean = this.chuckerEnabled) = LabSnapshot(
        profileIsZero = false, chuckerEnabled = chuckerEnabled,
        archiverEnabled = true, apiAvailabilityCheck = false,
        profiles = listOf(LabProfileEntry(4, "Ola", false)), currentProfileId = 4,
        cookies = emptyList(),
    )

    private fun vm() = LabViewModel(
        snapshot = { snapshotCalls++; json() },
        panelSnapshot = { panelCalls++; snapshot() },
        roots = {
            mapOf(
                LabRoot.PROFILE_STUDENT_DATA to studentData,
                LabRoot.CONFIG_GLOBAL to globalConfig,
                LabRoot.CONFIG_PROFILE to profileConfig,
            )
        },
        write = { resolved, value ->
            if (writeThrows) throw IllegalStateException("boom")
            writes += resolved to value
            // Apply it for real, so the re-projection has something to show.
            (resolved.target as? LabTarget.JsonLeaf)?.let { leaf ->
                when (value) {
                    is String -> leaf.parent.addProperty(leaf.name, value)
                    is Number -> leaf.parent.addProperty(leaf.name, value)
                    is Boolean -> leaf.parent.addProperty(leaf.name, value)
                }
            }
        },
        persist = { persisted += it },
        act = { acted += it },
        toggle = { t, v -> toggled += t to v },
    )

    private fun LabViewModel.hasOpenChucker() =
        panel.value.controls.any { it is LabControl.Button && it.action == LabAction.OpenChucker }

    private fun TestScope.collectEffects(vm: LabViewModel): MutableList<LabEffect> {
        val effects = mutableListOf<LabEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effects.collect { effects += it } }
        return effects
    }

    /* ---------- initial state ---------- */

    @Test
    fun `the panel and the tree are built from their own snapshots`() {
        val vm = vm()
        assertEquals(LabPanelBuilder.build(snapshot()).size, vm.panel.value.controls.size)
        assertEquals(listOf("Profile / studentData", "Config", "Config (profile)"), vm.tree.value.rows.map { it.name })
    }

    @Test
    fun `toggleNode expands and collapses without rebuilding the tree`() {
        val vm = vm()
        val before = snapshotCalls
        vm.toggleNode(LabPath(LabRoot.PROFILE_STUDENT_DATA))
        assertEquals(listOf("Profile / studentData", "token", "list", "Config", "Config (profile)"),
            vm.tree.value.rows.map { it.name })
        vm.toggleNode(LabPath(LabRoot.PROFILE_STUDENT_DATA))
        assertEquals(3, vm.tree.value.rows.size)
        assertEquals(before, snapshotCalls)   // the tree rebuilds only on edit
    }

    /* ---------- the edit path ---------- */

    @Test
    fun `clicking a leaf offers the formatted prefill, not the quoted display text`() = runTest {
        val vm = vm()
        val effects = collectEffects(vm)
        vm.onLeafClick(tokenPath)
        val opened = assertIs<LabEffect.OpenEditor>(effects.single())
        assertEquals(tokenPath, opened.path)
        assertEquals("abc", opened.prefill)                          // not "\"abc\""
        assertEquals("Profile / studentData / token", opened.title)
    }

    @Test
    fun `clicking an array element reports it instead of opening an editor`() = runTest {
        val vm = vm()
        val effects = collectEffects(vm)
        vm.onLeafClick(arrayPath)
        assertIs<LabEffect.ParseFailed>(effects.single())
    }

    @Test
    fun `a parse failure writes nothing and tells the operator`() = runTest {
        val vm = vm()
        val effects = collectEffects(vm)
        // "token" is a JSON string, so pick a number leaf to make parsing fail.
        studentData.addProperty("count", 7)
        vm.writeValue(LabPath(LabRoot.PROFILE_STUDENT_DATA, listOf("count")), "not a number")
        assertTrue(writes.isEmpty())
        assertTrue(persisted.isEmpty())
        assertIs<LabEffect.ParseFailed>(effects.single())
    }

    @Test
    fun `a successful write reaches the sink, persists its own root, and rebuilds the tree`() {
        val vm = vm()
        vm.toggleNode(LabPath(LabRoot.PROFILE_STUDENT_DATA))
        assertEquals("\"abc\"", assertIs<LabNode.Leaf>(vm.tree.value.rows.single { it.name == "token" }).displayText)
        val before = snapshotCalls
        vm.writeValue(tokenPath, "xyz")
        assertEquals(1, writes.size)
        assertEquals("xyz", writes.single().second)
        assertEquals(listOf(LabRoot.PROFILE_STUDENT_DATA), persisted)
        assertEquals(before + 1, snapshotCalls)
        // The rebuilt nodes have to reach _tree: a writeValue that rebuilds but never re-projects
        // leaves the row showing the pre-edit value, and every other assertion here still passes.
        assertEquals("\"xyz\"", assertIs<LabNode.Leaf>(vm.tree.value.rows.single { it.name == "token" }).displayText)
    }

    @Test
    fun `a write under Config (profile) carries the profile config, not the global one`() {
        val vm = vm()
        vm.writeValue(configPath, "9")
        val target = assertIs<LabTarget.ConfigEntry>(writes.single().first.target)
        assertSame(profileConfig, target.owner)
        assertEquals(LabRoot.CONFIG_PROFILE, writes.single().first.root)
        // BaseConfig.set persists on its own, so nothing extra is asked of the persist sink.
        assertEquals(listOf(LabRoot.CONFIG_PROFILE), persisted)
    }

    @Test
    fun `a throwing write sink becomes a reported failure, not a crash`() = runTest {
        // The original defined its write inside a try but ran it from the dialog's click handler,
        // outside that try's dynamic extent (DialogExtensions.kt:45-48), so this threw at the user.
        val vm = vm()
        val effects = collectEffects(vm)
        writeThrows = true
        val before = snapshotCalls
        vm.writeValue(tokenPath, "xyz")
        assertIs<LabEffect.ParseFailed>(effects.single())
        assertEquals(before, snapshotCalls)   // no rebuild after a failed write
    }

    /* ---------- toggles ---------- */

    @Test
    fun `a toggle writes through the seam and refreshes the panel`() {
        val vm = vm()
        val before = panelCalls
        assertTrue(vm.hasOpenChucker())
        // The fake snapshot changes under the VM, so counting panelSnapshot() calls is not enough:
        // a refreshPanel() that re-snapshots but never publishes leaves `Open Chucker` on the panel.
        chuckerEnabled = false
        vm.onToggle(LabToggle.ARCHIVER_ENABLED, false)
        assertEquals(listOf(LabToggle.ARCHIVER_ENABLED to false), toggled)
        assertEquals(before + 1, panelCalls)
        assertFalse(vm.hasOpenChucker())
    }

    @Test
    fun `only the Chucker toggle asks for a restart, and only once per tap`() = runTest {
        val vm = vm()
        val effects = collectEffects(vm)
        vm.onToggle(LabToggle.ARCHIVER_ENABLED, true)
        vm.onToggle(LabToggle.API_AVAILABILITY_CHECK, true)
        assertTrue(effects.isEmpty())
        vm.onToggle(LabToggle.CHUCKER, true)
        // listOf<LabEffect>(...) explicitly: `RestartRequired` is a `data object`, so a bare
        // listOf() infers List<LabEffect.RestartRequired> and cannot unify with MutableList<LabEffect>.
        assertEquals(listOf<LabEffect>(LabEffect.RestartRequired), effects)
    }

    /* ---------- actions ---------- */

    @Test
    fun `the three host-owned actions become effects and never reach the act sink`() = runTest {
        val vm = vm()
        val effects = collectEffects(vm)
        vm.onAction(LabAction.ClearProfile)
        vm.onAction(LabAction.OpenChucker)
        vm.onAction(LabAction.DisableDevMode)
        assertEquals(
            listOf(
                // Design D7's whole content - the REAL profile name, not the literal "FAKE" - is on
                // the effect, so it is asserted here rather than re-read off App by the host.
                LabEffect.ConfirmClearProfile(profileId = 4, profileName = "Ola"),
                LabEffect.OpenChucker,
                LabEffect.ConfirmDisableDevMode,
            ),
            effects,
        )
        assertTrue(acted.isEmpty())
    }

    @Test
    fun `every other action goes straight to the act sink and refreshes the panel`() {
        val vm = vm()
        val before = panelCalls
        vm.onAction(LabAction.Rodo)
        vm.onAction(LabAction.RebuildConfig)
        assertEquals(listOf(LabAction.Rodo, LabAction.RebuildConfig), acted)
        assertEquals(before + 2, panelCalls)
    }

    @Test
    fun `Disable Dev Mode only writes once confirmed, then asks for the restart`() = runTest {
        // App.kt:219 reads `devMode = config.devMode ?: debugMode`, so a non-null false bypasses the
        // BuildConfig.DEBUG fallback forever. That is why this one is behind its own confirm (D6).
        val vm = vm()
        val effects = collectEffects(vm)
        vm.confirmDisableDevMode()
        assertEquals(listOf(LabAction.DisableDevMode), acted)
        // listOf<LabEffect>(...) explicitly: `RestartRequired` is a `data object`, so a bare
        // listOf() infers List<LabEffect.RestartRequired> and cannot unify with MutableList<LabEffect>.
        assertEquals(listOf<LabEffect>(LabEffect.RestartRequired), effects)
    }
}
