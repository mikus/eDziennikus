/*
 * Copyright (c) Mikolaj Olszewski 2026-9-7.
 */

package eu.mikus.edziennik.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File

/**
 * The app's user-visible folder inside the shared collections. One constant for both
 * `Download/eDziennikus` and `Pictures/eDziennikus`, so the two cannot drift.
 */
object PublicFolder {
    const val NAME = "eDziennikus"

    /** e.g. `"Download/eDziennikus"` - the shape `MediaStore`'s `RELATIVE_PATH` wants. */
    fun relativePath(collection: String): String = File(collection, NAME).path
}

/**
 * Copies an already-stored attachment into the user's Downloads under [PublicFolder.NAME], so it
 * shows up in a file manager as well as inside the app.
 *
 * **Best-effort by contract: every failure is contained and returned as `false`; nothing throws.**
 * That is load-bearing rather than defensive. The only live caller runs inside `sandboxGetFile`'s
 * catch-all (`LibrusMessages.kt:261-276`), which turns an escaping throwable into a user-visible
 * `EXCEPTION_LIBRUS_MESSAGES_FILE_REQUEST` - indistinguishable from the download itself failing,
 * which is the defect this phase exists to remove. Hence [Throwable], not [Exception].
 */
object PublicDownloads {

    private const val TAG = "PublicDownloads"

    fun publish(context: Context, source: File, displayName: String): Boolean =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                publishViaMediaStore(context, source, displayName)
            } else {
                publishByCopy(source, displayName)
            }
        } catch (t: Throwable) {
            Utils.d(TAG, "publish failed for $displayName: $t")
            false
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun publishViaMediaStore(context: Context, source: File, displayName: String): Boolean {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val relative = PublicFolder.relativePath(Environment.DIRECTORY_DOWNLOADS)

        // MediaStore renames a colliding DISPLAY_NAME to "note (1).txt", so re-downloading the same
        // attachment would otherwise pile up copies in the user's Downloads. Drop the previous row
        // first. Both trailing-slash forms are tried because MediaStore stores RELATIVE_PATH with
        // one and accepts it without.
        for (path in listOf("$relative/", relative)) {
            resolver.delete(
                collection,
                "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                arrayOf(path, displayName),
            )
        }

        // IS_PENDING hides the row until the bytes are all there, so no other app sees a truncated
        // file.
        val pending = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, pending) ?: return false

        val wrote = resolver.openOutputStream(uri)?.use { out ->
            source.inputStream().use { it.copyTo(out) }
            true
        } ?: false

        if (!wrote) {
            resolver.delete(uri, null, null)
            return false
        }

        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null,
            null,
        )
        return true
    }

    private fun publishByCopy(source: File, displayName: String): Boolean {
        @Suppress("DEPRECATION")
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(downloads, PublicFolder.NAME)
        if (!dir.isDirectory && !dir.mkdirs()) return false
        source.inputStream().use { input ->
            File(dir, displayName).outputStream().use { input.copyTo(it) }
        }
        return true
    }
}
