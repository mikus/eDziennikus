/*
 * Copyright (c) Mikolaj Olszewski 2026-9-22.
 */
package eu.mikus.edziennik

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Pins that the shared OkHttp client bounds a whole call, not just one read.
 *
 * Measured 2026-09-22 against a real profile: a healthy endpoint on a GPRS-grade connection goes
 * silent for up to 30.1 s, which is exactly `readTimeout`. Past it the read throws and that becomes
 * `onError`, so a stalled endpoint already self-terminates. What is NOT bounded without a
 * `callTimeout` is a socket that keeps trickling bytes - each byte rearms the read clock - and
 * retries, because `retryOnConnectionFailure(true)` is on. Either leaves a sync running with no
 * ceiling at all, which is the hang this bounds.
 *
 * 90 s is evidenced rather than picked: 3x the measured worst legitimate silence, and clear of two
 * stacked 30 s read timeouts. Anything at or below 60 s would cut a healthy GPRS sync short.
 *
 * Boots the real [App], as [eu.mikus.edziennik.data.api.ApiServiceTerminalEventTest] does, because
 * the point is to assert what `App.buildHttp()` actually produces rather than a copy of it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppHttpTimeoutTest {

    private val app get() = RuntimeEnvironment.getApplication() as App

    @Test fun `the shared client bounds the whole call`() {
        assertEquals(90_000, app.http.callTimeoutMillis())
    }

    /** `httpLazy` is `http.newBuilder()`, so it must inherit the ceiling - a redirect-following
     *  variant that could hang forever would defeat the point. */
    @Test fun `the no-redirect client inherits the ceiling`() {
        assertEquals(90_000, app.httpLazy.callTimeoutMillis())
    }

    /**
     * Pins the *dependency's* contract, not ours, and is scoped accordingly.
     *
     * This test shortens the clock itself, so it passes whether or not `buildHttp()` sets a
     * `callTimeout` - it cannot and does not guard the production value. The two tests above do
     * that. What this one guards is that `callTimeout` still bounds a stalled read in the pinned
     * OkHttp 3.12.13, on a client carrying every production setting (`retryOnConnectionFailure`,
     * the cookie jar, the TLS config) rather than a rebuilt copy. The version is pinned `strictly`,
     * so a future bump is exactly when this would matter.
     *
     * Deliberately NOT asserting the production 90 s ceiling behaviourally: that test would take
     * 90 s. The gap is that nothing here proves the real client ends a real stall - only that the
     * mechanism works and that the value is set.
     */
    @Test fun `okhttp still bounds a stalled read by callTimeout`() {
        val server = ServerSocket(0)
        val accepted = thread { runCatching { server.accept() } }
        try {
            val client = app.http.newBuilder()
                .callTimeout(300, TimeUnit.MILLISECONDS)
                .build()
            val request = Request.Builder().url("http://127.0.0.1:${server.localPort}/").build()
            val start = System.currentTimeMillis()
            val thrown = runCatching { client.newCall(request).execute() }.exceptionOrNull()
            val elapsed = System.currentTimeMillis() - start
            assertNotNull("the call must not succeed against a silent server", thrown)
            assertTrue("callTimeout must end it; took ${elapsed}ms", elapsed < 5_000)
        } finally {
            server.close()
            accepted.join(2_000)
        }
    }
}
