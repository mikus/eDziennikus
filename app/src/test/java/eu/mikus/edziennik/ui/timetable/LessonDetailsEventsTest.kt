/*
 * Copyright (c) Mikolaj Olszewski 2026-11-5.
 */

package eu.mikus.edziennik.ui.timetable

import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.utils.models.Time
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class LessonDetailsEventsTest {

    private val t10 = Time(10, 0, 0)
    private val a10 = normal(1, t10, Time(10, 45, 0), subject = 100)
    private val b20 = normal(2, t10, Time(10, 45, 0), subject = 200)
    private val e10 = examEvent(10, t10, subject = 100)
    private val e20 = examEvent(20, t10, subject = 200)

    private fun ids(events: List<EventFull>) = events.map { it.id }

    @Test
    fun `the dialog's events are filtered by the day's lessons`() = runTest {
        val shown = lessonEventsFlow(a10, flowOf(listOf(e10, e20)), flowOf(listOf<LessonFull>(a10, b20))).first()
        assertEquals(listOf(10L), ids(shown))
    }

    @Test
    fun `the dialog shows nothing until the day's lessons have loaded`() = runTest {
        val dayLessons = MutableSharedFlow<List<LessonFull>>()
        val shown = withTimeoutOrNull(1_000) {
            lessonEventsFlow(a10, flowOf(listOf(e10, e20)), dayLessons).first()
        }
        assertNull(shown)
    }

    @Test
    fun `the dialog follows a change in the day's lessons`() = runTest {
        val dayLessons = MutableStateFlow(listOf(a10))
        val emissions = mutableListOf<List<Long>>()
        backgroundScope.launch {
            lessonEventsFlow(a10, flowOf(listOf(e10, e20)), dayLessons).collect { emissions += ids(it) }
        }
        runCurrent()
        assertEquals(listOf(listOf(10L, 20L)), emissions)

        dayLessons.value = listOf(a10, b20)
        runCurrent()
        assertEquals(listOf(listOf(10L, 20L), listOf(10L)), emissions)
    }
}
