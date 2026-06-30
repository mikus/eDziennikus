/*
 * Copyright (c) Mikolaj Olszewski 2026-6-30.
 */

package eu.mikus.edziennik.ui.home

import eu.mikus.edziennik.data.db.entity.Grade.Companion.TYPE_NORMAL
import eu.mikus.edziennik.data.db.entity.Grade.Companion.TYPE_NO_GRADE
import eu.mikus.edziennik.data.db.full.GradeFull
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class HomeGradesGrouperTest {

    private fun grade(subjectId: Long, name: String?, type: Int = TYPE_NORMAL): GradeFull = mockk(relaxed = true) {
        every { this@mockk.subjectId } returns subjectId
        every { subjectLongName } returns name
        every { this@mockk.type } returns type
    }

    @Test
    fun `groups by subject preserving first-seen order`() {
        val rows = HomeGradesGrouper.group(listOf(
            grade(2, "Biologia"), grade(1, "Algebra"), grade(2, "Biologia"),
        ))
        assertEquals(listOf("Biologia", "Algebra"), rows.map { it.subjectName })
        assertEquals(2, rows[0].grades.size)
        assertEquals(1, rows[1].grades.size)
    }

    @Test
    fun `drops TYPE_NO_GRADE and null-subject grades`() {
        val rows = HomeGradesGrouper.group(listOf(
            grade(1, "Algebra", type = TYPE_NO_GRADE),
            grade(2, null),
            grade(3, "Fizyka"),
        ))
        assertEquals(listOf("Fizyka"), rows.map { it.subjectName })
    }

    @Test
    fun `empty in -- empty out`() {
        assertEquals(emptyList(), HomeGradesGrouper.group(emptyList()))
    }
}
