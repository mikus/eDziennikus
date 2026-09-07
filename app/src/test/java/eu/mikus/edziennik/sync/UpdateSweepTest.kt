/*
 * Copyright (c) Mikolaj Olszewski 2026-9-7.
 */

package eu.mikus.edziennik.sync

import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class UpdateSweepTest {

    @Test
    fun `the sweep removes stale apks`(@TempDir dir: File) {
        val apk = File(dir, "2026.05.0.apk").apply { writeText("x") }
        UpdateDownloaderService.deleteStaleApks(dir)
        assertFalse(apk.exists(), "a stale apk should be swept")
    }

    @Test
    fun `the sweep spares the attachments subdirectory and its contents`(@TempDir dir: File) {
        val attachments = File(dir, "attachments").apply { mkdirs() }
        val payload = File(attachments, "homework.pdf").apply { writeText("x") }
        val sidecar = File(attachments, ".4_1_2928135043028").apply { writeText("homework.pdf") }

        UpdateDownloaderService.deleteStaleApks(dir)

        assertTrue(attachments.isDirectory, "the attachments directory must survive an update")
        assertTrue(payload.exists(), "a downloaded attachment must survive an update")
        assertTrue(sidecar.exists(), "the sidecar must survive an update")
    }

    @Test
    fun `the sweep spares unrelated files`(@TempDir dir: File) {
        val other = File(dir, "notes.txt").apply { writeText("x") }
        UpdateDownloaderService.deleteStaleApks(dir)
        assertTrue(other.exists(), "the sweep must not be a blanket delete")
    }
}
