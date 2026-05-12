/*
 * Copyright (c) Kuba Szczodrzyński 2020-2-29.
 */

package eu.mikus.edziennik.ui.grades.models

import eu.mikus.edziennik.data.db.full.GradeFull

data class GradesSubject(
    val subjectId: Long,
    val subjectName: String,
    val semesters: MutableList<GradesSemester> = mutableListOf()
) : ExpandableItemModel<GradesSemester>(semesters) {
    override var level = 1

    var lastAddedDate = 0L
    var semester: Int = 1
    var isUnknown = false

    var hasUnseen: Boolean = false
        get() = field || semesters.any { it.hasUnseen }

    val averages = GradesAverages()
    var proposedGrade: GradeFull? = null
    var finalGrade: GradeFull? = null
}
