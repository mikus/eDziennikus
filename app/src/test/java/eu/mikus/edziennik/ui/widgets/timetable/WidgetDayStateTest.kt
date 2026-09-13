/*
 * Copyright (c) Mikolaj Olszewski 2026-9-13.
 */

package eu.mikus.edziennik.ui.widgets.timetable

import eu.mikus.edziennik.data.db.entity.Lesson
import eu.mikus.edziennik.data.db.full.LessonFull
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class WidgetDayStateTest {

    private fun lesson(type: Int = Lesson.TYPE_NORMAL, cancelled: Boolean = false): LessonFull =
        mockk(relaxed = true) {
            every { this@mockk.type } returns type
            every { isCancelled } returns cancelled
        }

    @Test
    fun `a resolved day with lessons is CONTENT even when a later week is missing`() {
        // The whole point of the seam. Coverage is a question about the *window*; what to render is a
        // question about the *resolved day*. Conflating them made the widget report "not downloaded"
        // on top of the lessons it had just found - and, because next week is only pre-fetched at
        // weekends, would have done so every weekday for every user.
        assertEquals(
            WidgetDayState.CONTENT,
            widgetDayState(listOf(lesson()), notDownloadedWeek = "2026-09-14"),
        )
    }

    @Test
    fun `an empty resolved day with a missing week is NOT_DOWNLOADED`() {
        assertEquals(
            WidgetDayState.NOT_DOWNLOADED,
            widgetDayState(emptyList(), notDownloadedWeek = "2026-09-14"),
        )
    }

    @Test
    fun `an empty resolved day with everything downloaded is NO_LESSONS`() {
        assertEquals(WidgetDayState.NO_LESSONS, widgetDayState(emptyList(), notDownloadedWeek = null))
        // A lone marker is the same thing said differently.
        assertEquals(
            WidgetDayState.NO_LESSONS,
            widgetDayState(listOf(lesson(type = Lesson.TYPE_NO_LESSONS)), notDownloadedWeek = null),
        )
    }

    @Test
    fun `a day of only cancelled lessons is empty for this purpose`() {
        // The production guard is `none { !it.isCancelled }`, not `isEmpty()`. Without this test a
        // seam written with `resolved.isEmpty()` passes every other case here, and the widget would
        // render a day whose every lesson is cancelled as if it had something to show.
        val cancelled = listOf(lesson(cancelled = true), lesson(cancelled = true))
        assertEquals(WidgetDayState.NO_LESSONS, widgetDayState(cancelled, notDownloadedWeek = null))
        assertEquals(
            WidgetDayState.NOT_DOWNLOADED,
            widgetDayState(cancelled, notDownloadedWeek = "2026-09-14"),
        )
    }
}
