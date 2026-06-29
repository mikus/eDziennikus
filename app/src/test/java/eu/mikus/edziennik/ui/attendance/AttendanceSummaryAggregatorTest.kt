/*
 * Copyright (c) Mikolaj Olszewski 2026-6-29.
 */

package eu.mikus.edziennik.ui.attendance

import eu.mikus.edziennik.data.db.entity.Attendance
import eu.mikus.edziennik.data.db.entity.AttendanceType
import eu.mikus.edziennik.data.db.full.AttendanceFull
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class AttendanceSummaryAggregatorTest {

    private val presentType = AttendanceType(1, 0, Attendance.TYPE_PRESENT, "obecność", "ob", "•", null)
    private val absentType = AttendanceType(1, 1, Attendance.TYPE_ABSENT, "nieobecność", "nb", "nb", null)

    private fun row(
        id: Long,
        subjectId: Long,
        subjectName: String,
        baseType: Int,
        type: AttendanceType,
        semester: Int = 1,
        seen: Boolean = true,
    ): AttendanceFull = mockk(relaxed = true) {
        every { this@mockk.id } returns id
        every { this@mockk.subjectId } returns subjectId
        every { subjectLongName } returns subjectName
        every { this@mockk.baseType } returns baseType
        every { typeObject } returns type
        every { this@mockk.semester } returns semester
        every { this@mockk.seen } returns seen
    }

    @Test
    fun `groups by subject sorted by name, strips PRESENT from leaves, keeps it in counts`() {
        val rows = listOf(
            row(1, 20, "Zoologia", Attendance.TYPE_PRESENT, presentType),
            row(2, 20, "Zoologia", Attendance.TYPE_ABSENT, absentType),
            row(3, 10, "Algebra", Attendance.TYPE_ABSENT, absentType),
        )
        val tab = AttendanceSummaryAggregator.aggregate(rows, Period.ALL, currentSemester = 1)

        assertEquals(listOf("Algebra", "Zoologia"), tab.subjects.map { it.name })
        val zoologia = tab.subjects.first { it.name == "Zoologia" }
        assertEquals(1, zoologia.leaves.size)                          // PRESENT stripped from leaves
        assertEquals(2, zoologia.counts.byType.sumOf { it.count })     // PRESENT kept in counts
        assertEquals(0.5f, zoologia.percentage!!, 0.0001f)             // 1 present / 2 total
    }

    @Test
    fun `period filter selects only the chosen semester`() {
        val rows = listOf(
            row(1, 10, "Algebra", Attendance.TYPE_ABSENT, absentType, semester = 1),
            row(2, 10, "Algebra", Attendance.TYPE_ABSENT, absentType, semester = 2),
        )
        assertEquals(2, AttendanceSummaryAggregator.aggregate(rows, Period.ALL, 1).subjects.first().leaves.size)
        assertEquals(1, AttendanceSummaryAggregator.aggregate(rows, Period.SEM1, 1).subjects.first().leaves.size)
        assertEquals(1, AttendanceSummaryAggregator.aggregate(rows, Period.SEM2, 1).subjects.first().leaves.size)
    }

    @Test
    fun `overall stats aggregate across subjects`() {
        val rows = listOf(
            row(1, 10, "Algebra", Attendance.TYPE_PRESENT, presentType),
            row(2, 10, "Algebra", Attendance.TYPE_ABSENT, absentType),
            row(3, 20, "Biologia", Attendance.TYPE_ABSENT, absentType),
        )
        val stats = AttendanceSummaryAggregator.aggregate(rows, Period.ALL, 1).stats
        assertEquals(1f / 3f, stats.overallPercent!!, 0.0001f)   // 1 present / 3 total
        assertEquals(3, stats.counts.byType.sumOf { it.count })
    }

    @Test
    fun `hasUnseen reflects an unseen non-present leaf`() {
        val seenOnly = AttendanceSummaryAggregator.aggregate(
            listOf(row(1, 10, "Algebra", Attendance.TYPE_ABSENT, absentType, seen = true)), Period.ALL, 1,
        )
        assertFalse(seenOnly.subjects.first().hasUnseen)

        val withUnseen = AttendanceSummaryAggregator.aggregate(
            listOf(row(1, 10, "Algebra", Attendance.TYPE_ABSENT, absentType, seen = false)), Period.ALL, 1,
        )
        assertTrue(withUnseen.subjects.first().hasUnseen)
    }
}
