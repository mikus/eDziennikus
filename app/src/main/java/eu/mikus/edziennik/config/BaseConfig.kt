/*
 * Copyright (c) Kuba Szczodrzyński 2019-11-27.
 */

package eu.mikus.edziennik.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import eu.mikus.edziennik.config.db.ConfigEntry
import eu.mikus.edziennik.data.db.AppDb
import eu.mikus.edziennik.ext.takePositive
import eu.mikus.edziennik.utils.Utils
import kotlin.coroutines.CoroutineContext

abstract class BaseConfig(
    @Transient
    val db: AppDb,
    val profileId: Int? = null,
    protected var entries: List<ConfigEntry>? = null,
) : CoroutineScope {

    companion object {
        private const val TAG = "BaseConfig"

        /** Key prefix of the rows [recordParseFailure] writes; the rest of the key is the failing key. */
        const val PARSE_FAILURE_PREFIX = "parseFailure."

        /** How much of an unparseable stored value a marker keeps. */
        const val PARSE_FAILURE_VALUE_LIMIT = 120
    }

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Default

    /**
     * Set once by [retireInstance] and never cleared: a retired instance stays retired.
     *
     * `@Volatile` because it is written on the profile-removal path - `ProfileRemoveDialog` runs
     * `removeProfileData` inside `withContext(Dispatchers.Default)` - and read on whatever thread a
     * screen still holding this instance happens to write from.
     */
    @Volatile
    private var retired = false

    val values = hashMapOf<String, String?>()

    private val _changes = MutableSharedFlow<String>(extraBufferCapacity = 64)

    /**
     * Emits the key of every config value written through [set].
     *
     * Per instance, and there is more than one — see [configFlow], which is how callers should
     * consume this.
     *
     * `extraBufferCapacity` keeps [tryEmit] non-suspending. Dropping an event under extreme burst is
     * acceptable because collectors re-read current state rather than accumulating deltas.
     */
    val changes: SharedFlow<String> = _changes.asSharedFlow()

    init {
        if (entries == null)
            entries = db.configDao().getAllNow()
        values.clear()
        for ((profileId, key, value) in entries!!) {
            if (profileId.takePositive() != this.profileId)
                continue
            values[key] = value
        }
    }

    /**
     * Retires this instance for good: no further write is accepted, and anything already queued on
     * its scope is cancelled.
     *
     * [ProfileConfig.retire] is the only caller, and it is declared **there** rather than here so
     * that `retire()` on the global [Config] does not compile. Retiring the global instance would
     * stop every app-wide setting persisting for the rest of the process, and nothing would say so.
     *
     * Cancelling the `Job` alone would not have been enough, and would have been a defect of its
     * own: `launch` on a cancelled scope does not throw, so [set] would still write [values] and
     * still emit on [changes] while only the row vanished - the user would watch the edit take and
     * lose it on the next launch. The flag is what keeps the three effects a unit.
     *
     * The one thing outside that unit is [ConfigDelegate]'s own cache: its `setValue` assigns the
     * cached value before it calls [set], so on a retired instance a write leaves [values] and the
     * table untouched yet the property still reads back the new value. That is harmless - the
     * profile is gone and the instance is about to be discarded - but it is not covered by the
     * guard.
     */
    protected fun retireInstance() {
        retired = true
        job.cancel()
    }

    /**
     * Writes [key] to [values] and [changes] now, and to the table asynchronously.
     *
     * A retired instance refuses the write outright rather than half-applying it; see
     * [retireInstance]. The guard is above all three effects deliberately - below the first of them
     * it reintroduces exactly the silent half-write it exists to prevent.
     */
    fun set(key: String, value: String?) {
        if (retired) {
            Utils.d(TAG, "refusing a write to retired profile $profileId: $key")
            return
        }
        values[key] = value
        _changes.tryEmit(key)
        launch(Dispatchers.IO) {
            db.configDao().add(ConfigEntry(profileId ?: -1, key, value))
        }
    }

    /**
     * Records that the stored value of [key] would not parse, as a row of its own under
     * [PARSE_FAILURE_PREFIX] + [key], holding the first [PARSE_FAILURE_VALUE_LIMIT] characters of
     * [stored].
     *
     * A separate key rather than the property's own row: the fallback to the declared default is
     * read-through, so the unparseable row stays exactly as it is and is never overwritten by the
     * default.
     *
     * One row per key rather than an accumulated list: there is no read-modify-write, and the marker
     * is never itself read through a [ConfigDelegate], so recording one cannot recurse into another.
     *
     * A marker that already holds this value is not written again. A property read on every render
     * would otherwise emit on [changes] each time, and a [configFlow] collector re-reading on that
     * emission would read the property again - a loop. The `has` check is required: the value compare
     * alone would rest on an absent key happening to read back as something other than [stored].
     *
     * A retired instance drops the marker, as it does any write; see [retireInstance].
     */
    fun recordParseFailure(key: String, stored: String) {
        Utils.d(TAG, "stored value of $key does not parse")
        val markerKey = PARSE_FAILURE_PREFIX + key
        val marker = stored.take(PARSE_FAILURE_VALUE_LIMIT)
        if (has(markerKey) && values[markerKey] == marker)
            return
        set(markerKey, marker)
    }

    fun has(key: String) = values.containsKey(key)
}
