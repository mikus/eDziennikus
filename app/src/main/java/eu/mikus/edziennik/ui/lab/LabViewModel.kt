/*
 * Copyright (c) Mikolaj Olszewski 2026-9-15.
 */

package eu.mikus.edziennik.ui.lab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.sqlite.db.SimpleSQLiteQuery
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.mikus.edziennik.App
import eu.mikus.edziennik.config.Config
import eu.mikus.edziennik.data.db.entity.EventType.Companion.SOURCE_DEFAULT
import eu.mikus.edziennik.data.db.entity.LoginStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Lab's decisions, and nothing else. Every sink is injected, as `SettingsViewModel`'s are, so the one
 * path in this phase that can corrupt a live profile is buildable under JUnit.
 *
 * Two independent flows: [panel] is re-snapshotted on demand (the host polls it, as the old 300 ms
 * timer did), while [tree] rebuilds only after a successful edit.
 */
class LabViewModel(
    private val snapshot: () -> Map<LabRoot, JsonElement>,
    private val panelSnapshot: () -> LabSnapshot,
    private val roots: () -> Map<LabRoot, Any?>,
    private val write: (Resolved, Any?) -> Unit,
    private val persist: (LabRoot) -> Unit,
    private val act: (LabAction) -> Unit,
    private val toggle: (LabToggle, Boolean) -> Unit,
) : ViewModel() {

    private var nodes: List<LabNode> = LabTreeBuilder.build(snapshot())
    private var expanded: Set<LabPath> = emptySet()

    /**
     * The snapshot the current panel was built from. Kept so an effect can carry its own data rather
     * than sending the host back to `App` for it - see [LabEffect.ConfirmClearProfile]. Declared
     * before [_panel] because property initialisers run in declaration order.
     */
    private var lastSnapshot: LabSnapshot = panelSnapshot()

    private val _panel = MutableStateFlow(LabPanelUiState(LabPanelBuilder.build(lastSnapshot)))
    val panel: StateFlow<LabPanelUiState> = _panel.asStateFlow()

    private val _tree = MutableStateFlow(LabTreeUiState(LabTreeBuilder.visible(nodes, expanded)))
    val tree: StateFlow<LabTreeUiState> = _tree.asStateFlow()

    private val _effects = MutableSharedFlow<LabEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<LabEffect> = _effects.asSharedFlow()

    /** Re-snapshots the panel. The host calls this on a timer and after a host-owned dialog closes. */
    fun refreshPanel() {
        lastSnapshot = panelSnapshot()
        _panel.value = LabPanelUiState(LabPanelBuilder.build(lastSnapshot))
    }

    fun toggleNode(path: LabPath) {
        expanded = if (path in expanded) expanded - path else expanded + path
        projectTree()
    }

    /** The host owns the edit dialog, so the prefill travels to it as an effect. */
    fun onLeafClick(path: LabPath) {
        val resolved = LabPathResolver.resolve(roots(), path).getOrElse { return failed(it) }
        val target = resolved.target
        if (target is LabTarget.Unsupported) return failed(LabEditError(target.reason))
        _effects.tryEmit(LabEffect.OpenEditor(path, path.title, LabValueCodec.format(target)))
    }

    /** Parse failure, resolve failure or a throwing sink all mean: zero writes, operator told. */
    fun writeValue(path: LabPath, input: String) {
        val resolved = LabPathResolver.resolve(roots(), path).getOrElse { return failed(it) }
        val value = LabValueCodec.parse(resolved.target, input).getOrElse { return failed(it) }
        runCatching {
            write(resolved, value)
            persist(resolved.root)
        }.getOrElse { return failed(it) }
        nodes = LabTreeBuilder.build(snapshot())
        projectTree()
    }

    fun onToggle(toggle: LabToggle, value: Boolean) {
        this.toggle(toggle, value)
        refreshPanel()
        // Exhaustive rather than `if (toggle == CHUCKER)`, mirroring SettingsViewModel.effectFor
        // (`SettingsViewModel.kt:53-61`): the null arm exists so that a fourth toggle whose value is
        // read at process start has to answer the restart question instead of silently skipping it.
        // A one-shot on a SharedFlow, never a function of state, so no recomposition can re-kill.
        val effect = when (toggle) {
            LabToggle.CHUCKER -> LabEffect.RestartRequired
            LabToggle.ARCHIVER_ENABLED, LabToggle.API_AVAILABILITY_CHECK -> null
        }
        effect?.let(_effects::tryEmit)
    }

    fun onAction(action: LabAction) {
        when (action) {
            // The host owns these three: a dialog, an Intent, and a confirm-then-kill.
            LabAction.ClearProfile -> _effects.tryEmit(
                LabEffect.ConfirmClearProfile(
                    profileId = lastSnapshot.currentProfileId,
                    // Design D7: the real name, taken from the snapshot the panel was built from.
                    // An empty fallback would reproduce the very defect D7 exists to fix - a dialog
                    // saying "you are about to delete profile " - so fall back to the id instead.
                    profileName = lastSnapshot.profiles
                        .firstOrNull { it.id == lastSnapshot.currentProfileId }?.name
                        ?: "#${lastSnapshot.currentProfileId}",
                ),
            )
            LabAction.OpenChucker -> _effects.tryEmit(LabEffect.OpenChucker)
            LabAction.DisableDevMode -> _effects.tryEmit(LabEffect.ConfirmDisableDevMode)

            LabAction.Last10Unseen, LabAction.FullSync, LabAction.ClearEndpointTimers,
            LabAction.Rodo, LabAction.RemoveHomework, LabAction.ResetEventTypes,
            LabAction.Unarchive, LabAction.ResetCert, LabAction.RebuildConfig,
            LabAction.ClearCookies,
            -> {
                act(action)
                refreshPanel()
            }
        }
    }

    /** After the host's `ProfileRemoveDialog` has done the clearing itself. */
    fun onProfileCleared() {
        nodes = LabTreeBuilder.build(snapshot())
        projectTree()
        refreshPanel()
    }

    /** The confirmed half of [LabAction.DisableDevMode]. */
    fun confirmDisableDevMode() {
        act(LabAction.DisableDevMode)
        _effects.tryEmit(LabEffect.RestartRequired)
    }

    private fun projectTree() {
        _tree.value = LabTreeUiState(LabTreeBuilder.visible(nodes, expanded))
    }

    private fun failed(t: Throwable) {
        _effects.tryEmit(LabEffect.ParseFailed(t.message ?: t.toString()))
    }

    /** Host-constructed; the only `App.*` reader for the Android-free units. */
    class Factory(private val app: App) : ViewModelProvider.Factory {
        private val loginStore by lazy { app.db.loginStoreDao().getByIdNow(app.profile.loginStoreId) }

        /**
         * Read once, as `LabPageFragment.kt@53a07964:161` read it. The list cannot change while Lab is on
         * screen - picking a profile calls `MainActivity.navigate`, which rebuilds the fragment - and
         * keeping it out of `panelSnapshot` stops the 300 ms cookie poll from also re-running a
         * blocking `SELECT * FROM profiles` on the main thread 3.3 times a second.
         */
        private val profiles by lazy {
            app.db.profileDao().allNow.map { LabProfileEntry(it.id, it.name, it.archived) }
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LabViewModel(
            snapshot = { readLabJson(app, loginStore) },
            panelSnapshot = { readLabSnapshot(app, profiles) },
            roots = { labRoots(app, loginStore) },
            write = { resolved, value -> writeLabValue(resolved, value) },
            persist = { root -> persistLabRoot(app, root, loginStore) },
            act = { action -> performLabAction(app, action) },
            toggle = { toggle, value -> writeLabToggle(app, toggle, value) },
        ) as T
    }
}

// --- Android edge: the only App/Android readers ---

/**
 * `LabProfileFragment.showJson()` (`:175-186`), except that it returns a typed map instead of one
 * `JsonObject` keyed by display strings — so a root's identity never has to be recovered by matching
 * `LabRoot.label` against a JSON key, and renaming a label cannot silently drop a root.
 * `mapOf` keeps insertion order, which is the display order.
 */
internal fun readLabJson(app: App, loginStore: LoginStore?): Map<LabRoot, JsonElement> = mapOf(
    LabRoot.PROFILE to app.gson.toJsonTree(app.profile),
    LabRoot.PROFILE_STUDENT_DATA to app.profile.studentData,
    LabRoot.LOGIN_STORE to app.gson.toJsonTree(loginStore),
    LabRoot.LOGIN_STORE_DATA to (loginStore?.data ?: JsonObject()),
    LabRoot.CONFIG_GLOBAL to JsonParser.parseString(app.gson.toJson(app.config.values.toSortedMap())),
    LabRoot.CONFIG_PROFILE to JsonParser.parseString(app.gson.toJson(app.profile.config.values.toSortedMap())),
)

/**
 * The write targets behind those six roots. The two config roots carry the owning [BaseConfig], not
 * its `values` map - that is what makes a "Config (profile)" edit reach the profile config instead of
 * the global one (design D4).
 */
internal fun labRoots(app: App, loginStore: LoginStore?): Map<LabRoot, Any?> = mapOf(
    LabRoot.PROFILE to app.profile,
    LabRoot.PROFILE_STUDENT_DATA to app.profile.studentData,
    LabRoot.LOGIN_STORE to loginStore,
    LabRoot.LOGIN_STORE_DATA to (loginStore?.data ?: JsonObject()),
    LabRoot.CONFIG_GLOBAL to app.config,
    LabRoot.CONFIG_PROFILE to app.profile.config,
)

/** [profiles] is passed in, not read here: the Factory reads it once. Everything else is a field read. */
internal fun readLabSnapshot(app: App, profiles: List<LabProfileEntry>) = LabSnapshot(
    profileIsZero = app.profile.id == 0,
    chuckerEnabled = App.enableChucker,
    archiverEnabled = app.config.archiverEnabled,
    apiAvailabilityCheck = app.config.apiAvailabilityCheck,
    profiles = profiles,
    currentProfileId = app.profileId,
    cookies = app.cookieJar.getAllDomains(),
)

internal fun writeLabValue(resolved: Resolved, newValue: Any?) {
    when (val target = resolved.target) {
        is LabTarget.JsonLeaf -> when (newValue) {
            is String -> target.parent.addProperty(target.name, newValue)
            is Number -> target.parent.addProperty(target.name, newValue)
            is Boolean -> target.parent.addProperty(target.name, newValue)
            else -> Unit
        }
        // owner.set, not app.config.set: BaseConfig.set (`:40-45`) writes
        // ConfigEntry(profileId ?: -1, ...), so the row lands in the scope the path came from.
        is LabTarget.ConfigEntry -> target.owner.set(target.key, newValue as? String)
        is LabTarget.Field -> {
            val field = target.owner::class.java.getDeclaredField(target.name)
            field.isAccessible = true
            field.set(target.owner, newValue)
        }
        is LabTarget.Unsupported -> Unit
    }
}

/** `LabProfileFragment.kt@53a07964:139-142`. The config roots need nothing: `BaseConfig.set` already wrote. */
internal fun persistLabRoot(app: App, root: LabRoot, loginStore: LoginStore?) {
    when (root) {
        LabRoot.PROFILE, LabRoot.PROFILE_STUDENT_DATA -> app.profileSave()
        LabRoot.LOGIN_STORE, LabRoot.LOGIN_STORE_DATA -> loginStore?.let { app.db.loginStoreDao().add(it) }
        LabRoot.CONFIG_GLOBAL, LabRoot.CONFIG_PROFILE -> Unit
    }
}

internal fun writeLabToggle(app: App, toggle: LabToggle, value: Boolean) {
    when (toggle) {
        LabToggle.CHUCKER -> {
            app.config.enableChucker = value
            App.enableChucker = value
        }
        LabToggle.ARCHIVER_ENABLED -> app.config.archiverEnabled = value
        LabToggle.API_AVAILABILITY_CHECK -> app.config.apiAvailabilityCheck = value
    }
}

/** Every handler from `LabPageFragment.onPageCreated`, verbatim. */
internal fun performLabAction(app: App, action: LabAction) {
    when (action) {
        LabAction.Last10Unseen -> app.launch(Dispatchers.Default) {
            val events = app.db.eventDao().getAllNow(App.profileId)
            events.sortedBy { it.date }.filter { it.isHomework }.takeLast(10).forEach {
                app.db.metadataDao().setSeen(App.profileId, it, false)
            }
        }
        LabAction.FullSync -> {
            app.profile.empty = true
            app.profileSave()
        }
        LabAction.ClearEndpointTimers -> app.db.endpointTimerDao().clear(app.profileId)
        LabAction.Rodo -> app.db.teacherDao().query(
            SimpleSQLiteQuery("UPDATE teachers SET teacherSurname = \"\" WHERE profileId = ${App.profileId}"),
        )
        LabAction.RemoveHomework -> app.db.eventDao()
            .getRawNow("UPDATE events SET homeworkBody = NULL WHERE profileId = ${App.profileId}")
        LabAction.ResetEventTypes -> {
            app.db.eventTypeDao().clearBySource(App.profileId, SOURCE_DEFAULT)
            app.db.eventTypeDao().getAllWithDefaults(App.profile)
        }
        LabAction.Unarchive -> {
            app.profile.archived = false
            app.profile.archiveId = null
            app.profileSave()
        }
        LabAction.ResetCert -> app.config.apiInvalidCert = null
        LabAction.RebuildConfig -> App.config = Config(App.db)
        LabAction.ClearCookies -> app.cookieJar.clearAllDomains()
        // App.kt:219 reads `devMode = config.devMode ?: debugMode`, so a non-null false bypasses the
        // BuildConfig.DEBUG fallback forever, and the only re-enable (checkDevModePassword, App.kt:396)
        // is gated on config.devModePassword. Its single writer is the v3 prefs migration
        // (AppConfigMigrationV3.kt:38), which ConfigMigration.kt:18 gates on a legacy prefs key it
        // deletes as it runs, so no in-app path re-enables. Recovery means editing stored state from
        // outside the app: the config row keyed "debugMode" (Config.kt:38) at profileId = -1 - not
        // "devMode" - or the legacy prefs pair that replays the migration.
        LabAction.DisableDevMode -> {
            app.config.devMode = false
            App.devMode = false
        }
        // Routed to a LabEffect by the ViewModel: the host owns the dialog and the Intent.
        LabAction.ClearProfile, LabAction.OpenChucker -> Unit
    }
}
