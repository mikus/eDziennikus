/*
 * Copyright (c) Mikolaj Olszewski 2026-5-14.
 */

package eu.mikus.edziennik.ext

import android.app.Application
import android.os.Build
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Smoke test that proves the Robolectric path is wired up correctly: if these
 * tests pass, JVM unit tests can touch the Android framework via Robolectric's
 * fakes (Application, Build, resources, …).
 *
 * Robolectric currently ships only a JUnit 4 runner — there's no first-party
 * JUnit 5 extension. We run this test under JUnit 4, and the
 * `junit-vintage-engine` on the test classpath surfaces it to the JUnit
 * Platform alongside Jupiter tests. New non-Android tests should still be
 * written as Jupiter (`org.junit.jupiter.api.Test`); reach for this pattern
 * only when the unit under test genuinely touches `android.*`.
 *
 * We use Robolectric's [RuntimeEnvironment.getApplication] rather than
 * `androidx.test.core.app.ApplicationProvider` because androidx.test artifacts
 * declare `minSdkVersion="19"` and the app's production minSdk is 16 — the
 * manifest merger rejects them. Robolectric provides the same Application
 * directly with no manifest entanglements.
 *
 * `@Config(application = Application::class)` swaps the real [eu.mikus.edziennik.App]
 * for a stock [android.app.Application]. The real App.onCreate calls
 * `System.loadLibrary("szkolny-signing")` (the native crypto / API-signing lib
 * under `app/src/main/cpp/`), which fails on the JVM with `UnsatisfiedLinkError`.
 * Tests that need the real App must either ship a JVM-buildable native lib or
 * stub `Signing` — neither is in scope for an infrastructure smoke test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RobolectricSmokeTest {

    @Test
    fun `application is available`() {
        val app: Application = RuntimeEnvironment.getApplication()
        assertNotNull("Robolectric must provide an Application", app)
        // packageName is set from the AndroidManifest under test — proves the
        // resources / manifest pipeline is wired up.
        assertNotNull("Application must expose a packageName", app.packageName)
    }

    @Test
    fun `build version sdk int is populated`() {
        // Robolectric fakes android.os.Build; without it, SDK_INT would be 0
        // (because the stub Android jar has no field values).
        assertTrue("Build.VERSION.SDK_INT must be a valid SDK level", Build.VERSION.SDK_INT > 0)
    }
}
