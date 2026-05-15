/*
 * Copyright (c) Kuba Szczodrzyński 2022-10-22.
 */

package eu.mikus.edziennik.utils.managers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.greenrobot.eventbus.EventBus
import eu.mikus.edziennik.App
import eu.mikus.edziennik.BuildConfig
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.api.models.Update
import eu.mikus.edziennik.data.api.task.PostNotifications
import eu.mikus.edziennik.data.db.entity.Notification
import eu.mikus.edziennik.data.db.enums.NotificationType
import eu.mikus.edziennik.ext.concat
import eu.mikus.edziennik.ext.getBoolean
import eu.mikus.edziennik.ext.getJsonArray
import eu.mikus.edziennik.ext.getString
import eu.mikus.edziennik.ext.resolveString
import eu.mikus.edziennik.ext.toJsonObject
import eu.mikus.edziennik.utils.html.BetterHtml
import kotlin.coroutines.CoroutineContext

class UpdateManager(val app: App) : CoroutineScope {
    companion object {
        private const val TAG = "UpdateManager"

        /**
         * GitHub Releases API endpoint for the fork. Returns the most recent
         * non-prerelease published release as JSON. Documented at:
         * https://docs.github.com/en/rest/releases/releases#get-the-latest-release
         */
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/mikus/eDziennikus/releases/latest"
    }

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Default

    /**
     * Check for updates by querying the fork's GitHub Releases endpoint.
     *
     * The [maxChannel] parameter is kept for API compatibility with the
     * previous szkolny.eu-backed implementation but no longer affects the
     * lookup — GitHub's `/latest` endpoint only returns stable releases,
     * and the fork does not yet publish pre-release channels separately.
     *
     * Optionally posts a notification if [notify] is true.
     *
     * @return [Result] containing a newer update, or null if not available
     */
    suspend fun checkNow(
        maxChannel: Update.Type,
        notify: Boolean,
    ): Result<Update?> = withContext(Dispatchers.IO) {
        return@withContext checkNowSync(maxChannel, notify)
    }

    /**
     * Synchronous variant of [checkNow]. Must be called off the main thread.
     */
    fun checkNowSync(
        @Suppress("UNUSED_PARAMETER") maxChannel: Update.Type,
        notify: Boolean,
    ): Result<Update?> = runCatching {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github.v3+json")
            .build()

        val response = app.http.newCall(request).execute()
        if (response.code() != 200)
            return@runCatching null

        val json = response.body()?.string()?.toJsonObject()
            ?: return@runCatching null

        parseGitHubRelease(json)
    }.fold(
        onSuccess = { Result.success(process(it, notify)) },
        onFailure = { Result.failure(it) },
    )

    /**
     * Map a GitHub Releases JSON payload to the [Update] DTO. Returns null
     * when required fields are missing or malformed.
     *
     * Mapping:
     * - `tag_name` (e.g. "v2026.05.0") → versionName / versionCode
     * - `prerelease` boolean           → releaseType BETA / RELEASE
     * - `body`                         → releaseNotes (raw markdown)
     * - First `.apk` asset             → downloadUrl
     */
    private fun parseGitHubRelease(json: com.google.gson.JsonObject): Update? {
        val tagName = json.getString("tag_name") ?: return null
        val versionName = tagName.removePrefix("v")
        val versionCode = versionCodeFromName(versionName) ?: return null

        val isPrerelease = json.getBoolean("prerelease", false)
        val releaseType =
            if (isPrerelease) Update.Type.BETA.name else Update.Type.RELEASE.name

        val publishedAt = json.getString("published_at") ?: ""
        val releaseNotes = json.getString("body")

        val downloadUrl = json.getJsonArray("assets")?.firstOrNull { asset ->
            asset.asJsonObject?.getString("name")?.endsWith(".apk") == true
        }?.asJsonObject?.getString("browser_download_url")

        return Update(
            versionCode = versionCode,
            versionName = versionName,
            releaseDate = publishedAt,
            releaseNotes = releaseNotes,
            releaseType = releaseType,
            isOnGooglePlay = false,
            downloadUrl = downloadUrl,
            updateMandatory = false,
        )
    }

    /**
     * Derive [BuildConfig.VERSION_CODE]-comparable integer from a
     * calendar-versioned name (`YYYY.MM.patch`). Returns null on malformed
     * input so the caller treats the release as "no update available"
     * rather than promoting a garbage versionCode.
     *
     * Scheme: year * 10000 + month * 100 + patch
     *   2026.05.0 → 20260500
     *   2026.05.1 → 20260501
     *   2026.06.0 → 20260600
     */
    private fun versionCodeFromName(versionName: String): Int? {
        val parts = versionName.split(".")
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val patch = parts[2].toIntOrNull() ?: return null
        if (year !in 2000..2999 || month !in 1..12 || patch !in 0..99) return null
        return year * 10000 + month * 100 + patch
    }

    /**
     * Process the update: check if the version is newer, and optionally
     * post a notification.
     *
     * @return [update] if it's a newer version, null otherwise
     */
    fun process(update: Update?, notify: Boolean): Update? {
        if (update == null || update.versionCode <= BuildConfig.VERSION_CODE) {
            app.config.update = null
            return null
        }
        app.config.update = update

        if (EventBus.getDefault().hasSubscriberForEvent(update::class.java)) {
            EventBus.getDefault().postSticky(update)
            return update
        }

        if (notify)
            notify(update)
        return update
    }

    fun notify(update: Update) {
        if (!app.config.sync.notifyAboutUpdates)
            return
        val bigText = listOf(
            app.getString(R.string.notification_updates_text, update.versionName),
            update.releaseNotes?.let { BetterHtml.fromHtml(context = null, it) },
        )
        val notification = Notification(
            id = System.currentTimeMillis(),
            title = R.string.notification_updates_title.resolveString(app),
            text = bigText.concat("\n").toString(),
            type = NotificationType.UPDATE,
            profileId = null,
            profileName = R.string.notification_updates_title.resolveString(app),
        ).addExtra("action", "updateRequest")
        app.db.notificationDao().add(notification)
        PostNotifications(app, listOf(notification))
    }
}
