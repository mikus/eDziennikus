/*
 * Copyright (c) Mikolaj Olszewski 2026-11-11.
 */
package eu.mikus.edziennik.data.api.edziennik.librus

import com.google.gson.JsonObject
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.api.ERROR_LIBRUS_API_ACCESS_DENIED
import eu.mikus.edziennik.data.api.ERROR_LIBRUS_API_LUCKY_NUMBER_NOT_ACTIVE
import eu.mikus.edziennik.data.api.ERROR_LIBRUS_API_TOKEN_EXPIRED
import eu.mikus.edziennik.data.api.ERROR_LIBRUS_MESSAGES_ACCESS_DENIED
import eu.mikus.edziennik.data.api.ERROR_LIBRUS_PORTAL_ACCESS_DENIED
import eu.mikus.edziennik.data.api.ERROR_LIBRUS_SYNERGIA_ACCESS_DENIED
import eu.mikus.edziennik.data.api.ERROR_LOGIN_DATA_MISSING
import eu.mikus.edziennik.data.api.ERROR_LOGIN_LIBRUS_MESSAGES_NO_SESSION_ID
import eu.mikus.edziennik.data.api.ERROR_LOGIN_LIBRUS_SYNERGIA_NO_SESSION_ID
import eu.mikus.edziennik.data.api.ERROR_LOGIN_LIBRUS_SYNERGIA_NO_TOKEN
import eu.mikus.edziennik.data.api.ERROR_LOGIN_LIBRUS_SYNERGIA_TOKEN_INVALID
import eu.mikus.edziennik.data.api.EXCEPTION_LIBRUS_API_REQUEST
import eu.mikus.edziennik.data.api.edziennik.librus.login.LibrusLogin
import eu.mikus.edziennik.data.api.events.UserActionRequiredEvent
import eu.mikus.edziennik.data.api.interfaces.EdziennikCallback
import eu.mikus.edziennik.data.api.models.ApiError
import eu.mikus.edziennik.data.db.entity.LoginStore
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.enums.LoginMethod
import eu.mikus.edziennik.data.db.enums.LoginMethod.LIBRUS_API
import eu.mikus.edziennik.data.db.enums.LoginMethod.LIBRUS_MESSAGES
import eu.mikus.edziennik.data.db.enums.LoginMethod.LIBRUS_PORTAL
import eu.mikus.edziennik.data.db.enums.LoginMethod.LIBRUS_SYNERGIA
import eu.mikus.edziennik.data.db.enums.LoginMode
import eu.mikus.edziennik.data.db.enums.LoginType
import im.wangchao.mhttp.MHttp
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

/**
 * Pins what `Librus` does when a login error it answers by logging in again arrives while a login is
 * running. An "access denied" error must log in again with the denied method, then re-run the login
 * that was interrupted, then the logins still queued behind it. A Synergia or Messages session error
 * must re-run the login that raised it, then the logins queued behind it, and a second failure of the
 * same code must end the task as a critical error.
 *
 * `LibrusLogin` takes each method off `targetLoginMethods` when it starts it, and a login that fails
 * never joins `loginMethods`. The old arms re-planned with `prepareFor(denied)`, which clears the
 * queue and rebuilds it from the denied method's dependency chain alone, so the interrupted login and
 * everything behind it were dropped, and the sync went on to the endpoints without them. The old
 * session arms called a bare `login()`, which resumed at the login after the failed one.
 *
 * Each case drives the real `LibrusLogin` over the logins that were pending, so the production code
 * consumes the queue and really starts the interrupted login. That login reaches a real request, which
 * is parked: the HTTP clients are swapped for one whose dispatcher drops every call, so nothing goes
 * on the network and no callback ever arrives. The error is then raised through `data.error`, with the
 * tag of the step that raises it in production, as if the parked response had come back. Recorded:
 * which logins `LibrusLogin` started before and after the error (`onStartProgress` fires only for a
 * login it actually starts, not one it skips as already done), the queue once the retried login has
 * parked or stopped, and every error that reached the task's callback (`tag/code`, with a trailing `~`
 * when it is not critical).
 *
 * The login store has no email or password, so a retried portal login stops at its first check with
 * `ERROR_LOGIN_DATA_MISSING`, before building any request.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibrusLoginRetryQueueTest {

    private val app: App
        get() = RuntimeEnvironment.getApplication() as App

    private lateinit var savedHttp: OkHttpClient
    private lateinit var savedHttpLazy: OkHttpClient
    private var savedMHttpClient: OkHttpClient? = null

    /**
     * Requests built with `withClient(app.httpLazy)` use that client. Every other one asks
     * `MHttp.instance().client()` when it is enqueued, and that getter memoises: the first client it
     * builds in a Robolectric sandbox stays in its private `mInnerClient` for good, and a later
     * `customOkHttpClient(...)` only rewrites a builder nothing reads again (see `CannedLibrusApi`).
     * So `customOkHttpClient` is no use here either way: run after another test it silently leaves
     * the production client live, and these requests would go to Librus; run first, the parked client
     * gets memoised for every later test in the sandbox, which broke `CannedLibrusApi`'s eleven tests.
     * The memoised field itself is swapped, checked, and put back exactly as it was.
     */
    @Before
    fun parkEveryRequest() {
        savedHttp = app.http
        savedHttpLazy = app.httpLazy
        val parked = OkHttpClient.Builder().dispatcher(Dispatcher(DroppingExecutor())).build()
        app.http = parked
        app.httpLazy = parked
        savedMHttpClient = mHttpClientField.get(MHttp.instance()) as OkHttpClient?
        mHttpClientField.set(MHttp.instance(), parked)
        assertSame("MHttp must hand out the parked client, or these requests reach the network", parked, MHttp.instance().client())
    }

    @After
    fun restoreClients() {
        app.http = savedHttp
        app.httpLazy = savedHttpLazy
        mHttpClientField.set(MHttp.instance(), savedMHttpClient)
    }

    @Test
    fun `a portal access denial during the API login re-runs the portal, the API login and the logins behind it`() {
        assertEquals(
            "before=[LIBRUS_API] after=[LIBRUS_PORTAL] queued=[LIBRUS_API, LIBRUS_SYNERGIA, LIBRUS_MESSAGES] " +
                "errors=[LoginLibrusPortal/$ERROR_LOGIN_DATA_MISSING] step=$STEP",
            run(
                satisfied = listOf(LIBRUS_PORTAL),
                pending = listOf(LIBRUS_API, LIBRUS_SYNERGIA, LIBRUS_MESSAGES),
                tag = "SynergiaTokenExtractor",
                errorCode = ERROR_LIBRUS_PORTAL_ACCESS_DENIED,
            ),
        )
    }

    @Test
    fun `an API access denial during the Synergia login re-runs the API login, the Synergia login and the logins behind it`() {
        val codes = listOf(ERROR_LIBRUS_API_ACCESS_DENIED, ERROR_LIBRUS_API_TOKEN_EXPIRED)
        assertEquals(
            codes.map { "$it: before=[LIBRUS_SYNERGIA] after=[LIBRUS_API] queued=[LIBRUS_SYNERGIA, LIBRUS_MESSAGES] errors=[] step=$STEP" },
            codes.map {
                "$it: " + run(
                    satisfied = listOf(LIBRUS_PORTAL, LIBRUS_API),
                    pending = listOf(LIBRUS_SYNERGIA, LIBRUS_MESSAGES),
                    tag = "LoginLibrusSynergia",
                    errorCode = it,
                )
            },
        )
    }

    @Test
    fun `a Synergia access denial during the Messages login re-runs the Synergia login and the Messages login`() {
        assertEquals(
            "before=[LIBRUS_MESSAGES] after=[LIBRUS_SYNERGIA] queued=[LIBRUS_MESSAGES] errors=[] step=$STEP",
            run(
                satisfied = listOf(LIBRUS_PORTAL, LIBRUS_API, LIBRUS_SYNERGIA),
                pending = listOf(LIBRUS_MESSAGES),
                tag = "LoginLibrusMessages",
                errorCode = ERROR_LIBRUS_SYNERGIA_ACCESS_DENIED,
            ),
        )
    }

    /** `prepareFor(LIBRUS_MESSAGES)` already re-queued the whole chain: GREEN before and after. */
    @Test
    fun `a Messages access denial during the Messages login re-runs the Messages login`() {
        assertEquals(
            "before=[LIBRUS_MESSAGES] after=[LIBRUS_MESSAGES] queued=[] errors=[] step=$STEP",
            run(
                satisfied = listOf(LIBRUS_PORTAL, LIBRUS_API, LIBRUS_SYNERGIA),
                pending = listOf(LIBRUS_MESSAGES),
                tag = "LoginLibrusMessages",
                errorCode = ERROR_LIBRUS_MESSAGES_ACCESS_DENIED,
            ),
        )
    }

    /**
     * The data phase: every login has finished, so nothing was interrupted and the queue is empty. Only
     * the denied method's chain is logged in again, as before. The logins are drained through the real
     * `LibrusLogin` (all already done, so each is skipped), so nothing it records about them may
     * survive into the retry. GREEN before and after.
     */
    @Test
    fun `an access denial with no login running re-logs only the denied method's chain`() {
        val all = listOf(LIBRUS_PORTAL, LIBRUS_API, LIBRUS_SYNERGIA, LIBRUS_MESSAGES)
        assertEquals(
            listOf(
                "before=[] after=[LIBRUS_API] queued=[] errors=[] step=$STEP",
                "before=[] after=[LIBRUS_SYNERGIA] queued=[] errors=[] step=$STEP",
            ),
            listOf(
                run(satisfied = all, pending = all, tag = "LibrusApiEvents", errorCode = ERROR_LIBRUS_API_ACCESS_DENIED),
                run(satisfied = all, pending = all, tag = "LibrusSynergiaHomework", errorCode = ERROR_LIBRUS_SYNERGIA_ACCESS_DENIED),
            ),
        )
    }

    /**
     * The two retries compose. A Synergia session error re-runs the Synergia login, and while that
     * retried login is running an API access denial arrives: the API login runs again, then the
     * retried Synergia login, which is again the interrupted one.
     */
    @Test
    fun `an access denial while a retried login runs re-queues the retried login`() {
        assertEquals(
            "before=[LIBRUS_SYNERGIA] after=[LIBRUS_API] queued=[LIBRUS_SYNERGIA] errors=[] step=$STEP",
            run(
                satisfied = listOf(LIBRUS_PORTAL, LIBRUS_API),
                pending = listOf(LIBRUS_SYNERGIA),
                tag = "LoginLibrusSynergia",
                errorCode = ERROR_LIBRUS_API_ACCESS_DENIED,
                earlier = ApiError("LoginLibrusSynergia", ERROR_LOGIN_LIBRUS_SYNERGIA_NO_SESSION_ID),
            ),
        )
    }

    /**
     * Each code is raised only by `LibrusLoginSynergia` while it runs. The old bare `login()` started
     * the Messages login instead, which needs Synergia done and stopped with `ERROR_LOGIN_DATA_MISSING`.
     */
    @Test
    fun `a Synergia session error re-runs the Synergia login before the logins behind it`() {
        val codes = listOf(
            ERROR_LOGIN_LIBRUS_SYNERGIA_TOKEN_INVALID,
            ERROR_LOGIN_LIBRUS_SYNERGIA_NO_TOKEN,
            ERROR_LOGIN_LIBRUS_SYNERGIA_NO_SESSION_ID,
        )
        assertEquals(
            codes.map { "$it: before=[LIBRUS_SYNERGIA] after=[LIBRUS_SYNERGIA] queued=[LIBRUS_MESSAGES] errors=[] step=$STEP" },
            codes.map {
                "$it: " + run(
                    satisfied = listOf(LIBRUS_PORTAL, LIBRUS_API),
                    pending = listOf(LIBRUS_SYNERGIA, LIBRUS_MESSAGES),
                    tag = "LoginLibrusSynergia",
                    errorCode = it,
                )
            },
        )
    }

    /** With nothing queued behind it, the old bare `login()` went straight to the data phase. */
    @Test
    fun `a Synergia session error with nothing queued behind it re-runs the Synergia login`() {
        assertEquals(
            "before=[LIBRUS_SYNERGIA] after=[LIBRUS_SYNERGIA] queued=[] errors=[] step=$STEP",
            run(
                satisfied = listOf(LIBRUS_PORTAL, LIBRUS_API),
                pending = listOf(LIBRUS_SYNERGIA),
                tag = "LoginLibrusSynergia",
                errorCode = ERROR_LOGIN_LIBRUS_SYNERGIA_NO_SESSION_ID,
            ),
        )
    }

    @Test
    fun `a Messages session error re-runs the Messages login`() {
        assertEquals(
            "before=[LIBRUS_MESSAGES] after=[LIBRUS_MESSAGES] queued=[] errors=[] step=$STEP",
            run(
                satisfied = listOf(LIBRUS_PORTAL, LIBRUS_API, LIBRUS_SYNERGIA),
                pending = listOf(LIBRUS_MESSAGES),
                tag = "LoginLibrusMessages",
                errorCode = ERROR_LOGIN_LIBRUS_MESSAGES_NO_SESSION_ID,
            ),
        )
    }

    /**
     * The retry is bounded: `Librus` reports a code it has already seen in this task as it is, which is
     * critical, and starts nothing. The Messages login stays queued, unrun.
     */
    @Test
    fun `a session error that repeats ends the task as a critical error`() {
        assertEquals(
            "before=[LIBRUS_SYNERGIA] after=[] queued=[LIBRUS_MESSAGES] " +
                "errors=[LoginLibrusSynergia/$ERROR_LOGIN_LIBRUS_SYNERGIA_NO_SESSION_ID] step=$STEP",
            run(
                satisfied = listOf(LIBRUS_PORTAL, LIBRUS_API),
                pending = listOf(LIBRUS_SYNERGIA, LIBRUS_MESSAGES),
                tag = "LoginLibrusSynergia",
                errorCode = ERROR_LOGIN_LIBRUS_SYNERGIA_NO_SESSION_ID,
                earlier = ApiError("LoginLibrusSynergia", ERROR_LOGIN_LIBRUS_SYNERGIA_NO_SESSION_ID),
            ),
        )
    }

    /**
     * Some arms answer an error by going straight on to the data phase. They are meant for endpoints,
     * but `LibrusLoginSynergia` extends `LibrusApi`, so its `AutoLoginToken` request can raise them while
     * the Synergia login runs: a parser exception (reported as non-critical) or a "not active" code.
     * The data phase then starts without `LibrusLogin` having run out of logins. Nothing may survive
     * from the abandoned login phase: an endpoint's API access denial must re-log the API alone, not
     * also the Synergia login that was running and the Messages login that was queued behind it.
     */
    @Test
    fun `a login error that goes straight on to the data phase leaves no login to re-run`() {
        val codes = listOf(EXCEPTION_LIBRUS_API_REQUEST, ERROR_LIBRUS_API_LUCKY_NUMBER_NOT_ACTIVE)
        assertEquals(
            listOf(
                "$EXCEPTION_LIBRUS_API_REQUEST: before=[LIBRUS_SYNERGIA] after=[LIBRUS_API] queued=[] " +
                    "errors=[LoginLibrusSynergia/$EXCEPTION_LIBRUS_API_REQUEST~] step=$STEP",
                "$ERROR_LIBRUS_API_LUCKY_NUMBER_NOT_ACTIVE: before=[LIBRUS_SYNERGIA] after=[LIBRUS_API] queued=[] " +
                    "errors=[] step=$STEP",
            ),
            codes.map {
                "$it: " + run(
                    satisfied = listOf(LIBRUS_PORTAL, LIBRUS_API),
                    pending = listOf(LIBRUS_SYNERGIA, LIBRUS_MESSAGES),
                    tag = "LibrusApiEvents",
                    errorCode = ERROR_LIBRUS_API_ACCESS_DENIED,
                    earlier = ApiError("LoginLibrusSynergia", it),
                )
            },
        )
    }

    /**
     * A fresh `Librus` whose `satisfied` logins are done and whose `pending` ones are queued. Runs the
     * real `LibrusLogin` over the queue until it parks on a request (or drains it), raises [errorCode]
     * as [tag], and reports what happened.
     */
    private fun run(
        satisfied: List<LoginMethod>,
        pending: List<LoginMethod>,
        tag: String,
        errorCode: Int,
        earlier: ApiError? = null,
    ): String {
        val started = mutableListOf<LoginMethod>()
        val errors = mutableListOf<String>()
        val callback = object : EdziennikCallback {
            override fun onCompleted() {}
            override fun onRequiresUserAction(event: UserActionRequiredEvent) {}
            override fun onProgress(step: Float) {}
            override fun onStartProgress(stringRes: Int) {
                started += LOGIN_PROGRESS.getValue(stringRes)
            }
            override fun onError(apiError: ApiError) {
                errors += "${apiError.tag}/${apiError.errorCode}" + if (apiError.isCritical) "" else "~"
            }
        }
        val store = LoginStore(L, LoginType.LIBRUS, LoginMode.LIBRUS_EMAIL, JsonObject())
        val librus = Librus(app, profile(), store, callback)
        librus.data.apiLogin = "login"
        librus.data.portalAccessToken = "token"
        librus.data.loginMethods.clear()
        librus.data.loginMethods += satisfied
        librus.data.targetLoginMethods = pending.toMutableList()
        librus.data.progressStep = STEP

        LibrusLogin(librus.data) {}
        val before = started.toList()
        earlier?.let { librus.data.error(it) }
        started.clear()

        librus.data.error(ApiError(tag, errorCode))

        return "before=$before after=$started queued=${librus.data.targetLoginMethods} errors=$errors step=${librus.data.progressStep}"
    }

    private fun profile() = Profile(
        id = P,
        loginStoreId = L,
        loginStoreType = LoginType.LIBRUS,
        name = "Requeue",
        studentData = JsonObject(),
    )

    /** Accepts every call and never runs it: the request stays in flight and no callback arrives. */
    private class DroppingExecutor : AbstractExecutorService() {
        override fun execute(command: Runnable) {}
        override fun shutdown() {}
        override fun shutdownNow(): List<Runnable> = emptyList()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }

    private companion object {
        const val P = 9062
        const val L = 9162

        /** A progress step no code path sets on its own, so a reset to 0 shows. */
        const val STEP = 5f

        /** `MHttp`'s memoised client; see [parkEveryRequest]. */
        val mHttpClientField = MHttp::class.java.getDeclaredField("mInnerClient").apply { isAccessible = true }
        val LOGIN_PROGRESS = mapOf(
            R.string.edziennik_progress_login_librus_portal to LIBRUS_PORTAL,
            R.string.edziennik_progress_login_librus_api to LIBRUS_API,
            R.string.edziennik_progress_login_librus_synergia to LIBRUS_SYNERGIA,
            R.string.edziennik_progress_login_librus_messages to LIBRUS_MESSAGES,
        )
    }
}
