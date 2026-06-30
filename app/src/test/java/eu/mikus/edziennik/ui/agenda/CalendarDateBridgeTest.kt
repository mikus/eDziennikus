/*
 * Copyright (c) Mikolaj Olszewski 2026-6-30.
 */

package eu.mikus.edziennik.ui.agenda

import eu.mikus.edziennik.utils.models.Date
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class CalendarDateBridgeTest {

    @Test
    fun `Date to LocalDate preserves 1-based month and day`() {
        assertEquals(LocalDate.of(2026, 9, 1), Date(2026, 9, 1).toLocalDate())
    }

    @Test
    fun `LocalDate to app Date round-trips`() {
        val d = Date(2026, 6, 30)
        assertEquals(d.value, d.toLocalDate().toAppDate().value)
    }

    @Test
    fun `Date to YearMonth`() {
        assertEquals(YearMonth.of(2026, 2), Date(2026, 2, 14).toYearMonth())
    }
}
