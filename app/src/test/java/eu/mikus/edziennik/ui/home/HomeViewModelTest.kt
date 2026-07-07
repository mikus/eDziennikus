/*
 * Copyright (c) Mikolaj Olszewski 2026-6-30.
 */

package eu.mikus.edziennik.ui.home

import eu.mikus.edziennik.data.db.enums.FeatureType
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.data.db.full.GradeFull
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.data.db.full.LuckyNumberFull
import eu.mikus.edziennik.data.db.entity.Note
import eu.mikus.edziennik.utils.models.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = Date(2026, 6, 1)
    private val cfg = HomeBuilder.Config(
        agendaSubjectImportant = false, homeEventsWeeks = 4,
        bellSyncDiffMillis = 0L, countInSeconds = false, notPublic = false,
    )
    private val allFeatures = setOf(FeatureType.LUCKY_NUMBER, FeatureType.TIMETABLE, FeatureType.AGENDA, FeatureType.GRADES)

    private fun vm(
        initialCards: List<HomeCardModel>,
        defaults: List<HomeCardModel> = listOf(HomeCardModel(1, HomeCard.CARD_NOTES)),
        saved: MutableList<List<HomeCardModel>> = mutableListOf(),
    ) = HomeViewModel(
        luckyNumberSource = { flowOf<LuckyNumberFull?>(null) },
        eventsSource = { flowOf(emptyList<EventFull>()) },
        gradesSource = { flowOf(emptyList<GradeFull>()) },
        notesSource = { flowOf(emptyList<Note>()) },
        timetableSource = { flowOf(emptyList<LessonFull>()) },
        loadCards = { initialCards },
        saveCards = { saved.add(it) },
        availableFeatures = allFeatures,
        archived = false,
        updateAvailable = false,
        locked = false,
        studentNumber = 7,
        profileName = "Jan",
        today = today,
        config = cfg,
        defaultCards = defaults,
        profileId = 1,
        dispatcher = dispatcher,
    )

    @BeforeEach fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterEach fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `emits Content from sources`() = runTest(dispatcher) {
        val model = vm(initialCards = listOf(HomeCardModel(1, HomeCard.CARD_GRADES), HomeCardModel(1, HomeCard.CARD_NOTES)))
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        val state = model.uiState.value as HomeUiState.Content
        assertEquals(listOf(HomeCard.CARD_GRADES, HomeCard.CARD_NOTES), state.cards.map { it.cardId })
        job.cancel()
    }

    @Test
    fun `seeds defaults + persists when stored list is empty`() = runTest(dispatcher) {
        val saved = mutableListOf<List<HomeCardModel>>()
        val model = vm(initialCards = emptyList(), defaults = listOf(HomeCardModel(1, HomeCard.CARD_NOTES)), saved = saved)
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(listOf(HomeCard.CARD_NOTES), (model.uiState.value as HomeUiState.Content).cards.map { it.cardId })
        assertEquals(1, saved.size)
        assertEquals(listOf(HomeCard.CARD_NOTES), saved.last().map { it.cardId })
        job.cancel()
    }

    @Test
    fun `reorder swaps and persists off-main`() = runTest(dispatcher) {
        val saved = mutableListOf<List<HomeCardModel>>()
        val model = vm(initialCards = listOf(HomeCardModel(1, 3), HomeCardModel(1, 5)), saved = saved)
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        model.reorder(fromId = 3, toId = 5)
        advanceUntilIdle()
        assertEquals(listOf(5, 3), (model.uiState.value as HomeUiState.Content).cards.map { it.cardId })
        assertEquals(listOf(5, 3), saved.last().map { it.cardId })
        job.cancel()
    }

    @Test
    fun `removeCard drops and persists`() = runTest(dispatcher) {
        val saved = mutableListOf<List<HomeCardModel>>()
        val model = vm(initialCards = listOf(HomeCardModel(1, 3), HomeCardModel(1, 5)), saved = saved)
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        model.removeCard(cardId = 3)
        advanceUntilIdle()
        assertEquals(listOf(5), (model.uiState.value as HomeUiState.Content).cards.map { it.cardId })
        assertEquals(listOf(5), saved.last().map { it.cardId })
        job.cancel()
    }

    @Test
    fun `gated-off card stays persisted but is hidden`() = runTest(dispatcher) {
        val saved = mutableListOf<List<HomeCardModel>>()
        val model = HomeViewModel(
            luckyNumberSource = { flowOf<LuckyNumberFull?>(null) }, eventsSource = { flowOf(emptyList()) },
            gradesSource = { flowOf(emptyList()) }, notesSource = { flowOf(emptyList()) },
            timetableSource = { flowOf(emptyList()) },
            loadCards = { listOf(HomeCardModel(1, HomeCard.CARD_GRADES), HomeCardModel(1, HomeCard.CARD_NOTES)) },
            saveCards = { saved.add(it) }, availableFeatures = emptySet(), archived = false, updateAvailable = false,
            locked = false, studentNumber = 7, profileName = "Jan", today = today, config = cfg,
            defaultCards = emptyList(), profileId = 1, dispatcher = dispatcher,
        )
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(listOf(HomeCard.CARD_NOTES), (model.uiState.value as HomeUiState.Content).cards.map { it.cardId })
        model.removeCard(HomeCard.CARD_NOTES)
        advanceUntilIdle()
        assertTrue(saved.last().any { it.cardId == HomeCard.CARD_GRADES })
        job.cancel()
    }
}
