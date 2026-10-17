/*
 * Copyright (c) Kuba Szczodrzyński 2022-10-25.
 */

package eu.mikus.edziennik.ext

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import com.google.android.material.datepicker.CalendarConstraints
import com.google.gson.JsonElement
import pl.droidsonroids.gif.GifDrawable
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.compat.getDrawableFromRes
import eu.mikus.edziennik.config.AppData
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.enums.FeatureType
import eu.mikus.edziennik.data.db.enums.LoginType
import eu.mikus.edziennik.utils.models.Date

// TODO refactor Data* fields and make the receiver non-nullable
operator fun Profile?.set(key: String, value: JsonElement) = this?.studentData?.add(key, value)
operator fun Profile?.set(key: String, value: Boolean) = this?.studentData?.addProperty(key, value)
operator fun Profile?.set(key: String, value: String?) = this?.studentData?.addProperty(key, value)
operator fun Profile?.set(key: String, value: Number) = this?.studentData?.addProperty(key, value)
operator fun Profile?.set(key: String, value: Char) = this?.studentData?.addProperty(key, value)

fun Profile.getStudentData(key: String, defaultValue: Boolean) =
    studentData.getBoolean(key) ?: defaultValue

fun Profile.getStudentData(key: String, defaultValue: String?) =
    studentData.getString(key) ?: defaultValue

fun Profile.getStudentData(key: String, defaultValue: Int) =
    studentData.getInt(key) ?: defaultValue

fun Profile.getStudentData(key: String, defaultValue: Long) =
    studentData.getLong(key) ?: defaultValue

fun Profile.getStudentData(key: String, defaultValue: Float) =
    studentData.getFloat(key) ?: defaultValue

fun Profile.getStudentData(key: String, defaultValue: Char) =
    studentData.getChar(key) ?: defaultValue

/**
 * The calendar year in which the current school year started. A school year runs September to
 * August, so before September it is the previous calendar year. A new [Profile] computes its
 * default `studentSchoolYearStart` the same way.
 */
fun currentSchoolYearStart() = Date.getToday().let { if (it.month < 9) it.year - 1 else it.year }

/**
 * Whether this profile's stored semester 1 start, semester 2 start and year end read as a school
 * year, that is they are in order.
 *
 * The first comparison is strict on purpose. Before the date parsers learned to report an
 * unreadable value, three keys that were present but unparseable all collapsed onto 2019-01-01,
 * and a `<=` there would accept that trio as a school year.
 */
fun Profile.hasConsistentSchoolYear() =
    dateSemester1Start < dateSemester2Start && dateSemester2Start <= dateYearEnd

/**
 * The first day of [semester], or, when the stored dates are not a school year (see
 * [hasConsistentSchoolYear]), the usual 1 September / 1 February of the current school year.
 *
 * The fallback is a guess and is deliberately never written back to the profile: a school that
 * later reports a readable boundary has it picked up by the next Classes sync, with nothing of
 * ours in the way.
 */
fun Profile.getSemesterStart(semester: Int) = when {
    hasConsistentSchoolYear() -> if (semester == 1) dateSemester1Start else dateSemester2Start
    semester == 1 -> Date(currentSchoolYearStart(), 9, 1)
    else -> Date(currentSchoolYearStart() + 1, 2, 1)
}

fun Profile.getSemesterEnd(semester: Int) = when {
    semester == 1 -> getSemesterStart(2).clone().stepForward(0, 0, -1)
    hasConsistentSchoolYear() -> dateYearEnd
    else -> Date(currentSchoolYearStart() + 1, 6, 30)
}

fun Profile.dateToSemester(date: Date) = if (date >= getSemesterStart(2)) 2 else 1
fun Profile.isBeforeYear() = false && Date.getToday() < dateSemester1Start

fun Profile.getSchoolYearConstrains(): CalendarConstraints {
    return CalendarConstraints.Builder()
        .setStart(dateSemester1Start.inMillisUtc)
        .setEnd(dateYearEnd.inMillisUtc)
        .build()
}

fun Profile.hasFeature(featureType: FeatureType) = featureType in this.loginStoreType.features
fun Profile.hasUIFeature(featureType: FeatureType) =
    featureType.isUIAlwaysAvailable || hasFeature(featureType)

fun Profile.getAppData() =
    if (App.profileId == this.id) App.data else AppData.get(this.loginStoreType)

fun Profile.shouldArchive(): Boolean {
    return false
    // vulcan hotfix
    if (dateYearEnd.month > 6) {
        dateYearEnd.month = 6
        dateYearEnd.day = 30
    }
    // fix for when versions <4.3 synced 2020/2021 year dates to older profiles during 2020 Jun-Aug
    if (dateSemester1Start.year > studentSchoolYearStart) {
        val diff = dateSemester1Start.year - studentSchoolYearStart
        dateSemester1Start.year -= diff
        dateSemester2Start.year -= diff
        dateYearEnd.year -= diff
    }
    return App.config.archiverEnabled && Date.getToday() >= dateYearEnd && Date.getToday().year > studentSchoolYearStart
}

fun Profile.getDrawable(context: Context): Drawable {
    if (archived) {
        return context.getDrawableFromRes(R.drawable.profile_archived).also {
            it.colorFilter = PorterDuffColorFilter(colorFromName(name), PorterDuff.Mode.DST_OVER)
        }
    }

    if (!image.isNullOrEmpty()) {
        try {
            return if (image?.endsWith(".gif", true) == true) {
                GifDrawable(image ?: "")
            } else {
                RoundedBitmapDrawableFactory.create(context.resources, image ?: "")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    return context.getDrawableFromRes(R.drawable.profile).also {
        it.colorFilter = PorterDuffColorFilter(colorFromName(name), PorterDuff.Mode.DST_OVER)
    }
}
