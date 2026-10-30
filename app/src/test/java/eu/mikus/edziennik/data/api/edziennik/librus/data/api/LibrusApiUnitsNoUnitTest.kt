/*
 * Copyright (c) Mikolaj Olszewski 2026-10-30.
 */
package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import com.google.gson.JsonObject
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.api.edziennik.librus.DataLibrus
import eu.mikus.edziennik.data.api.edziennik.librus.ENDPOINT_LIBRUS_API_UNITS
import eu.mikus.edziennik.data.api.edziennik.librus.Librus
import eu.mikus.edziennik.data.api.edziennik.librus.LibrusFeatures
import eu.mikus.edziennik.data.api.edziennik.librus.data.LibrusData
import eu.mikus.edziennik.data.api.events.UserActionRequiredEvent
import eu.mikus.edziennik.data.api.interfaces.EdziennikCallback
import eu.mikus.edziennik.data.api.models.ApiError
import eu.mikus.edziennik.data.api.prepare
import eu.mikus.edziennik.data.db.entity.EndpointTimer
import eu.mikus.edziennik.data.db.entity.LoginStore
import eu.mikus.edziennik.data.db.enums.LoginMode
import eu.mikus.edziennik.data.db.enums.LoginType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * `LibrusApiUnits` with no unit id to ask for makes no request, so it must record no timer: a timer
 * says "this endpoint synced", and nothing did. The endpoint has to stay due, so that a later run
 * reads the unit's start points once Classes has stored an id.
 *
 * Runs with no network at all. The shared run drives the real `LibrusData` dispatcher into the
 * no-unit branch, which returns before `apiGet`, then asks the real scheduler what is due. Each part
 * of the fixture is load-bearing:
 *
 * - **The null profile** keeps `Data`'s init and `saveData` off the database, and makes no unit id
 *   known (`unitIdUnknown`), which is the branch under test.
 * - **`endpointTimers` must start empty.** The timer writer appends, and `prepare` looks a timer up
 *   with `singleOrNull`, so a pre-loaded Units timer plus one appended by a mutation would read as no
 *   timer at all, and the "still due" test would pass against the bug. The arrange step asserts it.
 * - **`999_999` is an unmatched endpoint id.** `LibrusData` advances through it without a request,
 *   and a chain that completes without advancing past Units leaves it in `targetEndpoints`. That
 *   gives the completion test's `isEmpty()` half its teeth: the completion count alone already
 *   catches a missing continuation, but not one that completes by calling the outer callback
 *   directly. Do not simplify the map to Units alone.
 * - **The "still due" test reuses the same `Data`**, because a fresh null-profile `Data` has no
 *   timers, so Units would be due whatever the code did.
 * - **`onlyEndpoints` is `null`** there, because a set containing Units would force it to be
 *   selected whatever its timer said.
 *
 * The fixture comes from `LibrusParserErrorRecoveryTest`, which this does not touch. Boots the real
 * [App]: `Librus`'s constructor takes one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibrusApiUnitsNoUnitTest {

    /** An endpoint id no arm of `LibrusData.useEndpoint` matches, so its `else` arm just advances. */
    private val unmatchedEndpoint = 999_999

    private class Spy : EdziennikCallback {
        val started = mutableListOf<Int>()
        override fun onCompleted() {}
        override fun onRequiresUserAction(event: UserActionRequiredEvent) {}
        override fun onError(apiError: ApiError) {}
        override fun onProgress(step: Float) {}
        override fun onStartProgress(stringRes: Int) { started += stringRes }
    }

    private class NoUnitRun(val librus: Librus, val spy: Spy, val completions: Int)

    private fun runNoUnitSync(): NoUnitRun {
        val spy = Spy()
        val app = RuntimeEnvironment.getApplication() as App
        val librus = Librus(app, null, LoginStore(1, LoginType.LIBRUS, LoginMode.LIBRUS_EMAIL, JsonObject()), spy)
        // Asserted BEFORE the chain runs, so a wrong answer never lets Units reach apiGet.
        assertTrue("the branch under test is the no-unit one", librus.data.unitIdUnknown)
        assertTrue("no timer may exist before the run", librus.data.endpointTimers.isEmpty())
        librus.data.targetEndpoints[ENDPOINT_LIBRUS_API_UNITS] = null
        librus.data.targetEndpoints[unmatchedEndpoint] = null
        var completions = 0
        LibrusData(librus.data) { completions++ }
        assertTrue("the dispatcher must have reached the Units arm",
            R.string.edziennik_progress_endpoint_units in spy.started)
        return NoUnitRun(librus, spy, completions)
    }

    @Test fun `with no unit id the chain still goes past Units and completes`() {
        val run = runNoUnitSync()

        assertEquals("the chain must complete exactly once", 1, run.completions)
        assertTrue("and consume every endpoint it was given", run.librus.data.targetEndpoints.isEmpty())
    }

    @Test fun `with no unit id no timer is written`() {
        val run = runNoUnitSync()

        assertEquals(emptyList<EndpointTimer>(), run.librus.data.endpointTimers)
    }

    @Test fun `with no unit id Units is still due on the next sync`() {
        val run = runNoUnitSync()

        run.librus.data.prepare(LibrusFeatures, null, null)

        assertTrue("Units must be due again, on the same Data the first run used",
            ENDPOINT_LIBRUS_API_UNITS in run.librus.data.targetEndpoints)
    }

    @Test fun `unitIdUnknown tracks the stored unit id`() {
        val data = DataLibrus(RuntimeEnvironment.getApplication() as App, null,
            LoginStore(1, LoginType.LIBRUS, LoginMode.LIBRUS_EMAIL, JsonObject()))

        assertTrue(data.unitIdUnknown)
        data.unitId = 5L
        assertFalse(data.unitIdUnknown)
        // a literal 0: Classes' old fallback already stored this in users' studentData
        data.unitId = 0L
        assertTrue(data.unitIdUnknown)
    }
}
