/*
 * Copyright (c) Mikolaj Olszewski 2026-6-30.
 */

package eu.mikus.edziennik.ui.home

import eu.mikus.edziennik.data.db.entity.Grade.Companion.TYPE_NO_GRADE
import eu.mikus.edziennik.data.db.full.GradeFull

/**
 * Pure port of HomeGradesCard's per-subject grouping. Groups recent grades by subject in first-seen
 * order, dropping no-grade entries and grades whose subject name is unknown. The card (later task)
 * renders each row's grades in a FlowRow (natural wrapping replaces the legacy manual pixel ellipsis).
 */
object HomeGradesGrouper {
    fun group(grades: List<GradeFull>): List<SubjectGradeRow> {
        val bySubject = LinkedHashMap<Long, MutableList<GradeFull>>()
        val names = HashMap<Long, String>()
        for (g in grades) {
            if (g.type == TYPE_NO_GRADE) continue
            val name = g.subjectLongName ?: continue
            bySubject.getOrPut(g.subjectId) { mutableListOf() }.add(g)
            names.getOrPut(g.subjectId) { name }
        }
        return bySubject.map { (id, gs) -> SubjectGradeRow(names.getValue(id), gs) }
    }
}
