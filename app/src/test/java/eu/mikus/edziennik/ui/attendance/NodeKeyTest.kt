/*
 * Copyright (c) Mikolaj Olszewski 2026-6-29.
 */

package eu.mikus.edziennik.ui.attendance

import eu.mikus.edziennik.data.db.entity.Attendance
import eu.mikus.edziennik.data.db.entity.AttendanceType
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class NodeKeyTest {

    /**
     * NodeKey.stableId is the Bundle-storable LazyColumn item key. It MUST be unique per node — in
     * particular two custom types share id = baseType.toLong(), so a key based on id alone would
     * collide and crash LazyColumn with a duplicate key. Guards that regression.
     */
    @Test
    fun `stableId is distinct across variants and across same-id custom types`() {
        val custom1 = AttendanceType(1, 10, Attendance.TYPE_PRESENT_CUSTOM, "name1", "s1", "c1", null)
        val custom2 = AttendanceType(1, 10, Attendance.TYPE_PRESENT_CUSTOM, "name2", "s2", "c2", null)

        val ids = listOf(
            NodeKey.SubjectKey(1L).stableId,
            NodeKey.MonthKey(2026, 6).stableId,
            NodeKey.DayRangeKey(20260609L).stableId,
            NodeKey.TypeKey(custom1).stableId,
            NodeKey.TypeKey(custom2).stableId, // same id as custom1, must still differ
        )

        assertEquals(ids.size, ids.toSet().size) // all distinct
    }

    @Test
    fun `stableId is stable for equal keys`() {
        assertEquals(NodeKey.SubjectKey(7L).stableId, NodeKey.SubjectKey(7L).stableId)
        assertEquals(NodeKey.DayRangeKey(20260101L).stableId, NodeKey.DayRangeKey(20260101L).stableId)
    }
}
