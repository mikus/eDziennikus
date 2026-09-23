/*
 * Copyright (c) Mikolaj Olszewski 2026-9-23.
 */
package eu.mikus.edziennik.ui

import android.app.Application
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.LinearLayout
import eu.mikus.edziennik.R
import eu.mikus.edziennik.databinding.NoteListDialogBinding
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The only automated guard this repo has ever had against a layout that no longer inflates.
 *
 * Phase 45 strips the `<layout>` wrapper from 50 files. That breakage is compile-gated, so this is
 * NOT the gate for it — it guards what the compiler cannot see: an id that stops resolving, or a
 * root element that stops constructing.
 *
 * **Discovery reads the layout DIRECTORY, not `R.layout`.** `gradle.properties:11` sets
 * `android.nonTransitiveRClass=false`, so `R.layout` carries 290 symbols — 89 ours and 201 from
 * AppCompat/Material/Chucker/etc, 14 of which are `<merge>` roots that cannot be inflated
 * standalone. Reflecting over `R.layout` would make this test red on arrival and would be testing
 * other people's layouts.
 *
 * `<merge>` roots are detected by reading the file, not from a hardcoded list, so the set cannot go
 * stale — this phase itself deletes one of them.
 *
 * **What this catches, demonstrated rather than asserted.** On its first run it failed on 31 named
 * layouts (`Error inflating class androidx.appcompat.widget.Toolbar` and similar) because the bare
 * application context resolves no `?selectableItemBackground`; it went green once the context was
 * themed. That observed red-to-green on a real cause is the evidence that the assertions bite.
 *
 * **What it does NOT catch, measured 2026-09-23:** removing a required `android:layout_width` — the
 * inflater falls back to defaults rather than throwing. Two synthetic mutations were tried and both
 * proved unusable: an unresolvable root class is a *compile* break (DataBinding's generated
 * `*BindingImpl` names the root type), and the missing-width case above is simply invisible. So this
 * guards "the layout still constructs and its ids still resolve" — not "the layout still looks
 * right". The cue-level regressions this phase can cause are covered by `LessonCuesTest`, not here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "en-rUS-notnight-xhdpi")
class LayoutInflationTest {

    /**
     * Themed deliberately. Measured 2026-09-23: inflating against the bare application context fails
     * for 31 of the 89 layouts with `Error inflating class androidx.appcompat.widget.Toolbar` and
     * friends, because attributes like `?selectableItemBackground` resolve only under an
     * AppCompat/Material3 theme. `AppTheme.Dark` is what `AndroidManifest.xml:30` gives the
     * application, so this reproduces production rather than inventing a test-only theme.
     *
     * The alternative — excluding the 31 — would have gutted the guard across exactly the
     * widget-heavy layouts most likely to break.
     */
    private val context: android.content.Context
        get() = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.AppTheme_Dark)

    private val layoutDir = File("src/main/res/layout")

    /** Layouts excluded with a reason. Empty unless a spike proves one cannot inflate under Robolectric. */
    private val excluded = emptyMap<String, String>()

    @Test
    fun `every app layout inflates`() {
        assertTrue(
            "layout dir not found at ${layoutDir.absolutePath} — the test's working directory is not the module root",
            layoutDir.isDirectory,
        )
        val names = layoutDir.listFiles { f -> f.name.endsWith(".xml") }!!
            .map { it.name.removeSuffix(".xml") }
            .sorted()
        assertTrue("no layouts discovered under ${layoutDir.absolutePath}", names.isNotEmpty())

        val inflater = LayoutInflater.from(context)
        val failures = mutableListOf<String>()
        var seen = 0
        for (name in names) {
            if (name in excluded) { seen++; continue }
            val id = context.resources.getIdentifier(name, "layout", context.packageName)
            if (id == 0) { failures += "$name: no R.layout id"; seen++; continue }
            try {
                // a <merge> cannot be inflated standalone; it needs a parent and attachToRoot=true
                if (File(layoutDir, "$name.xml").readText().contains("<merge")) {
                    val parent = LinearLayout(context)
                    inflater.inflate(id, parent, true)
                    assertTrue("$name inflated no children", parent.childCount > 0)
                } else {
                    assertNotNull("$name inflated to null", inflater.inflate(id, null, false))
                }
            } catch (e: Throwable) {
                failures += "$name: ${e::class.simpleName}: ${e.message}"
            }
            seen++
        }
        // exact, not a magic floor: catches empty discovery AND a silently skipped layout
        assertEquals("discovered ${names.size} layouts but only visited $seen", names.size, seen)
        assertTrue("layouts failed to inflate:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * Inflating through the generated BINDING, not through LayoutInflater — because those catch
     * different bugs.
     *
     * `note_list_dialog` includes `note_dialog_header` **with an id**. ViewBinding's generated
     * `bind()` resolves that id with `findViewById`, so the included layout needs a real root view;
     * a `<merge>` leaves nothing to carry the id and `bind()` throws
     * `Missing required view with ID: .../header`.
     *
     * Phase 45 shipped exactly that crash. A synthetic probe had shown ViewBinding *generates* the
     * `header` field — true, and taken as proof the include worked. Generation is not resolution,
     * and the layout inflates perfectly well on its own, so the test above stayed green. Only going
     * through the binding reproduces it.
     */
    @Test
    fun `note list dialog binds its id-bearing header include`() {
        val b = NoteListDialogBinding.inflate(LayoutInflater.from(context))
        assertNotNull("the header include did not bind", b.header)
        assertNotNull("the header's title did not bind", b.header.title)
    }
}
