/*
 * Copyright (c) Mikolaj Olszewski 2026-10-15.
 */
package eu.mikus.edziennik.data.api.edziennik.librus

import com.google.gson.JsonObject
import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.api.ERROR_REQUEST_FAILURE
import eu.mikus.edziennik.data.api.EXCEPTION_LIBRUS_API_REQUEST
import eu.mikus.edziennik.data.api.EXCEPTION_LIBRUS_MESSAGES_REQUEST
import eu.mikus.edziennik.data.api.events.UserActionRequiredEvent
import eu.mikus.edziennik.data.api.interfaces.EdziennikCallback
import eu.mikus.edziennik.data.api.models.ApiError
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
 * One endpoint's parse failure must skip that endpoint, not the rest of the profile.
 *
 * `LibrusApi.apiGet` wraps an endpoint's whole parser in one try/catch, and the parser's last two
 * statements are `data.setSyncNext(...)` and `onSuccess(endpointId)` - the latter being
 * `LibrusData.nextEndpoint`'s continuation, the only thing that advances the endpoint chain. A throw
 * inside the loop skips both. `targetEndpoints` is a sorted map walked by `firstKey()`, so with the
 * error critical every endpoint with a higher id never runs, on every sync, forever: a bad `Weight`
 * at 1021 takes grades, homework, attendance, announcements, Synergia and messages with it.
 *
 * **Marking the error non-critical is only half the fix, and the half alone is worse than the bug.**
 * `ApiService`'s non-critical arm counts the error and returns - it does not cancel, clear, or call
 * `runTask()` - so nothing resumes the chain and nothing ever posts a terminal event. Hence the
 * `onCompleted` assertions below: they, not the `isCritical` ones, are what proves the chain resumed.
 * See ApiServiceErrorBranchTest for the other half of that characterization.
 *
 * Runs with no network at all. `LibrusData.useEndpoint` ends in `else -> onSuccess(endpointId)`, so
 * an endpoint id with no `when` arm advances the chain without issuing a request; `Data`'s
 * constructor guards its DAO loads on a non-null profile and `saveData()` returns early on one, so a
 * null profile keeps the fixture off the database too. `Data.callback` is public, and it holds the
 * real `wrapCallback` instance - this drives the production object, not a copy.
 *
 * Boots the real [App]: `Librus`'s constructor takes one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibrusParserErrorRecoveryTest {

    /** An endpoint id no arm of `LibrusData.useEndpoint` matches, so its `else` arm just advances. */
    private val unmatchedEndpoint = 999_999

    private class Spy : EdziennikCallback {
        val errors = mutableListOf<ApiError>()
        var completed = 0
        override fun onCompleted() { completed++ }
        override fun onRequiresUserAction(event: UserActionRequiredEvent) {}
        override fun onError(apiError: ApiError) { errors += apiError }
        override fun onProgress(step: Float) {}
        override fun onStartProgress(stringRes: Int) {}
    }

    private fun librusWithOnePendingEndpoint(spy: Spy): Librus {
        val app = RuntimeEnvironment.getApplication() as App
        val librus = Librus(
            app,
            null,
            LoginStore(1, LoginType.LIBRUS, LoginMode.LIBRUS_EMAIL, JsonObject()),
            spy,
        )
        librus.data.targetEndpoints[unmatchedEndpoint] = null
        return librus
    }

    @Test fun `an API parse failure is reported and the chain goes on`() {
        val spy = Spy()
        val librus = librusWithOnePendingEndpoint(spy)

        librus.data.callback.onError(ApiError("T", EXCEPTION_LIBRUS_API_REQUEST))

        assertEquals("the failure must still be reported", 1, spy.errors.size)
        assertFalse("and reported non-critically, or ApiService kills the task", spy.errors[0].isCritical)
        assertEquals("the chain must resume and reach completion", 1, spy.completed)
        assertTrue("and consume the endpoints it resumed into", librus.data.targetEndpoints.isEmpty())
    }

    @Test fun `a messages parse failure recovers the same way`() {
        val spy = Spy()
        val librus = librusWithOnePendingEndpoint(spy)

        librus.data.callback.onError(ApiError("T", EXCEPTION_LIBRUS_MESSAGES_REQUEST))

        assertFalse(spy.errors[0].isCritical)
        assertEquals("the arm must cover every parser transport, not just the API one", 1, spy.completed)
    }

    /**
     * Review Focus 3, and the negative control. Without it the arm could be widened to `else ->` and
     * everything above would still pass. A transport failure is profile-wide: continuing would retry
     * ~25 endpoints that will all fail the same way.
     */
    @Test fun `a transport failure stays critical and does not resume`() {
        val spy = Spy()
        val librus = librusWithOnePendingEndpoint(spy)

        librus.data.callback.onError(ApiError("T", ERROR_REQUEST_FAILURE))

        assertTrue("a network failure must still abort the task", spy.errors[0].isCritical)
        assertEquals("and must not resume the chain", 0, spy.completed)
        assertFalse("leaving the endpoint pending", librus.data.targetEndpoints.isEmpty())
    }

    /**
     * Review Focus 1. When the failing endpoint was the last one there is nothing left to resume
     * into, and `nextEndpoint` must terminate through its empty-map arm rather than hang.
     */
    @Test fun `a parse failure on the last endpoint still completes`() {
        val spy = Spy()
        val librus = librusWithOnePendingEndpoint(spy)
        librus.data.targetEndpoints.clear()

        librus.data.callback.onError(ApiError("T", EXCEPTION_LIBRUS_API_REQUEST))

        assertFalse(spy.errors[0].isCritical)
        assertEquals("an empty chain must still reach completion, not hang", 1, spy.completed)
    }

    /**
     * Review Focus 2. `wrapCallback`'s `internalErrorList` guard is kept deliberately: `ApiService`'s
     * `errorList` is never drained - `ErrorReportTask` has no producers anywhere in `app/src/main` -
     * and every `ApiError` retains the whole response body as a String. A second failure of the same
     * code also means something systemic, where aborting is the conservative answer.
     */
    @Test fun `a second parse failure of the same code aborts`() {
        val spy = Spy()
        val librus = librusWithOnePendingEndpoint(spy)
        librus.data.targetEndpoints[unmatchedEndpoint + 1] = null

        librus.data.callback.onError(ApiError("T", EXCEPTION_LIBRUS_API_REQUEST))
        librus.data.callback.onError(ApiError("T", EXCEPTION_LIBRUS_API_REQUEST))

        assertEquals(2, spy.errors.size)
        assertFalse("the first recovers", spy.errors[0].isCritical)
        assertTrue("the second does not - the recovery is bounded per code per task", spy.errors[1].isCritical)
    }
}
