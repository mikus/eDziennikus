/*
 * Copyright (c) Mikolaj Olszewski 2026-9-15.
 */

package eu.mikus.edziennik.ui.lab

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import eu.mikus.edziennik.config.BaseConfig
import okhttp3.Cookie

/** The six roots `LabProfileFragment.showJson()` built, named instead of string-matched. */
enum class LabRoot(val label: String) {
    PROFILE("Profile"),
    PROFILE_STUDENT_DATA("Profile / studentData"),
    LOGIN_STORE("LoginStore"),
    LOGIN_STORE_DATA("LoginStore / data"),
    CONFIG_GLOBAL("Config"),
    CONFIG_PROFILE("Config (profile)"),
}

/**
 * A node's address: a root plus the keys below it.
 *
 * Structured, not the old `"Config:key"` colon string. The adapter minted paths by concatenating with
 * `":"` (`LabJsonAdapter.kt:49-51, :63-64`) and the resolver re-split them
 * (`LabProfileFragment.kt:66`), so a key containing a colon could not round-trip. The repo already
 * refused that once - `AttendanceUiState.kt:20` keeps `NodeKey` as the identity and `stableId` only
 * as a LazyColumn key, which is exactly what [stableId] is for here.
 */
data class LabPath(val root: LabRoot, val segments: List<String> = emptyList()) {
    /**
     * Bundle-storable, for LazyColumn item keys. Each segment is **length-prefixed**, not merely
     * counted: a JSON key may legally contain the separator, so `["a/b", "c"]` and `["a", "b/c"]`
     * would otherwise both render `.../2/a/b/c` — and `LazyColumn` answers a duplicate key with
     * `IllegalArgumentException: Key was already used`. Counting segments only separates lists of
     * *different* lengths, which is the same half-measure the old `"Config:key"` string made.
     */
    val stableId: String get() = root.name + segments.joinToString("") { "/${it.length}:$it" }
    fun child(segment: String) = copy(segments = segments + segment)
    /** The dialog title, as `item.key` was (`LabProfileFragment.kt:102`). */
    val title: String get() = (listOf(root.label) + segments).joinToString(" / ")
}

/**
 * Which of the three container renderings a row gets. Selected by **depth**, not by kind:
 * `LabJsonAdapter.kt:98-100` reads `if (item.level == 1)`, while an array always got the full
 * rendering (`ITEM_TYPE_ARRAY`). Decided here, in the builder, so `LabTreeBuilderTest` can assert it
 * instead of the composable branching on depth.
 */
enum class LabContainerStyle { Full, Compact }

sealed interface LabNode {
    val path: LabPath
    val name: String
    /** 1 for a root row, 2 for its children, ... Mirrors `LabJsonAdapter`'s `level`. */
    val depth: Int
    val typeLabel: String?

    data class Container(
        override val path: LabPath,
        override val name: String,
        override val depth: Int,
        override val typeLabel: String,
        val style: LabContainerStyle,
        /** Null for [LabContainerStyle.Compact]: that row renders neither, so neither is computed. */
        val preview: String?,
        val summary: String?,
        val expanded: Boolean = false,
    ) : LabNode

    data class Leaf(
        override val path: LabPath,
        override val name: String,
        override val depth: Int,
        override val typeLabel: String?,
        /** `JsonElement.toString()` - quoted, as `JsonElementViewHolder.kt:51` rendered it. */
        val displayText: String,
    ) : LabNode
}

/** What a [LabPath] points at, and therefore how a write reaches it. */
sealed interface LabTarget {
    data class JsonLeaf(val parent: JsonObject, val name: String, val old: JsonPrimitive) : LabTarget

    /**
     * The owning config, **not** its `values` map. `BaseConfig.kt:27` declares
     * `val values = hashMapOf<String, String?>()`, inherited identically by `Config` and
     * `ProfileConfig`, so both roots reach a `when (parent)` as the same runtime type and dispatching
     * on it cannot tell them apart. Carrying the owner makes design D4 true by construction.
     */
    data class ConfigEntry(val owner: BaseConfig, val key: String) : LabTarget

    data class Field(val owner: Any, val name: String, val old: Any?) : LabTarget

    /**
     * A reachable node that cannot be edited. Kept as an explicit arm, not deleted: array elements
     * arrive here because `LabJsonAdapter.kt:64` indexes arrays into clickable rows, and without this
     * arm they fall to the reflective branch, which runs `getDeclaredField("0")` on a `JsonArray`.
     */
    data class Unsupported(val reason: String) : LabTarget
}

data class Resolved(val root: LabRoot, val target: LabTarget)

/** A typed, message-carrying failure. Surfaced as [LabEffect.ParseFailed], never thrown at the UI. */
class LabEditError(message: String) : Exception(message)

data class LabProfileEntry(val id: Int, val name: String, val archived: Boolean)

data class CookieGroup(val domain: String, val lines: List<CookieLine>)
data class CookieLine(val name: String, val value: String, val persistent: Boolean)

enum class LabAction {
    Last10Unseen, FullSync, ClearProfile, ClearEndpointTimers, Rodo, RemoveHomework,
    ResetEventTypes, Unarchive, ResetCert, RebuildConfig, ClearCookies, OpenChucker, DisableDevMode,
}

enum class LabToggle { CHUCKER, ARCHIVER_ENABLED, API_AVAILABILITY_CHECK }

enum class LabControlStyle { Filled, Outlined, Danger }

/** `gapBefore` reproduces the 24 dp `layout_marginTop` groupings of `lab_fragment.xml`. */
sealed interface LabControl {
    val gapBefore: Boolean
    data class Button(
        override val gapBefore: Boolean,
        val action: LabAction,
        val label: String,
        val style: LabControlStyle = LabControlStyle.Filled,
    ) : LabControl
    data class Check(
        override val gapBefore: Boolean,
        val toggle: LabToggle,
        val label: String,
        val checked: Boolean,
    ) : LabControl
    data class ProfilePicker(
        override val gapBefore: Boolean,
        val profiles: List<LabProfileEntry>,
        val selectedId: Int,
    ) : LabControl
    data class Cookies(override val gapBefore: Boolean, val groups: List<CookieGroup>) : LabControl
}

/** Immutable input to [LabPanelBuilder]. Produced by the Factory-injected edge lambda. */
data class LabSnapshot(
    val profileIsZero: Boolean,
    val chuckerEnabled: Boolean,
    val archiverEnabled: Boolean,
    val apiAvailabilityCheck: Boolean,
    val profiles: List<LabProfileEntry>,
    val currentProfileId: Int,
    val cookies: List<Cookie>,
)

data class LabPanelUiState(val controls: List<LabControl>)
data class LabTreeUiState(val rows: List<LabNode>)

/** One-shot effects the VM emits and the host performs. Mirrors `SettingsEffect`. */
sealed interface LabEffect {
    data class ParseFailed(val message: String) : LabEffect
    data class OpenEditor(val path: LabPath, val title: String, val prefill: String) : LabEffect
    data object RestartRequired : LabEffect
    /**
     * Carries the two values `ProfileRemoveDialog` needs. Design D7's whole content is "pass the
     * real profile name" - the old code passed the literal "FAKE" - so the name belongs where a test
     * can see it, not re-read off `App` in the host.
     */
    data class ConfirmClearProfile(val profileId: Int, val profileName: String) : LabEffect
    data object ConfirmDisableDevMode : LabEffect
    data object OpenChucker : LabEffect
}
