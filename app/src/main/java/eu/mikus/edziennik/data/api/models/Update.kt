/*
 * Copyright (c) Kuba Szczodrzyński 2020-1-18.
 */

package eu.mikus.edziennik.data.api.models

/**
 * DTO for a fork release: the shape returned by the GitHub Releases API
 * lookup in `UpdateManager.checkNow()` and stored in `app.config.update`.
 *
 * Originally lived at `data/api/szkolny/response/UpdateResponse.kt` as a
 * JSON shape returned by szkolny.eu's `/update` endpoint; moved here when
 * that endpoint went away and UpdateManager started consuming GitHub's
 * Releases API instead.
 */
data class Update(
    val versionCode: Int,
    val versionName: String,
    val releaseDate: String,
    val releaseNotes: String?,
    val releaseType: String,
    val isOnGooglePlay: Boolean,
    val downloadUrl: String?,
    val updateMandatory: Boolean,
) {

    enum class Type {
        NIGHTLY,
        DEV,
        BETA,
        RC,
        RELEASE,
    }
}
