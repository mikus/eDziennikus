/*
 * Copyright (c) Mikolaj Olszewski 2026-11-7.
 */
package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import com.google.gson.JsonObject
import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.api.edziennik.librus.DataLibrus
import eu.mikus.edziennik.data.api.edziennik.librus.Librus
import eu.mikus.edziennik.data.api.events.UserActionRequiredEvent
import eu.mikus.edziennik.data.api.interfaces.EdziennikCallback
import eu.mikus.edziennik.data.api.models.ApiError
import eu.mikus.edziennik.data.db.entity.LoginStore
import eu.mikus.edziennik.data.db.enums.LoginMode
import eu.mikus.edziennik.data.db.enums.LoginType
import im.wangchao.mhttp.MHttp
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs a real Librus parser's `init { apiGet(…) { … } }` body over a canned response, on the JVM,
 * with no network.
 *
 * All 34 live parsers under `librus/data/api/` build their rows inside that block - the 35th,
 * `LibrusApiTemplate`, is a commented-out stub. The handler is a lambda captured in the constructor
 * of a `final class`, so there is no seam to call it through, which is why the extracted helpers
 * (`librusLessonRow`, `librusEventTeamId`, …) are pinned by tests while **nothing pins that a parser
 * calls them**. Reverting `LibrusApiLessons` to the inline `Class`-only read it used before item 82
 * left the whole suite green - 1238 of 1238, `LibrusLessonRowTest` among them - with the defect
 * back. This class closes that hole for the parsers that use it, by driving the production object
 * end to end and asserting the rows it stored.
 *
 * `App.buildHttp()` ends in `MHttp.instance().customOkHttpClient(http)`, so the client mhttp issues
 * every Librus request through is globally replaceable. [serve] registers a body per endpoint and an
 * `Interceptor` answers from that map, so no socket is ever opened.
 *
 * Three things about `MHttp` shape this class, each of which silently produces a *passing* test when
 * got wrong:
 *
 * - **`client()` memoises.** It is `if (mInnerClient == null) mInnerClient = mOkBuilder.build()`, so
 *   the first client built in a Robolectric sandbox wins **permanently**; `customOkHttpClient` after
 *   that only rewrites a builder nothing reads again. [install] therefore forces the build
 *   immediately and [assertIntercepting] checks our own interceptor is really in the live client. A
 *   test class that ran earlier in the same sandbox and issued a real request would otherwise leave
 *   the production client memoised, and these tests would **reach the network** instead of failing.
 * - **So a second install is a no-op**, and bodies must be routed by request path rather than by
 *   re-installing per test. Served by endpoint, a `/Lessons` parser handed a `HomeWorks` body reads
 *   `getJsonArray("Lessons")` as null, loops over nothing, and still writes its timer and calls
 *   `onSuccess`: zero rows, zero errors, a successful-looking run. That is how this was first
 *   written, and it passed.
 * - **An unregistered path must fail loudly**, for the same reason, so the interceptor throws rather
 *   than answering empty.
 *
 * **The parser's callback does not run on a looper.** `LibrusApi.apiGet` sets no
 * `callbackThreadMode`/`callbackExecutor`, so mhttp keeps its default `ThreadMode.BACKGROUND`, whose
 * executor runs the runnable inline - which means the whole handler, and every row it stores, runs on
 * the OkHttp dispatcher thread. Measured by printing `Thread.currentThread().name` from inside the
 * continuation: `OkHttp Dispatcher`, against `SDK 35 Main Thread` in the test. [drive] therefore hands
 * results across a thread boundary and needs a real happens-before edge, which is what the
 * [CountDownLatch] gives it: everything the OkHttp thread did before `countDown` is visible to the
 * test thread once `await` returns. An earlier revision of this KDoc claimed the callback arrived on
 * the Robolectric main looper and spun on an unsynchronised field; both were wrong.
 *
 * The main looper is still idled once afterwards, cheaply, for anything the chain posts to it.
 *
 * The profile is null, which keeps `Data`'s init and `saveData` off the database, exactly as
 * `LibrusParserErrorRecoveryTest` and `LibrusApiUnitsNoUnitTest` already rely on. `profileId` is
 * then `-1`.
 *
 * Construct one per test: the constructor clears the route table, so a body registered by an earlier
 * test cannot answer a later one.
 */
internal class CannedLibrusApi {

    private companion object {
        /** Endpoint (last path segment) to canned body. Static because the client that reads it is. */
        val bodies = mutableMapOf<String, String>()

        /** Held so [assertIntercepting] can prove the live client is still ours. */
        val interceptor = Interceptor { chain ->
            val path = chain.request().url().encodedPath().substringAfterLast('/')
            val body = bodies[path] ?: throw IOException(
                "CannedLibrusApi: nothing registered for /$path; registered: ${bodies.keys}"
            )
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(ResponseBody.create(MediaType.parse("application/json"), body))
                .build()
        }

        var installed = false

        /** No real endpoint id, so an unreported run cannot be mistaken for a reported one. */
        const val NOTHING_REPORTED = Int.MIN_VALUE

        /**
         * Installs the canned client and forces `MHttp` to memoise it, winning the race described
         * in the class KDoc. Idempotent, because a second install could not take effect anyway.
         */
        fun install() {
            if (installed) return
            installed = true
            MHttp.instance().customOkHttpClient(
                OkHttpClient.Builder().addInterceptor(interceptor).build()
            )
            MHttp.instance().client()
        }
    }

    private val app = RuntimeEnvironment.getApplication() as App

    /**
     * Errors the parser reported. A canned run that produces any has not done what it was asked.
     * Written on the OkHttp thread, read by the test thread only after [done] has been awaited.
     */
    val errors = mutableListOf<ApiError>()

    /** Counted down by whichever of onSuccess / onError the parser reaches first; see [drive]. */
    private val done = CountDownLatch(1)

    private val callback = object : EdziennikCallback {
        override fun onCompleted() {}
        override fun onRequiresUserAction(event: UserActionRequiredEvent) {}
        override fun onError(apiError: ApiError) { errors += apiError; done.countDown() }
        override fun onProgress(step: Float) {}
        override fun onStartProgress(stringRes: Int) {}
    }

    /** The production `DataLibrus` the parser writes its rows into. */
    val data: DataLibrus = Librus(
        app,
        null,
        LoginStore(1, LoginType.LIBRUS, LoginMode.LIBRUS_EMAIL, JsonObject()),
        callback,
    ).data

    init {
        bodies.clear()
        install()
        assertIntercepting()
    }

    /**
     * Fails if the live `MHttp` client is not the canned one - see the class KDoc. Without this a
     * sandbox that memoised the production client would send these requests to Librus for real.
     */
    private fun assertIntercepting() {
        assertTrue(
            "the canned interceptor must be in MHttp's live client, or this test hits the network",
            MHttp.instance().client().interceptors().contains(interceptor),
        )
    }

    /** Registers [body] as the response to the endpoint whose last path segment is [endpoint]. */
    fun serve(endpoint: String, body: String) {
        bodies[endpoint] = body
    }

    /**
     * Builds the parser and returns the endpoint id it reported to its `onSuccess`.
     *
     * [build] receives [data] and the continuation to hand the parser, so the call site reads as the
     * production construction it is: `drive { data, onSuccess -> LibrusApiX(data, null, onSuccess) }`.
     */
    fun drive(build: (DataLibrus, (Int) -> Unit) -> Unit): Int {
        val reported = AtomicInteger(NOTHING_REPORTED)
        build(data) { reported.set(it); done.countDown() }

        val finished = done.await(10, TimeUnit.SECONDS)
        shadowOf(app.mainLooper).idle()

        assertEquals(
            "a canned run must report no error",
            emptyList<String>(),
            errors.map { "${it.errorCode}: ${it.throwable?.message}" },
        )
        if (!finished) fail("the parser reached neither onSuccess nor onError within 10 s")
        val id = reported.get()
        if (id == NOTHING_REPORTED) fail("the parser finished without reporting an endpoint id")
        return id
    }
}
