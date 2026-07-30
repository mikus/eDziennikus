/*
 * Copyright (c) Mikolaj Olszewski 2026-7-25.
 */
package eu.mikus.edziennik.ui.base

import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.JsonObject
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.api.edziennik.EdziennikTask
import eu.mikus.edziennik.data.db.enums.FeatureType
import eu.mikus.edziennik.ext.*

/**
 * Shared feature-sync trigger, extracted from MainActivity.syncCurrentFeature. Replicates its
 * archived / before-year guards (dialog + return) and shouldArchive dialog, eagerly marks the
 * app-scoped SyncStatus refreshing (so a PullToRefreshBox indicator stays continuous from release),
 * then enqueues the sync for the current profile. The per-feature Toast is intentionally dropped
 * (the PullToRefreshBox spinner + toolbar subtitle already signal the sync).
 */
fun syncFeature(activity: AppCompatActivity, featureType: FeatureType?, arguments: JsonObject? = null) {
    val app = activity.application as App
    if (app.profile.archived) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.profile_archived_title)
            .setMessage(
                activity.getString(
                    R.string.profile_archived_text,
                    app.profile.studentSchoolYearStart,
                    app.profile.studentSchoolYearStart + 1,
                )
            )
            .setPositiveButton(R.string.ok, null)
            .show()
        return
    }
    if (app.profile.shouldArchive()) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.profile_archiving_title)
            .setMessage(
                activity.getString(
                    R.string.profile_archiving_format,
                    app.profile.dateYearEnd.formattedString,
                )
            )
            .setPositiveButton(R.string.ok, null)
            .show()
    }
    if (app.profile.isBeforeYear()) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.profile_year_not_started_title)
            .setMessage(
                activity.getString(
                    R.string.profile_year_not_started_format,
                    app.profile.dateSemester1Start.formattedString,
                )
            )
            .setPositiveButton(R.string.ok, null)
            .show()
        return
    }
    app.syncStatus.markRefreshing()
    EdziennikTask.syncProfile(
        App.profileId,
        featureType?.let { setOf(it) },
        arguments = arguments,
    ).enqueue(activity)
}
