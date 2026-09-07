/*
 * Copyright (c) Mikolaj Olszewski 2026-9-7.
 */

package eu.mikus.edziennik.utils

import android.app.Application
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The gate for Phase 35: the attachment store must be the same private subdirectory on every API
 * level - no version branch, and not the `getExternalFilesDir(null)` root that
 * `UpdateDownloaderService` sweeps.
 *
 * sdk = [23, 30, 35]: **30 is the point** - it sits inside the broken 29-32 window that today's
 * TIRAMISU guard gets wrong. 29 itself is not pinned only because Robolectric has no cached
 * android-all jar for it, where 30's is already present.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 30, 35])
class StorageDirTest {

    /** `Utils.storageDir` is a memoized static and Robolectric reuses the JVM across classes. */
    @Before
    fun clearMemoizedStorageDir() {
        Utils::class.java.getDeclaredField("storageDir").apply {
            isAccessible = true
            set(null, null)
        }
    }

    @Test
    fun `the store is the private attachments subdirectory on every API level`() {
        val ctx = RuntimeEnvironment.getApplication()
        Utils.initializeStorageDir(ctx)
        assertEquals(File(ctx.getExternalFilesDir(null), "attachments"), Utils.getStorageDir())
    }

    @Test
    fun `the store is not the swept parent directory`() {
        val ctx = RuntimeEnvironment.getApplication()
        Utils.initializeStorageDir(ctx)
        assertNotEquals(ctx.getExternalFilesDir(null), Utils.getStorageDir())
    }

    @Test
    fun `the store is never inside public external storage`() {
        val ctx = RuntimeEnvironment.getApplication()
        Utils.initializeStorageDir(ctx)
        val path = Utils.getStorageDir().absolutePath
        assertTrue(
            path.startsWith(ctx.getExternalFilesDir(null)!!.absolutePath),
            "expected a private path, got $path",
        )
    }

    @Test
    fun `the store exists after initialization`() {
        val ctx = RuntimeEnvironment.getApplication()
        Utils.initializeStorageDir(ctx)
        assertTrue(Utils.getStorageDir().isDirectory)
    }
}
