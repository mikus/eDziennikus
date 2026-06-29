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
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class AttendanceCountsTest {

    private fun type(
        id: Long,
        baseType: Int,
        counted: Boolean = true,
        symbol: String = "x",
    ): AttendanceType = AttendanceType(
        profileId = 1, id = id, baseType = baseType,
        typeName = "name$id", typeShort = "s$id", typeSymbol = symbol, typeColor = null,
    ).also { it.isCounted = counted }

    private fun row(t: AttendanceType, baseType: Int = t.baseType): AttendanceFull =
        mockk(relaxed = true) {
            every { typeObject } returns t
            every { this@mockk.baseType } returns baseType
        }

    @Test
    fun `snapshot groups by AttendanceType in compareTo order, custom types stay distinct`() {
        val absent = type(1, Attendance.TYPE_ABSENT)
        val present = type(0, Attendance.TYPE_PRESENT)
        val custom1 = type(10, Attendance.TYPE_PRESENT_CUSTOM, symbol = "c1")
        val custom2 = type(10, Attendance.TYPE_PRESENT_CUSTOM, symbol = "c2") // same id, distinct type

        val snap = AttendanceCounts.snapshot(
            listOf(row(absent), row(present), row(present), row(custom1), row(custom2), row(custom2)),
        )

        // PRESENT(1) < PRESENT_CUSTOM(2) < ABSENT(8) by compareTo
        assertEquals(listOf(present, custom1, custom2, absent), snap.byType.map { it.type })
        assertEquals(listOf(2, 1, 2, 1), snap.byType.map { it.count })
    }

    @Test
    fun `percentage is presence-counted over counted-total as a 0f to 1f fraction`() {
        // present(counted) x3, belated(counted) x1, absent(counted) x4  => presence 4 / total 8 = 0.5
        val snap = AttendanceCounts.snapshot(
            List(3) { row(type(0, Attendance.TYPE_PRESENT)) } +
                List(1) { row(type(4, Attendance.TYPE_BELATED)) } +
                List(4) { row(type(1, Attendance.TYPE_ABSENT)) },
        )
        assertEquals(0.5f, AttendanceCounts.percentage(snap)!!, 0.0001f)
    }

    @Test
    fun `uncounted types and TYPE_UNKNOWN are excluded from the total`() {
        // present(counted) x1, unknown x5, uncounted-absent x5 => total counts only the present => 1/1 = 1.0
        val snap = AttendanceCounts.snapshot(
            List(1) { row(type(0, Attendance.TYPE_PRESENT)) } +
                List(5) { row(type(99, Attendance.TYPE_UNKNOWN)) } +
                List(5) { row(type(1, Attendance.TYPE_ABSENT, counted = false)) },
        )
        assertEquals(1.0f, AttendanceCounts.percentage(snap)!!, 0.0001f)
    }

    @Test
    fun `percentage is null when nothing is counted`() {
        val snap = AttendanceCounts.snapshot(List(3) { row(type(99, Attendance.TYPE_UNKNOWN)) })
        assertNull(AttendanceCounts.percentage(snap))
    }
}
