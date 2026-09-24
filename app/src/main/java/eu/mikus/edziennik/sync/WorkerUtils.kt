/*
 * Copyright (c) Kuba Szczodrzyński 2020-1-18.
 */

package eu.mikus.edziennik.sync

import android.annotation.SuppressLint
import android.os.AsyncTask
import android.os.Build
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl
import androidx.work.impl.model.WorkSpec
import org.greenrobot.eventbus.EventBus
import eu.mikus.edziennik.App
import eu.mikus.edziennik.ext.MINUTE
import eu.mikus.edziennik.ext.formatDate
import eu.mikus.edziennik.ext.readConnectivityState
import eu.mikus.edziennik.utils.Utils

/**
 * What [WorkerUtils.scheduleNext] asks its caller to do about the schedule. There is deliberately no
 * `None` case: "leave it alone" is the absence of a decision, so the callback is simply not invoked
 * and no caller has to handle a state that never reaches it.
 */
internal enum class RescheduleDecision {
    /** Nothing viable is scheduled. Schedule one at the worker's normal interval. */
    AtInterval,

    /** Everything scheduled is already past due. Schedule one soon instead of an interval away. */
    Promptly,
}

/**
 * A deliberate subset of WorkManager's own internal `NetworkState`, re-derived because that type is
 * not public API. Named differently so it does not read as the same thing.
 */
internal data class ConnectivityState(
    val connected: Boolean,
    val validated: Boolean,
    val unmetered: Boolean,
)

object WorkerUtils {
    /** A job later than this should be replaced — it is not going to run on its own. */
    internal const val RESCHEDULE_GRACE_MS = 1 * MINUTE * 1000

    /**
     * ...but only a job later than *this* is evidence that an OEM App Manager killed it.
     *
     * Two thresholds because they answer two questions. This object cannot tell "killed by an App
     * Manager" from "waiting on a network constraint that is still unmet" — it never could. At one
     * shared minute, the prompt replacement scheduled by [RescheduleDecision.Promptly] would itself
     * look failed 70 s later, and a user with no network would get the non-cancelable dialog at
     * `MainActivity.onAppManagerDetectedEvent` on essentially every app open, falsely.
     */
    internal const val APP_MANAGER_GRACE_MS = 15 * MINUTE * 1000

    /**
     * Whether the schedule needs a new job, and how urgently. Null means leave it alone.
     *
     * [overdueCount] counts a subset of [unfinishedCount], so `unfinished - overdue` is the number
     * of jobs still expected to run by themselves. Both are plain `Int` and therefore silently
     * swappable at the call site — hence the names rather than `pendingCount`/`failedCount`.
     */
    internal fun decideReschedule(
        unfinishedCount: Int,
        overdueCount: Int,
        rescheduleIfFailedFound: Boolean,
    ): RescheduleDecision? {
        // The path used by App.onCreate and by UpdateWorker: only refill an empty queue, never
        // reason about lateness. A four-day update check has no useful notion of "a minute late".
        if (!rescheduleIfFailedFound)
            return if (unfinishedCount < 1) RescheduleDecision.AtInterval else null
        if (unfinishedCount - overdueCount > 0)
            return null
        return if (overdueCount > 0) RescheduleDecision.Promptly else RescheduleDecision.AtInterval
    }

    /**
     * The jobs that should have run at least [graceMs] ago and did not.
     *
     * Returns the specs rather than a count because the caller needs this at two thresholds (see
     * [APP_MANAGER_GRACE_MS]) and because [AppManagerDetectedEvent] carries the due timestamps.
     *
     * Only `ENQUEUED` work can be overdue. A `RUNNING` job is doing exactly what it was scheduled to
     * do, however long ago that was — treating it as failed is what let an app open cancel a sync in
     * flight.
     */
    @SuppressLint("RestrictedApi")
    internal fun overdueWork(specs: List<WorkSpec>, nowMs: Long, graceMs: Long): List<WorkSpec> =
        specs.filter {
            it.state == WorkInfo.State.ENQUEUED && it.periodStartTime + it.initialDelay < nowMs - graceMs
        }

    /**
     * Whether [required] is currently satisfied, mirroring WorkManager's own constraint controllers
     * rather than assuming they agree with each other.
     *
     * A null [state] means connectivity could not be read at all — not that the device is offline,
     * which is `connected = false`. It answers true, so an unreadable state still warns: staying
     * silent would hide the one condition this whole feature exists to report.
     *
     * `NOT_ROAMING` is answered true whenever the device is connected. Deciding it properly needs
     * roaming detail this phase has no reason to gather, and no worker in this app sets it. That is a
     * deliberate limit, not an oversight.
     *
     * [sdkInt] is a parameter so the API-26 branch is testable without Robolectric.
     */
    internal fun networkConstraintMet(
        required: NetworkType,
        state: ConnectivityState?,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): Boolean = when {
        state == null -> true
        required == NetworkType.NOT_REQUIRED -> true
        !state.connected -> false
        // NetworkConnectedController: SDK_INT >= 26 ? (connected && validated) : connected
        required == NetworkType.CONNECTED -> sdkInt < Build.VERSION_CODES.O || state.validated
        required == NetworkType.UNMETERED -> state.unmetered
        else -> true
    }

    /**
     * The overdue jobs whose lateness is NOT explained by an unmet network constraint — i.e. the ones
     * worth blaming an OEM App Manager for.
     *
     * Deliberately separate from [overdueWork], which keeps feeding the *reschedule* decision its
     * unfiltered list: filtering that too would stop an offline job ever being replaced, which is the
     * opposite of what the previous phase built.
     */
    @SuppressLint("RestrictedApi")
    internal fun appManagerSuspects(
        specs: List<WorkSpec>,
        nowMs: Long,
        state: ConnectivityState?,
    ): List<WorkSpec> = overdueWork(specs, nowMs, APP_MANAGER_GRACE_MS)
        .filter { networkConstraintMet(it.constraints.requiredNetworkType, state) }

    /**
     * Schedule [tag]'s job only if it is not already scheduled, and tell the caller how urgently.
     *
     * `internal` and not `inline`. The lambda is captured into an `AsyncTask.execute` Runnable, so
     * `inline` never inlined anything useful at the call site — and a *public* `inline` function may
     * not reference `internal` declarations at all, which would rule out [decideReschedule].
     *
     * [tag] is a parameter rather than `SyncWorker`'s class name. It was hardcoded, so
     * `UpdateWorker.scheduleNext` decided from **SyncWorker's** jobs and could not see its own.
     */
    @SuppressLint("RestrictedApi")
    internal fun scheduleNext(
        app: App,
        tag: String,
        rescheduleIfFailedFound: Boolean = true,
        onReschedule: (RescheduleDecision) -> Unit,
    ) {
        AsyncTask.execute {
            val workManager = WorkManager.getInstance(app) as WorkManagerImpl
            val dao = workManager.workDatabase.workSpecDao()
            // getUnfinishedWorkWithTag is `state NOT IN (2,3,5) AND <tag matches>`. The query this
            // replaced, getScheduledWork(), is `state=0 AND schedule_requested_at<>-1` -- ENQUEUED
            // only. RUNNING work was invisible, so an app open during a sync saw an empty schedule
            // and cancelled the sync it was in the middle of.
            val specs = dao.getWorkSpecs(dao.getUnfinishedWorkWithTag(tag)).filterNot { it.isPeriodic }
            specs.forEach {
                Utils.d("WorkerUtils", "Work: ${it.id} at ${(it.periodStartTime + it.initialDelay).formatDate()}. State = ${it.state}")
            }
            val now = System.currentTimeMillis()
            val overdue = overdueWork(specs, now, RESCHEDULE_GRACE_MS)
            Utils.d("WorkerUtils", "${overdue.size} of ${specs.size} $tag work requests are overdue")
            if (rescheduleIfFailedFound) {
                val failed = appManagerSuspects(specs, now, app.readConnectivityState())
                if (failed.isNotEmpty()) {
                    Utils.d("WorkerUtils", "App Manager detected!")
                    EventBus.getDefault().postSticky(AppManagerDetectedEvent(failed.map { it.periodStartTime + it.initialDelay }))
                }
            }
            decideReschedule(specs.size, overdue.size, rescheduleIfFailedFound)?.let(onReschedule)
        }
    }
}
