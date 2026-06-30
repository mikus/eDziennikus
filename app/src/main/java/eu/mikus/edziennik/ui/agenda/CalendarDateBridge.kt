/*
 * Copyright (c) Mikolaj Olszewski 2026-6-30.
 */

package eu.mikus.edziennik.ui.agenda

import eu.mikus.edziennik.utils.models.Date
import java.time.LocalDate
import java.time.YearMonth

/** App [Date] (1-based y/m/d) <-> java.time. Bridges the Kizitonwose calendar (java.time) and the app model. */
fun Date.toLocalDate(): LocalDate = LocalDate.of(year, month, day)

fun Date.toYearMonth(): YearMonth = YearMonth.of(year, month)

fun LocalDate.toAppDate(): Date = Date(year, monthValue, dayOfMonth)
