/*
 * Copyright (c) Kuba Szczodrzyński 2020-2-29.
 */

package eu.mikus.edziennik.ui.grades.models

import eu.mikus.edziennik.data.db.full.GradeFull

data class GradesSemester(
    val subjectId: Long,
    val number: Int,
    val grades: MutableList<GradeFull> = mutableListOf()
) : ExpandableItemModel<GradeFull>(grades) {
    override var level = 2

    var hasUnseen = false
    var hideEditor = false

    val averages = GradesAverages()
    var proposedGrade: GradeFull? = null
    var finalGrade: GradeFull? = null
}
