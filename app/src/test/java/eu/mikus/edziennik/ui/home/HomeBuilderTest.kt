/*
 * Copyright (c) Mikolaj Olszewski 2026-6-30.
 */

package eu.mikus.edziennik.ui.home

import eu.mikus.edziennik.data.db.enums.FeatureType
import eu.mikus.edziennik.data.db.full.EventFull
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
    private val cfg = HomeBuilder.Config(agendaSubjectImportant = false, homeEventsWeeks = 4)
    private val noData = HomeBuilder.Data(luckyNumber = null, events = emptyList(), grades = emptyList(), notes = emptyList())

    private fun model(id: Int) = HomeCardModel(profileId = 1, cardId = id)

    private fun build(
        cards: List<HomeCardModel>,
        features: Set<FeatureType> = allFeatures,
        archived: Boolean = false,
        updateAvailable: Boolean = false,
        data: HomeBuilder.Data = noData,
    ) = HomeBuilder.build(
        cards = cards, availableFeatures = features, archived = archived, updateAvailable = updateAvailable,
        locked = false, studentNumber = 7, profileName = "Jan", today = today, config = cfg, data = data,
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
    fun `timetable maps to Wrapped`() {
        val content = build(listOf(model(HomeCard.CARD_TIMETABLE)))
        assertEquals(HomeCardUi.Wrapped(HomeCard.CARD_TIMETABLE), content.cards.single())
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
}
