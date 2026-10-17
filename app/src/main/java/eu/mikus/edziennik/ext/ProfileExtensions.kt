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
fun currentSchoolYearStart() = schoolYearOf(Date.getToday())

/**
 * The calendar year in which the school year containing [date] started. The September rule lives
 * here alone: [currentSchoolYearStart] and the archived anchor of [getSchoolYear] both read it.
 */
private fun schoolYearOf(date: Date) = if (date.month < 9) date.year - 1 else date.year

/**
 * Whether this profile's stored semester 1 start, semester 2 start and year end read as a school
 * year, that is they are in order.
 *
 * The first comparison is strict on purpose. Before the date parsers learned to report an
 * unreadable value, three keys that were present but unparseable all collapsed onto 2019-01-01,
 * and a `<=` there would accept that trio as a school year.
 *
 * The second comparison is non-strict on purpose too, but for the opposite reason. A school year
 * whose semester 2 is a single day, starting on the year end, is odd, but it is the school's word
 * and not evidence of a parse failure: dates collapsed onto one value already fail the first
 * comparison and never reach this one.
 */
fun Profile.hasConsistentSchoolYear() =
    dateSemester1Start < dateSemester2Start && dateSemester2Start <= dateYearEnd

/**
 * The first day of [semester], or, when the stored semester starts are not in order, the usual
 * 1 September / 1 February of the current school year.
 *
 * This guards on the order of the two semester starts alone, not on [hasConsistentSchoolYear].
 * A boundary depends on those two dates and not on the year end, and the year end is the date
 * that is written last, so a school year whose end cannot be read can still carry a boundary the
 * school did send. Discarding it for 1 February would be wrong by weeks for a school whose
 * semester 2 starts in January.
 *
 * The fallback is a guess and is deliberately never written back to the profile: a school that
 * later reports a readable boundary has it picked up by the next Classes sync, with nothing of
 * ours in the way.
 */
fun Profile.getSemesterStart(semester: Int) = when {
    dateSemester1Start < dateSemester2Start ->
        if (semester == 1) dateSemester1Start else dateSemester2Start
    semester == 1 -> Date(currentSchoolYearStart(), 9, 1)
    else -> Date(currentSchoolYearStart() + 1, 2, 1)
}

/**
 * The last day of [semester]. The two arms do not use the same guard.
 *
 * Semester 1 is the day before [getSemesterStart] of semester 2, so it inherits that function's
 * guard, the order of the two semester starts alone, and not [hasConsistentSchoolYear]. A boundary
 * the school did send is therefore kept when only the year end cannot be read.
 *
 * Only the semester 2 arm consults [hasConsistentSchoolYear]. The year end is the value that arm
 * returns, and it is trusted only when the whole school year is in order; otherwise the answer is
 * 30 June of the current school year.
 */
fun Profile.getSemesterEnd(semester: Int) = when {
    semester == 1 -> getSemesterStart(2).clone().stepForward(0, 0, -1)
    hasConsistentSchoolYear() -> dateYearEnd
    else -> Date(currentSchoolYearStart() + 1, 6, 30)
}

fun Profile.dateToSemester(date: Date) = if (date >= getSemesterStart(2)) 2 else 1
fun Profile.isBeforeYear() = false && Date.getToday() < dateSemester1Start

/**
 * The first and last day of this profile's school year, always with `start < endInclusive`. This is
 * the one answer for every reader of the outer bounds: the Agenda's calendar, the Timetable's day
 * pager and the date pickers.
 *
 * The stored semester 1 start and year end are trusted only when the year starts before it ends and
 * ends before the anniversary of its own start. A legal Polish school year runs 1 September to
 * 31 August, 364 days or 365 across a leap day, so the cap sits one day above it. The predicate
 * reads two fields the same parser wrote from the same response, never the wall clock, so an
 * archived profile's old but coherent year passes unchanged. The strict first comparison rejects a
 * one-day year, which is what several keys collapsing onto 2019-01-01 look like.
 *
 * It tests the pair and not the trio because the readers never read semester 2: a damaged semester
 * 2 start alone leaves the outer bounds good, and discarding them for it would be wrong by months.
 *
 * Both bounds come from this one decision. Guarded separately, an archived profile with only its
 * start unreadable would take that start from today and its end from storage, the range would
 * invert, and the Agenda's calendar would crash in `checkRange`. When the pair fails, both bounds
 * are 1 September to 30 June of one school year.
 *
 * That year is the current one for a live profile. An archived profile is never synced again, so
 * its year is frozen and today is the wrong anchor: it uses the school year of the latest of its
 * three stored dates. Every substitute the old parsers wrote was 2019-01-01, earlier than any date
 * a school sent, so the latest date is a real one in every shape except all three unreadable.
 * An archived profile with all three unreadable cannot be recovered and falls back to 2018/19,
 * where before it showed a single day.
 *
 * Nothing is written back, so a school that later sends a readable year has it picked up by the
 * next Classes sync. [getSemesterStart] with semester 1 still honours a bad semester 1 start. That
 * is deliberate: its two consumers cannot be reached on a profile that is already damaged, and
 * routing it here would give two notions of the year start that disagree where only semester 1
 * is bad.
 */
fun Profile.getSchoolYear(): ClosedRange<Date> {
    val start = dateSemester1Start
    val end = dateYearEnd
    if (start < end && end < Date(start.year + 1, start.month, start.day))
        return start..end
    val year =
        if (archived) schoolYearOf(maxOf(dateSemester1Start, dateSemester2Start, dateYearEnd))
        else currentSchoolYearStart()
    return Date(year, 9, 1)..Date(year + 1, 6, 30)
}

fun Profile.getSchoolYearConstrains() = getSchoolYear().let { year ->
    CalendarConstraints.Builder()
        .setStart(year.start.inMillisUtc)
        .setEnd(year.endInclusive.inMillisUtc)
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
