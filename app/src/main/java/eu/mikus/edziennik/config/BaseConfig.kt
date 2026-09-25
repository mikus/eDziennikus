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
import kotlin.coroutines.CoroutineContext

abstract class BaseConfig(
    @Transient
    val db: AppDb,
    val profileId: Int? = null,
    protected var entries: List<ConfigEntry>? = null,
) : CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Default

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

    fun set(key: String, value: String?) {
        values[key] = value
        _changes.tryEmit(key)
        launch(Dispatchers.IO) {
            db.configDao().add(ConfigEntry(profileId ?: -1, key, value))
        }
    }

    fun has(key: String) = values.containsKey(key)
}
