/*
 * Copyright (c) Mikolaj Olszewski 2026-7-18.
 */
package eu.mikus.edziennik.ui.dialogs.settings

import eu.mikus.edziennik.utils.models.Time
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class BellSyncLogicTest {

    @Test fun `parse rejects too short`() = assertNull(bellSyncParse("+0:00"))
    @Test fun `parse rejects bad colons`() = assertNull(bellSyncParse("+00000000"))
    @Test fun `parse rejects missing sign`() = assertNull(bellSyncParse("00:00:00"))
    @Test fun `parse plus`() {
        val (t, m) = bellSyncParse("+1:02:03")!!
        assertEquals(1, t.hour); assertEquals(2, t.minute); assertEquals(3, t.second); assertEquals(1, m)
    }
    @Test fun `parse minus`() {
        val (t, m) = bellSyncParse("-0:05:00")!!
        assertEquals(5, t.minute); assertEquals(-1, m)
    }

    private fun t(h: Int, m: Int) = Time(h, m, 0)

    @Test fun `canSync false when empty`() = assertFalse(bellSyncCanSync(emptyList(), t(8, 0), 10))
    @Test fun `canSync true inside window`() =
        assertTrue(bellSyncCanSync(listOf(t(8, 0), t(15, 0)), t(10, 0), 10))
    @Test fun `canSync true up to 10 min before first`() =
        assertTrue(bellSyncCanSync(listOf(t(8, 0), t(15, 0)), t(7, 50), 10))
    @Test fun `canSync false more than 10 min before first`() =
        assertFalse(bellSyncCanSync(listOf(t(8, 0), t(15, 0)), t(7, 49), 10))
    @Test fun `canSync true exactly at last`() =
        assertTrue(bellSyncCanSync(listOf(t(8, 0), t(15, 0)), t(15, 0), 10))
    @Test fun `canSync false after last (no tail grace, unlike a symmetric window)`() =
        assertFalse(bellSyncCanSync(listOf(t(8, 0), t(15, 0)), t(15, 1), 10))

    @Test fun `actualDiff future bell is negative`() {
        val (_, m) = bellSyncActualDiff(t(10, 0), t(10, 5)); assertEquals(-1, m)
    }
    @Test fun `actualDiff past bell is positive`() {
        val (_, m) = bellSyncActualDiff(t(10, 5), t(10, 0)); assertEquals(1, m)
    }
}
