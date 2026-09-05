/*
 * Copyright (c) Mikolaj Olszewski 2026-9-5.
 */

package eu.mikus.edziennik.theme

import android.app.Application
import android.content.res.Resources
import android.util.TypedValue
import eu.mikus.edziennik.R
import java.io.File
import java.util.Locale
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Resolves every theme attribute under every `AppTheme*` style and asserts the result against a
 * checked-in golden dump. This is Phase 33's primary gate.
 *
 * The commit that prepares the reparent passes only if it adds rows and changes none. The commit
 * that actually reparents the themes is allowed to change rows, but every changed row has to be
 * dispositioned by hand before the updated golden is accepted.
 *
 * Coverage is exactly what `R.style` exposes under a name starting with `AppTheme` — today the 20
 * activity themes plus the two MaterialAlertDialog theme overlays and their three text styles. A
 * theme named anything else is invisible to this gate.
 *
 * Regeneration is never silent: `-Dtheme.probe.regenerate=true` rewrites the golden and then fails,
 * so a new dump becomes the expectation only once someone has read the diff and re-run without the
 * flag. The dump is a snapshot of one pinned resource configuration (see `@Config`), so it moves
 * only when the themes do.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "en-rUS-notnight-xhdpi")
class ThemeAttrProbe {

    private fun attrs(): List<Pair<String, Int>> {
        val out = sortedMapOf<String, Int>()
        for (cls in listOf(
            android.R.attr::class.java,
            androidx.appcompat.R.attr::class.java,
            com.google.android.material.R.attr::class.java,
            R.attr::class.java,
        )) {
            val prefix = if (cls == android.R.attr::class.java) "android:" else ""
            for (f in cls.fields) {
                if (f.type != Int::class.javaPrimitiveType) continue
                runCatching { out[prefix + f.name] = f.getInt(null) }
            }
        }
        return out.toList()
    }

    /** Every `R.style` field whose name starts with `AppTheme`, found reflectively, not listed. */
    private fun themes(): List<Pair<String, Int>> = R.style::class.java.fields
        .filter { it.name.startsWith("AppTheme") && it.type == Int::class.javaPrimitiveType }
        .map { it.name to it.getInt(null) }
        .sortedBy { it.first }

    private fun render(res: Resources, tv: TypedValue): String = when {
        tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT ->
            "#%08x".format(tv.data)
        tv.type == TypedValue.TYPE_INT_BOOLEAN -> "bool=${tv.data != 0}"
        tv.type == TypedValue.TYPE_DIMENSION -> {
            // complexToFloat drops the unit bits, so 56dp/56sp/56px all render alike — keep the
            // raw unit alongside the magnitude, or a unit-only change would be invisible.
            val unit = (tv.data shr TypedValue.COMPLEX_UNIT_SHIFT) and TypedValue.COMPLEX_UNIT_MASK
            String.format(Locale.ROOT, "dim=%.4f/u%d", TypedValue.complexToFloat(tv.data), unit)
        }
        tv.type == TypedValue.TYPE_FLOAT -> "float=${java.lang.Float.intBitsToFloat(tv.data)}"
        tv.type >= TypedValue.TYPE_FIRST_INT && tv.type <= TypedValue.TYPE_LAST_INT -> "int=${tv.data}"
        tv.resourceId != 0 -> runCatching { res.getResourceName(tv.resourceId) }.getOrNull()
            ?: "res=0x%08x".format(tv.resourceId)
        tv.string != null -> "str=${tv.string}"
        else -> "type=${tv.type} data=0x%08x".format(tv.data)
    }

    private fun dump(): String {
        val app = RuntimeEnvironment.getApplication()
        val all = attrs()
        val sb = StringBuilder()
        for ((themeName, themeRes) in themes()) {
            val theme = app.resources.newTheme().apply { applyStyle(themeRes, true) }
            val tv = TypedValue()
            for ((name, id) in all) {
                if (id == 0) continue
                if (!theme.resolveAttribute(id, tv, true)) continue
                sb.append(themeName).append('\t').append(name).append('\t')
                    .append(render(app.resources, tv)).append('\n')
            }
        }
        return sb.toString()
    }

    @Test
    fun themeAttributesMatchGolden() {
        // Floor on the reflected theme count: a probe that reflects nothing would otherwise be
        // free to pin an empty golden and pass for ever after.
        check(themes().size >= 25) { "only ${themes().size} themes reflected" }

        val actual = dump()
        val golden = File("src/test/resources/theme-attrs-golden.txt")
        val delta = File("build/theme-attrs-delta.txt")

        if (System.getProperty("theme.probe.regenerate") == "true") {
            golden.parentFile.mkdirs()
            golden.writeText(actual)
            fail("Regenerated golden (${actual.lineSequence().count { it.isNotBlank() }} rows) at " +
                "${golden.absolutePath}. Review the diff, then re-run without the flag.")
        }
        if (!golden.exists()) {
            fail("Golden missing at ${golden.absolutePath} (cwd=${File(".").absolutePath}). " +
                "Regenerate with -Dtheme.probe.regenerate=true.")
        }

        val expected = golden.readText()
        if (expected == actual) {
            // A leftover delta from an earlier failing run would otherwise read as current.
            delta.delete()
            return
        }

        fun toMap(s: String) = s.lineSequence().filter { it.isNotBlank() }
            .associate { it.substringBeforeLast('\t') to it.substringAfterLast('\t') }
        val expMap = toMap(expected)
        val actMap = toMap(actual)
        val added = (actMap.keys - expMap.keys).sorted()
        val removed = (expMap.keys - actMap.keys).sorted()
        val changed = (expMap.keys intersect actMap.keys).filter { expMap[it] != actMap[it] }.sorted()

        delta.parentFile.mkdirs()
        delta.writeText(buildString {
            changed.forEach {
                append("~ ").append(it).append('\t')
                    .append(expMap[it]).append(" -> ").append(actMap[it]).append('\n')
            }
            removed.forEach { append("- ").append(it).append('\t').append(expMap[it]).append('\n') }
            added.forEach { append("+ ").append(it).append('\t').append(actMap[it]).append('\n') }
        })
        fail(
            "Theme attributes changed: ${changed.size} changed, ${removed.size} removed, " +
                "${added.size} added. Full delta at app/build/theme-attrs-delta.txt. If intended, " +
                "re-run with -Dtheme.probe.regenerate=true and review the golden diff."
        )
    }
}
