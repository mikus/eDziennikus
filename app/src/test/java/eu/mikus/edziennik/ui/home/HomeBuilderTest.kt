/*
 * Copyright (c) Mikolaj Olszewski 2026-6-30.
 */

package eu.mikus.edziennik.ui.home

import eu.mikus.edziennik.config.ProfileConfigUI
import eu.mikus.edziennik.data.db.enums.FeatureType
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.data.db.full.GradeFull
import eu.mikus.edziennik.data.db.entity.Note
import eu.mikus.edziennik.utils.models.Date
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeBuilderTest {

    private val today = Date(2026, 6, 1)
    private val allFeatures = setOf(FeatureType.LUCKY_NUMBER, FeatureType.TIMETABLE, FeatureType.AGENDA, FeatureType.GRADES)
    // The two ceilings mean "no narrowing", so tests that are not about the limit or the grades
    // window see the whole input, exactly as they did before Home moved that narrowing out of SQL.
    private val cfg = HomeBuilder.Config(
        agendaSubjectImportant = false,
        homeEventsLimit = ProfileConfigUI.MAX_HOME_EVENTS_LIMIT, homeEventsWeeks = 4,
        homeGradesWeeks = ProfileConfigUI.MAX_HOME_GRADES_WEEKS,
        bellSyncDiffMillis = 0L, countInSeconds = false, notPublic = false,
    )
    private val noData = HomeBuilder.Data(
        luckyNumber = null, events = emptyList(), grades = emptyList(), notes = emptyList(), timetableLessons = emptyList(),
    )

    private fun model(id: Int) = HomeCardModel(profileId = 1, cardId = id)

    private fun build(
        cards: List<HomeCardModel>,
        features: Set<FeatureType> = allFeatures,
        archived: Boolean = false,
        updateAvailable: Boolean = false,
        config: HomeBuilder.Config = cfg,
        data: HomeBuilder.Data = noData,
    ) = HomeBuilder.build(
        cards = cards, availableFeatures = features, archived = archived, updateAvailable = updateAvailable,
        locked = false, studentNumber = 7, profileName = "Jan", today = today, config = config, data = data,
    )

    @Test
    fun `maps each card id to its HomeCardUi in configured order`() {
        val content = build(listOf(model(HomeCard.CARD_GRADES), model(HomeCard.CARD_NOTES)))
        assertEquals(listOf(HomeCard.CARD_GRADES, HomeCard.CARD_NOTES), content.cards.map { it.cardId })
        assertTrue(content.cards[0] is HomeCardUi.Grades)
        assertTrue(content.cards[1] is HomeCardUi.Notes)
    }

    @Test
    fun `gated-off card is omitted from display`() {
        val content = build(listOf(model(HomeCard.CARD_GRADES), model(HomeCard.CARD_NOTES)), features = emptySet())
        assertEquals(listOf(HomeCard.CARD_NOTES), content.cards.map { it.cardId })
    }

    @Test
    fun `pinned availability then archive prepend before user cards`() {
        val content = build(listOf(model(HomeCard.CARD_NOTES)), archived = true, updateAvailable = true)
        assertEquals(listOf(102, 101, HomeCard.CARD_NOTES), content.cards.map { it.cardId })
        assertTrue(content.cards[0] is HomeCardUi.Wrapped)
        assertTrue(content.cards[1] is HomeCardUi.Wrapped)
    }

    @Test
    fun `timetable maps to native Timetable variant`() {
        val content = build(listOf(model(HomeCard.CARD_TIMETABLE)))
        assertTrue(content.cards.single() is HomeCardUi.Timetable)
    }

    @Test
    fun `notes capped to 4`() {
        val notes = (1..6).map { mockk<Note>(relaxed = true) }
        val content = build(listOf(model(HomeCard.CARD_NOTES)), data = noData.copy(notes = notes))
        assertEquals(4, (content.cards.single() as HomeCardUi.Notes).rows.size)
    }

    @Test
    fun `events filtered to within the window`() {
        fun event(d: Date): EventFull = mockk(relaxed = true) { every { date } returns d }
        val inWindow = event(Date(2026, 6, 10))
        val outWindow = event(Date(2026, 8, 1))
        val content = build(listOf(model(HomeCard.CARD_EVENTS)), data = noData.copy(events = listOf(inWindow, outWindow)))
        val events = content.cards.single() as HomeCardUi.Events
        assertEquals(listOf(inWindow), events.rows)
        assertEquals(true, events.showType)
        assertEquals(false, events.showSubject)
    }

    private fun eventOn(d: Date): EventFull = mockk(relaxed = true) { every { date } returns d }

    private fun grade(subjectId: Long, name: String, added: Long): GradeFull = mockk(relaxed = true) {
        every { this@mockk.subjectId } returns subjectId
        every { subjectLongName } returns name
        every { addedDate } returns added
    }

    /** The limit used to be `LIMIT n` in SQL; the query now fetches the ceiling and this narrows. */
    @Test
    fun `the events card shows at most homeEventsLimit rows`() {
        // today = 2026-6-1, 4 weeks -> window ends 2026-6-29, so all five are inside it.
        val events = (2..6).map { eventOn(Date(2026, 6, it)) }
        val content = build(
            listOf(model(HomeCard.CARD_EVENTS)),
            config = cfg.copy(homeEventsLimit = 2),
            data = noData.copy(events = events),
        )
        assertEquals(events.take(2), (content.cards.single() as HomeCardUi.Events).rows)
    }

    /** The week window; events past it are dropped regardless of the limit. */
    @Test
    fun `the events card drops events past the week window`() {
        val inA = eventOn(Date(2026, 6, 10))
        val inB = eventOn(Date(2026, 6, 15))
        val farOut = eventOn(Date(2026, 8, 1))
        val content = build(
            listOf(model(HomeCard.CARD_EVENTS)),
            config = cfg.copy(homeEventsLimit = 3),   // exactly enough to keep all three
            data = noData.copy(events = listOf(inA, inB, farOut)),
        )
        assertEquals(listOf(inA, inB), (content.cards.single() as HomeCardUi.Events).rows)
    }

    /** The grades window used to be `addedDate > N` in SQL. */
    @Test
    fun `the grades card drops grades older than homeGradesWeeks`() {
        // today = 2026-6-1, 2 weeks -> cutoff 2026-5-18.
        val recent = grade(subjectId = 1, name = "Algebra", added = Date(2026, 5, 25).inMillis)
        val stale = grade(subjectId = 2, name = "Biologia", added = Date(2026, 5, 1).inMillis)
        val content = build(
            listOf(model(HomeCard.CARD_GRADES)),
            config = cfg.copy(homeGradesWeeks = 2),
            data = noData.copy(grades = listOf(recent, stale)),
        )
        val rows = (content.cards.single() as HomeCardUi.Grades).subjects
        assertEquals(listOf("Algebra"), rows.map { it.subjectName })
        assertEquals(listOf(recent), rows.single().grades)
    }
}
