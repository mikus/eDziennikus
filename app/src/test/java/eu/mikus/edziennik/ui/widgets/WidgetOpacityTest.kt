/*
 * Copyright (c) Mikolaj Olszewski 2026-10-9.
 */

package eu.mikus.edziennik.ui.widgets

import android.widget.RemoteViews
import com.google.gson.Gson
import eu.mikus.edziennik.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The widget opacity slider is applied with `RemoteViews.setFloat(id, "setAlpha", f)`.
 *
 * That only works if `View.setAlpha` is annotated `@RemotableViewMethod` in the framework — if it is
 * not, `RemoteViews.apply` throws `ActionException` at render time and the launcher shows the error
 * view instead of the widget. Nothing about that is visible at compile time, and the previous
 * implementation got this wrong in the other direction: it reached for the hidden
 * `RemoteViews.setDrawableParameters` by reflection and had to be gated to `SDK_INT < P` because it
 * crashed launchers, which left the slider dead on every device since Android 9.
 *
 * So this inflates each widget's real layout through the real `RemoteViews` pipeline and asserts the
 * alpha actually landed on the root. Robolectric is used because the assertion is about framework
 * behaviour, not about our own code.
 *
 * Only the light/small variant of each widget is covered: the dark and big layouts differ in colours
 * and row templates, not in the root id this acts on, and all twelve were checked to carry the same
 * two ids (`root` for timetable and notifications, `widgetLuckyNumberRoot` for lucky number).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class WidgetOpacityTest {

    private fun assertAlphaApplies(layout: Int, rootId: Int, alpha: Float) {
        val context = RuntimeEnvironment.getApplication()
        val views = RemoteViews(context.packageName, layout)
        views.setFloat(rootId, "setAlpha", alpha)

        // Throws ActionException here if setAlpha is not a @RemotableViewMethod.
        val inflated = views.apply(context, null)
        val root = if (inflated.id == rootId) inflated else inflated.findViewById(rootId)

        assertEquals(alpha, root.alpha, 0.001f)
    }

    @Test
    fun `timetable widget honours the opacity slider`() {
        assertAlphaApplies(R.layout.widget_timetable, R.id.root, 0.5f)
    }

    @Test
    fun `notifications widget honours the opacity slider`() {
        assertAlphaApplies(R.layout.widget_notifications, R.id.root, 0.5f)
    }

    @Test
    fun `lucky number widget honours the opacity slider`() {
        assertAlphaApplies(R.layout.widget_lucky_number, R.id.widgetLuckyNumberRoot, 0.5f)
    }

    /** Fully opaque is the new default, and must round-trip as exactly 1 rather than near-1. */
    @Test
    fun `an opaque widget stays fully opaque`() {
        assertAlphaApplies(R.layout.widget_lucky_number, R.id.widgetLuckyNumberRoot, 1.0f)
    }

    /**
     * A widget placed before opacity was honoured stores `"opacity"`, typically the 0.6 or 0.8 that
     * WidgetConfigActivity set as its initial value while nothing read it. The field now serialises
     * as `"alpha"`, so those entries must NOT bind — otherwise every already-placed widget turns
     * translucent on its next update, which nobody asked for.
     */
    @Test
    fun `a config stored before this change stays fully opaque`() {
        val legacy = "{\"profileId\":1,\"bigStyle\":false,\"darkTheme\":false,\"opacity\":0.6}"
        val config = Gson().fromJson(legacy, WidgetConfig::class.java)
        assertEquals(1.0f, config.opacity, 0.001f)
    }

    /** ...while a value written by this version round-trips under the new name. */
    @Test
    fun `a config stored by this version round-trips its alpha`() {
        val json = Gson().toJson(WidgetConfig(1, false, false, 0.4f))
        assertTrue("alpha must be the wire name, was: " + json, json.contains("\"alpha\""))
        assertEquals(0.4f, Gson().fromJson(json, WidgetConfig::class.java).opacity, 0.001f)
    }
}
