/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-25.
 */
package eu.mikus.edziennik.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index

@Entity(tableName = "notices",
        primaryKeys = ["profileId", "noticeId"],
        indices = [
            Index(value = ["profileId"])
        ])
open class Notice(
        val profileId: Int,
        @ColumnInfo(name = "noticeId")
        var id: Long,
        @ColumnInfo(name = "noticeType")
        var type: Int,
        @ColumnInfo(name = "noticeSemester")
        var semester: Int,

        @ColumnInfo(name = "noticeText")
        var text: String,
        @ColumnInfo(name = "noticeCategory")
        var category: String?,
        @ColumnInfo(name = "noticePoints")
        var points: Float?,

        var teacherId: Long,
        var addedDate: Long = System.currentTimeMillis()
) : Keepable() {
    companion object {
        /**
         * A notice whose type the provider's data does not determine. Counted toward no summary
         * bucket.
         */
        const val TYPE_UNKNOWN = -1
        const val TYPE_NEUTRAL = 0
        const val TYPE_POSITIVE = 1
        const val TYPE_NEGATIVE = 2

        /**
         * The [addedDate] that means the school's date for this notice could not be read. Readers
         * must not compare against it; they ask [addedDateUnknown] instead.
         *
         * One reader depends on the value itself by design, and a grep for either name cannot
         * find it: `NoticeDao`'s `ORDER BY addedDate DESC` lists undated notices after every notice
         * dated after 1970-01-01 only because 0 is below all of those dates. Keep the value where
         * it is.
         *
         * This is a sentinel in the existing column rather than a flag column (as
         * `Event.dateUnknown`), chosen so that no schema change is needed. Grades use a literal
         * `0L` for a missing added date.
         *
         * `0L` is also local midnight of 1970-01-01 in a zero-offset zone, so on a UTC device a
         * notice truly dated that day would read as unknown. Accepted: no school sends that date.
         */
        const val ADDED_DATE_UNKNOWN = 0L
    }

    /**
     * True when [addedDate] holds [ADDED_DATE_UNKNOWN], the value that means the school's date for
     * this notice could not be read. When true, [addedDate] is not a date and must not be shown as
     * one.
     *
     * Getter-only, with no backing field, so Room maps no column for it and the selective DAO binds
     * nothing for it (as `Event.isHomework`). A reader that asks this rather than comparing
     * [addedDate] itself does not depend on the encoding: if notices ever get a flag column (as
     * `Event.dateUnknown`), this property becomes that column and no reader that asks it changes.
     *
     * Asked by:
     * - `noticeDateText`, for the date in `BehaviourScreen`'s notice row and in `NoticesAdapter`.
     * - `noticeNotificationText` and `noticeNotificationTime`, for a new-notice notification.
     */
    val addedDateUnknown: Boolean
        get() = addedDate == ADDED_DATE_UNKNOWN

    @Ignore
    var showAsUnseen = false
}
