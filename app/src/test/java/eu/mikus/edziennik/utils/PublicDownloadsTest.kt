/*
 * Copyright (c) Mikolaj Olszewski 2026-9-7.
 */

package eu.mikus.edziennik.utils

import android.app.Application
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric, not a plain JVM test, and deliberately so: off-Robolectric
 * `Build.VERSION.SDK_INT` has no ConstantValue in android.jar and reads 0 (forcing the legacy
 * branch always), and `Environment.DIRECTORY_DOWNLOADS` is not `final`, so under
 * `returnDefaultValues` it reads null and `File(null, "eDziennikus").path` is just "eDziennikus".
 *
 * sdk = [23, 30, 35]: 30 sits inside the broken 29-32 window and its android-all jar is already
 * cached, where 29's is not.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 30, 35])
class PublicDownloadsTest {

    @Test
    fun `the download folder path is Download eDziennikus`() {
        assertEquals("Download/eDziennikus", PublicFolder.relativePath(Environment.DIRECTORY_DOWNLOADS))
    }

    @Test
    fun `the pictures folder shares the same name, so the two cannot drift`() {
        assertEquals("Pictures/eDziennikus", PublicFolder.relativePath(Environment.DIRECTORY_PICTURES))
        assertEquals("eDziennikus", PublicFolder.NAME)
    }

    /**
     * Containment. A missing source makes `source.inputStream()` throw FileNotFoundException. If the
     * implementation omits its catch, this test fails by throwing rather than by asserting - which is
     * exactly the signal we want.
     */
    @Test
    fun `publish returns false and does not throw when the source is missing`() {
        val ctx = RuntimeEnvironment.getApplication()
        val missing = File(ctx.cacheDir, "definitely-not-here.pdf")
        assertFalse(missing.exists())
        assertFalse(PublicDownloads.publish(ctx, missing, "definitely-not-here.pdf"))
    }

    @Test
    fun `publish returns false and does not throw when the source is a directory`() {
        val ctx = RuntimeEnvironment.getApplication()
        val dir = File(ctx.cacheDir, "a-directory").apply { mkdirs() }
        assertFalse(PublicDownloads.publish(ctx, dir, "a-directory"))
    }

    /**
     * Observes what the helper actually hands the resolver. Verified against
     * shadows-framework-4.14.1.jar: `insert` records a copy of the ContentValues into
     * `insertStatements` *before* any provider lookup, so they are inspectable with no `media`
     * provider registered; and the single-arg `openOutputStream(Uri)` catches and returns a no-op
     * sink, so the write completes and `publish` returns true on 30/35.
     */
    @Test
    fun `publish lands the payload in Download eDziennikus`() {
        val ctx = RuntimeEnvironment.getApplication()
        val source = File(ctx.cacheDir, "note.txt").apply { writeText("hello") }
        assertTrue(PublicDownloads.publish(ctx, source, "note.txt"))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val shadow = shadowOf(ctx.contentResolver)
            val insert = shadow.insertStatements.last()
            assertEquals("content://media/external/downloads", insert.uri.toString())
            assertEquals("note.txt", insert.contentValues.getAsString(MediaStore.MediaColumns.DISPLAY_NAME))
            assertEquals(
                "Download/eDziennikus",
                insert.contentValues.getAsString(MediaStore.MediaColumns.RELATIVE_PATH),
            )
            assertEquals(1, insert.contentValues.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
            assertEquals(
                0,
                shadow.updateStatements.last().contentValues.getAsInteger(MediaStore.MediaColumns.IS_PENDING),
            )
            assertTrue(shadow.deleteStatements.isNotEmpty(), "the duplicate pre-delete must run")
        } else {
            @Suppress("DEPRECATION")
            val target = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "eDziennikus/note.txt",
            )
            assertTrue(target.exists(), "the sub-Q copy must land at $target")
            assertEquals("hello", target.readText())
        }
    }
}
