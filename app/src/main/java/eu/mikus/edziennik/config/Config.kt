/*
 * Copyright (c) Kuba Szczodrzyński 2019-11-26.
 */

package eu.mikus.edziennik.config

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import eu.mikus.edziennik.App
import eu.mikus.edziennik.BuildConfig
import eu.mikus.edziennik.config.utils.*
import eu.mikus.edziennik.data.api.models.PendingUserAction
import eu.mikus.edziennik.data.api.models.Update
import eu.mikus.edziennik.data.db.AppDb
import eu.mikus.edziennik.ui.widgets.WidgetConfig
import eu.mikus.edziennik.utils.Utils
import java.util.concurrent.ConcurrentHashMap

@Suppress("RemoveExplicitTypeArguments")
class Config(db: AppDb) : BaseConfig(db) {
    companion object {
        const val DATA_VERSION = 12
        private const val TAG = "Config"
    }

    /**
     * Built on first use and cached until [removeProfile] retires it.
     *
     * Concurrent because [removeProfile] runs off the main thread - `ProfileRemoveDialog` calls it
     * inside `withContext(Dispatchers.Default)` - while `SzkolnyTask` is already putting into this
     * map from a sync thread, and a concurrent structural removal is what corrupts a `HashMap`.
     *
     * It does **not** make the `?:` + put in [get] atomic, and the loser of that race is not
     * harmless. Two threads missing on the same id both build; whoever was handed the losing
     * instance keeps it - `Profile.config`'s lazy and the view models that capture a
     * `ProfileConfig` all do - and the two then hold separate `values` maps and separate
     * [ConfigDelegate] caches, so a write through one never reaches the other and the setting
     * appears to revert. This change widens that window, because [get]'s miss path now runs a Room
     * query where it used to filter an in-memory list.
     *
     * If that is ever worth closing, the call is `ConcurrentHashMap.putIfAbsent`, which is **API
     * 1**; `computeIfAbsent` is the API 24 one. Leaving the race alone preserves the behaviour that
     * predates this change rather than endorsing it - and note that minSdk is 23 while every
     * Robolectric test here runs at sdk 35, so only `lintDebug` would catch a `NewApi` slip.
     */
    private val profileConfigs = ConcurrentHashMap<Int, ProfileConfig>()

    val ui by lazy { ConfigUI(this) }
    val sync by lazy { ConfigSync(this) }
    val timetable by lazy { ConfigTimetable(this) }
    val grades by lazy { ConfigGrades(this) }

    var dataVersion by config<Int>(DATA_VERSION)
    var hash by config<String>("")

    var lastProfileId by config<Int>(0)
    var loginFinished by config<Boolean>(false)
    var privacyPolicyAccepted by config<Boolean>(false)
    var pendingUserAction by config<PendingUserAction?>(null)
    var update by config<Update?>(null)
    var updatesChannel by config<String>("release")

    var devMode by config<Boolean?>("debugMode", null)
    var devModePassword by config<String?>(null)
    var enableChucker by config<Boolean?>(null)

    var apiAvailabilityCheck by config<Boolean>(true)
    var apiInvalidCert by config<String?>(null)
    var apiKeyCustom by config<String?>(null)
    var appInstalledTime by config<Long>(0L)
    var appRateSnackbarTime by config<Long>(0L)
    var appVersion by config<Int>(BuildConfig.VERSION_CODE)
    var appVersionCore by config<Int>(0)
    var validation by config<String?>(null, "buildValidation")

    var archiverEnabled by config<Boolean>(true)
    var runSync by config<Boolean>(false)
    var widgetConfigs by config<JsonObject> { JsonObject() }
    var widgetBindingsRepaired by config<Boolean>(false)

    /**
     * Runs the one-shot data fixups this process owes, off the main thread - `App.onCreate` calls
     * this inside `launch { withContext(Dispatchers.Default) { … } }`.
     *
     * Two gates, two one-shots. [repairWidgetBindings] is **not** inside [ConfigMigration] and is
     * **not** gated on [dataVersion], because neither would reach the devices that need it.
     * `config<Int>(DATA_VERSION)` makes the delegate's default the constant itself - deliberately,
     * since `fd407b2b` - so a device that never wrote the row reads whatever the running build says
     * and skips every ladder step. And nothing writes 12: [ConfigMigration]'s ladder tops out at
     * 11, so a device that ran that step is permanently `11 < 12` and re-enters it on every launch.
     * A `dataVersion` gate would therefore fire on the wrong devices and record nothing.
     *
     * [widgetBindingsRepaired] is a config **row**, not a column - the `config` table is
     * `(profileId, key, value)` - so this needs no schema change, which matters because a version
     * bump without a registered `Migration(n, n + 1)` still wipes the database: `AppDb` registers
     * each migration by hand (`MIGRATION_1_2`, `MIGRATION_2_3`) and keeps
     * `fallbackToDestructiveMigration()`.
     *
     * The flag is set after the call, not before it, so a failure leaves the repair to retry on the
     * next launch rather than recording a sweep that did not happen. The repair is idempotent, so a
     * retry costs nothing.
     *
     * The repair is wrapped because it is **optional work sitting above work that is not**. This
     * method is the first statement in `App.onCreate`'s background block, and
     * `SSLProviderInstaller.install`, `checkDevModePassword` and both `scheduleNext` calls
     * follow it inside the same `withContext`; `App`'s scope is a bare `Job()` with no
     * `CoroutineExceptionHandler`. So a throw here crashes at launch, every launch, and silently
     * stops sync ever being scheduled - and the user cannot open LAB to undo whatever caused it.
     * [repairWidgetBindings] failing costs one stale widget binding; letting it propagate costs the
     * app.
     *
     * The catch does not swallow a failure into silence: the flag stays unset, so the repair is
     * retried on the next launch, and it is logged through `Utils.d` - which is gated on `devMode`,
     * so on a normal release build the unset flag is the only trace. The one shape that can still
     * reach it is a `widgetConfigs` row holding malformed JSON, which [ConfigDelegate] deserialises
     * to `null` into a non-null-typed property - a defect of that contract, shared by every
     * `config<JsonObject>` property, and not fixed here.
     */
    fun migrate(app: App) {
        if (dataVersion < DATA_VERSION || hash == "")
            // migrate old data version OR freshly installed app (or updated from 3.x)
            ConfigMigration(app, this)
        if (!widgetBindingsRepaired) {
            try {
                repairWidgetBindings()
                widgetBindingsRepaired = true
            }
            catch (e: Exception) {
                // the repair is optional work; everything App.onCreate has left to do is not
                Utils.d(TAG, "widget binding repair failed, retrying next launch: $e")
            }
        }
    }

    /**
     * Re-points every widget left bound to a profile id nothing holds - the damage phase 66's
     * [unbindWidgets] prevents from here on but cannot undo.
     *
     * **This is prophylactic, not curative, and the distinction is the whole design.** By the time
     * an id has been reused there is nothing to detect: the stored entry is byte-identical whether
     * it was configured for the child who holds that id now or for the one who held it before, and
     * neither side records a time. What this catches is the window before that - a removal on an
     * older build with no new child added since - where the widget already renders "profile does
     * not exist" and would start rendering the next child the moment one appears, because
     * `LoginViewModel` mints `ProfileDao.lastId + 1` and that is a MAX over the survivors.
     *
     * **`> 0` is the whole of the sentinel safety.** One comparison excludes `-1`, which the
     * timetable widget reads as "all profiles" and every notifications widget stores, and `-2`,
     * which an entry phase 66 already re-pointed holds. Re-pointing a `-1` entry would turn a
     * one-child timetable widget into an all-children one, which shows the new child - finding AA,
     * recreated by its own fix. An entry with no `profileId` answers `null` from [boundProfileId]
     * and is dropped before the comparison; Gson reads an absent key as `-1`, so it is unified too.
     *
     * The profile table is read **only** when some entry names an id, through
     * [eu.mikus.edziennik.data.db.dao.ProfileDao.idsNow] - ids, not rows, so this learns that an id
     * exists and nothing else about a profile. `idsNow` and not `idsForSyncNow`: the latter filters
     * `archived = 0`, and an archived profile keeps a positive id and still renders, because
     * `getByIdNow` - what both providers call - does not filter it either.
     *
     * It calls [unbindWidgets] rather than re-implementing it, which is what keeps the raw
     * `JsonObject` mutation and the write-back through the delegate setter in one place. One pass
     * per orphaned id; that count is 0 or 1 on essentially every device.
     */
    private fun repairWidgetBindings() {
        val bound = widgetConfigs.entrySet()
            .mapNotNull { (_, element) -> (element as? JsonObject)?.boundProfileId() }
            .filter { it > 0 }
            .toSortedSet()
        if (bound.isEmpty())
            return
        bound.removeAll(db.profileDao().idsNow.toSet())
        bound.forEach(::unbindWidgets)
    }

    /**
     * Retires [profileId] from config: deletes the stored rows, drops the in-memory copy, and
     * unbinds every widget pointed at it - see [unbindWidgets].
     *
     * Both halves are needed and this is the only place that does both. Profile ids are reused -
     * `ProfileDao.lastId` is a MAX over the survivors, not a counter - so clearing only the table
     * leaves the next child reading the previous child's settings until the app restarts, and
     * clearing only memory hands them back on the next launch. Dropping the instance, rather than
     * clearing its `values`, is what retires the per-property caches inside [ConfigDelegate]:
     * those cache the *default* too when a key is absent, so an emptied `values` would change
     * nothing.
     *
     * Pass a real profile id. Nothing here guards the argument, and `removeProfile(-1)` would
     * delete every global setting while the live global [Config] went on serving them from memory
     * until the process restarted - and would now also re-point every unified-timetable and every
     * notifications widget, both of which store `-1`.
     *
     * **Deleting before evicting narrows a race; it does not close one.** A [get] that misses the
     * cache holds nothing while it reads the rows, so one that read them *before* the `clear` can
     * still `put` *after* the `remove`, re-caching the removed profile's settings - and that holds
     * under either statement order. This order is still the right one, because reversed, the whole
     * gap between the two statements is unsafe as well. Closing it would need [get]'s miss path and
     * this method under a shared lock.
     *
     * **Retiring before deleting is what closes the write leak.** [ProfileConfig.retire] refuses
     * further writes through the instance *and* cancels anything already queued on its scope, so a
     * write in flight cannot re-insert a row after the `DELETE` and be handed to the reused id by
     * [get]. It has to come first: retired after the `clear`, the queued write lands in the gap,
     * which is the leak itself. It does not move the eviction, so the ordering above is unaffected
     * - a concurrent [get] in the window is simply handed the retired instance instead of a live
     * one.
     *
     * Two residues remain. A write already *executing* `ConfigDao.add` cannot be interrupted -
     * Room's generated insert is blocking, not suspending - so that window narrows to the width of
     * one DAO call rather than closing. And any [get] for this id that completes after the eviction
     * installs a **fresh, live** instance that nothing retires: the straddling one above, which is
     * a race, or any later one, which needs no concurrency at all. `Profile`'s
     * `val config by lazy { App.config[this.id] }` resolving for the first time while `App.profile`
     * is still the removed profile, in the window before `profileLoadLast` swaps it, is enough. A
     * write through either instance persists a row under the removed id, which the next child to
     * take it inherits. This is left open on purpose.
     */
    fun removeProfile(profileId: Int) {
        profileConfigs[profileId]?.retire()
        db.configDao().clear(profileId)
        profileConfigs.remove(profileId)
        unbindWidgets(profileId)
    }

    /**
     * The `profileId` this entry names, or `null` when it names none that can be compared.
     *
     * `null` covers three shapes that must all be left alone rather than treated as a match: the
     * key absent (Gson reads that as `-1`, so the entry is a unified widget), the value a JSON
     * object or array, and the value a primitive that is neither a number nor a string that parses
     * as one. The last one is why this was written: [eu.mikus.edziennik.ext.getInt] then checked
     * `isJsonPrimitive` and called `asInt`, so a boolean, or a string that does not parse, passed
     * that check and `Integer.parseInt` threw. Phase 68 made `getInt` branch on the number variant
     * first, so it now answers exactly what this does and this is a duplicate of
     * `getInt("profileId")`. Collapsing the two is its own change and is not folded in here.
     * A numeric string such as `"2"` does parse, and answers 2 exactly as `getInt` did: Gson's
     * reader accepts a string token for an `Int` field, so the providers resolve that widget as
     * profile 2 and it must not be skipped.
     * The blob is hand-editable from LAB, and both callers run somewhere a throw is expensive:
     * inside `withContext(Dispatchers.Default)` on the profile-removal path, and inside [migrate]
     * at startup, above everything else `App.onCreate` has left to do.
     */
    private fun JsonObject.boundProfileId(): Int? =
        (get("profileId") as? JsonPrimitive)?.let {
            if (it.isNumber) it.asInt else it.asString.toIntOrNull()
        }

    /**
     * Points every widget bound to [profileId] at [WidgetConfig.PROFILE_REMOVED], so it stops
     * resolving to a profile at all.
     *
     * [widgetConfigs] is a **global** value keyed by `appWidgetId` whose entries each carry their
     * own `profileId`, so nothing that sweeps profile-scoped *tables* can see it - and ids are
     * reused, so a widget set up for a removed child would otherwise render the next child's name,
     * lessons or lucky number under it.
     *
     * The entry is re-pointed, not removed. Removing it makes each provider's `?: continue` /
     * `?: return@forEach` fire, `updateAppWidget` is then never called, and the launcher goes on
     * showing the last frame it was handed - the removed child's data - indefinitely. It would also
     * discard the user's style, which a placed widget can never be asked for again, because
     * `android:configure` runs at placement only. Re-pointed, the providers reach the "profile does
     * not exist" state they already render.
     *
     * The member is edited in place rather than round-tripped through [WidgetConfig]: a round-trip
     * would drop the stale `"opacity"` key and rewrite every absent key to its declared default,
     * which is exactly what turns an already-placed widget translucent. Entries that do not match
     * are left untouched, which is also what keeps `-1` - all profiles, and every notifications
     * widget - out of this. A member whose value is not an object (a string, a `null`) is skipped
     * rather than cast: the `as` is this function's own, and a `ClassCastException` here would take
     * the removal down. The `profileId` read is guarded by [boundProfileId], which answers `null`
     * for every shape that does not name an id - so a hand-edited `"profileId": "two"` is skipped
     * rather than throwing out of this loop, while `"profileId": "2"` still reads as 2 and is
     * re-pointed.
     *
     * Runs last in [removeProfile] on purpose: if anything above it throws, the widgets stay bound
     * to a profile whose row has not been deleted yet, and the retry unbinds them. Unbinding first
     * would leave them detached from a profile that still exists.
     *
     * The same failure has a second cost, which a retry repairs only once it succeeds: if
     * `configDao().clear` throws, `profileConfigs.remove` never runs either, so the map keeps the
     * instance [ProfileConfig.retire] already retired - for a profile that still exists - and every
     * settings change for that child is silently refused until a retry of the removal succeeds or
     * the process restarts. Before retiring moved ahead of the `DELETE`, the same failure left a
     * working instance. It takes a SQLite failure that also propagates out of
     * `ProfileRemoveDialog`, so in practice the crash dominates; this records the cost, it does
     * not close it.
     *
     * **This mutates [widgetConfigs] off the main thread, from two callers, and takes no lock.**
     * `ProfileRemoveDialog` calls [removeProfile] inside `withContext(Dispatchers.Default)`, and
     * [repairWidgetBindings] reaches here from [migrate], which `App.onCreate` runs inside the same
     * dispatcher at process start - when a widget broadcast is especially likely to land, since a
     * broadcast is often what started the process. Meanwhile the three widget providers read this
     * same [JsonObject] instance on the main thread and their `onDeleted` removes from it there,
     * as does `WidgetConfigActivity`, whose OK tap adds to it when a widget is placed. A widget
     * deleted or placed inside the loop can race it: a `remove` or an `add` against this
     * `entrySet()` iteration, or against the `toString()` the delegate setter then runs over the
     * whole object, makes either throw `ConcurrentModificationException`. The repair is exposed one
     * step earlier than the removal is: [repairWidgetBindings] iterates the same `entrySet()`
     * itself before it calls here, so on any device with a widget it can throw even when nothing
     * turns out to be orphaned. Where the throw goes differs by caller: out of [removeProfile] it
     * propagates, and out of the repair [migrate] catches it, leaves [widgetBindingsRepaired] unset
     * and lets the next launch retry.
     * The `addProperty` itself is not part of that: it only ever reaches an entry whose `profileId`
     * is already present, so it replaces a value rather than inserting a key, which
     * `LinkedTreeMap` does not count as a structural change - a provider reading that child sees
     * either id and does not throw. The window is microseconds and the trigger is a widget being
     * deleted or placed at that same moment, whether during the removal tap or during the first
     * moments of the process - placing one needs user input, so that half is practically
     * unreachable - so it is accepted rather than closed; closing it would take a lock shared with
     * the providers, not one here.
     *
     * "The same instance" above is not guaranteed, and that is a second outcome that throws
     * nothing. [ConfigDelegate.getValue] is an unsynchronised check-then-set over two non-volatile
     * fields (`DelegateConfig.kt:60-61`, `:80-101`), so a provider that races [migrate]'s first
     * read of [widgetConfigs] can deserialise its own [JsonObject] rather than share this one.
     * The repair then mutates and persists instance A and sets [widgetBindingsRepaired], while a
     * provider's `onDeleted` write-back persists instance B - reverting the repair with the
     * one-shot flag already `true`, so it is never retried. The cost is that the repair is
     * silently skipped for that device. It is bounded: B is a clean re-read of the stored row, so
     * the revert corrupts nothing and adds no new wrong-child render. It does not make the device
     * safe - it is left exactly where it was before this repair existed, still exposed to the
     * stale binding the repair is there to prevent once an id is reused. Accepted for the same
     * reason, and closed by the same lock.
     */
    private fun unbindWidgets(profileId: Int) {
        val configs = widgetConfigs
        var changed = false
        for ((_, element) in configs.entrySet()) {
            val widget = element as? JsonObject ?: continue
            if (widget.boundProfileId() != profileId)
                continue
            widget.addProperty("profileId", WidgetConfig.PROFILE_REMOVED)
            changed = true
        }
        // through the delegate setter, never config.set: ConfigDelegate caches after the first read
        // and BaseConfig.set does not invalidate that cache, so a raw write emits a stale key
        if (changed)
            widgetConfigs = configs
    }

    /**
     * This profile's settings, built on first use and cached until [removeProfile] retires it.
     *
     * Reads the profile's own rows rather than reusing [entries]. That snapshot is taken once, when
     * `App.onCreate` constructs this object, and nothing ever refreshes it - so before this read
     * existed, a profile removed during the process went on being served its deleted rows from
     * here, and profile ids are reused. Do not "optimise" this back to [entries].
     *
     * The cost is one `WHERE profileId = ?` read per profile per process, against the `config`
     * table's own primary key. It runs on the main thread for `Profile.config`'s lazy and for
     * `AgendaScreen`'s default argument, which is safe only because `AppDb` is built with
     * `allowMainThreadQueries()`.
     */
    operator fun get(profileId: Int): ProfileConfig {
        return profileConfigs[profileId] ?: ProfileConfig(
            db,
            profileId,
            db.configDao().getAllNow(profileId),
        ).also {
            profileConfigs[profileId] = it
        }
    }
}
