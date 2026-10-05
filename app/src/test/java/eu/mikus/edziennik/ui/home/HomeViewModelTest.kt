/*
 * Copyright (c) Mikolaj Olszewski 2026-6-30.
 */

package eu.mikus.edziennik.ui.home

import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.enums.FeatureType
import eu.mikus.edziennik.data.db.enums.LoginType
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.data.db.full.GradeFull
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.data.db.full.LuckyNumberFull
import eu.mikus.edziennik.data.db.entity.Note
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.utils.models.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
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
        profileSource: () -> Flow<Profile?> = { flowOf(profile(studentNumber = 7)) },
    ) = HomeViewModel(
        luckyNumberSource = { flowOf<LuckyNumberFull?>(null) },
        eventsSource = { flowOf(emptyList<EventFull>()) },
        gradesSource = { flowOf(emptyList<GradeFull>()) },
        notesSource = { flowOf(emptyList<Note>()) },
        timetableSource = { flowOf(emptyList<LessonFull>()) },
        profileSource = profileSource,
        loadCards = { initialCards },
        saveCards = { saved.add(it) },
        availableFeatures = allFeatures,
        seedProfile = ProfileInputs(7, "Jan", false),
        updateAvailable = false,
        locked = false,
        today = today,
        config = cfg,
        defaultCards = defaults,
        profileId = 1,
        dispatcher = dispatcher,
    )

    private fun profile(studentNumber: Int, name: String = "Jan", archived: Boolean = false) =
        Profile(id = 1, loginStoreId = 1, loginStoreType = LoginType.LIBRUS).also {
            it.name = name
            it.studentNumber = studentNumber
            it.archived = archived
        }

    private fun luckyCard(state: HomeUiState.Content) =
        state.cards.filterIsInstance<HomeCardUi.LuckyNumber>().single()

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
        assertTrue(model.removeCard(cardId = 3))
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
            profileSource = { flow {} },
            loadCards = { listOf(HomeCardModel(1, HomeCard.CARD_GRADES), HomeCardModel(1, HomeCard.CARD_NOTES)) },
            saveCards = { saved.add(it) }, availableFeatures = emptySet(), updateAvailable = false,
            locked = false, seedProfile = ProfileInputs(7, "Jan", false), today = today, config = cfg,
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

    /**
     * The reproduction. Before this phase `studentNumber` was a constructor val, so the card kept
     * saying "click to set" after the user set a number, until the fragment was rebuilt.
     */
    @Test
    fun `a new studentNumber from the profile flow reaches uiState`() = runTest(dispatcher) {
        val profiles = MutableSharedFlow<Profile?>(replay = 1)
        val model = vm(
            initialCards = listOf(HomeCardModel(1, HomeCard.CARD_LUCKY_NUMBER)),
            profileSource = { profiles },
        )
        val job = launch { model.uiState.collect {} }
        profiles.emit(profile(studentNumber = -1))
        advanceUntilIdle()
        assertEquals(
            R.string.home_lucky_number_details_click_to_set,
            luckyCard(model.uiState.value as HomeUiState.Content).subTextRes,
        )

        profiles.emit(profile(studentNumber = 13))
        advanceUntilIdle()
        val card = luckyCard(model.uiState.value as HomeUiState.Content)
        assertEquals(R.string.home_lucky_number_details, card.subTextRes)
        assertEquals(listOf<Any>("Jan", 13), card.subTextArgs)
        job.cancel()
    }

    @Test
    fun `uiState emits from the seed before the profile flow emits`() = runTest(dispatcher) {
        val model = vm(
            initialCards = listOf(HomeCardModel(1, HomeCard.CARD_LUCKY_NUMBER)),
            profileSource = { flow {} },          // never emits
        )
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        val card = luckyCard(model.uiState.value as HomeUiState.Content)
        assertEquals(listOf<Any>("Jan", 7), card.subTextArgs)   // the ctor seed
        job.cancel()
    }

    /**
     * Ordering, not value: the seed must come FIRST. The source must COMPLETE — with a
     * MutableSharedFlow the `onCompletion` mutation in Task 4 never fires and this gate cannot
     * discriminate.
     */
    @Test
    fun `the first real profile supersedes the seed`() = runTest(dispatcher) {
        val model = vm(
            initialCards = listOf(HomeCardModel(1, HomeCard.CARD_LUCKY_NUMBER)),
            profileSource = { flowOf(profile(studentNumber = 21, name = "Ola")) },
        )
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        val card = luckyCard(model.uiState.value as HomeUiState.Content)
        assertEquals(listOf<Any>("Ola", 21), card.subTextArgs)
        assertNotEquals(listOf<Any>("Jan", 7), card.subTextArgs)
        job.cancel()
    }

    /**
     * Drives `profileInputsFlow` directly rather than `uiState`: `uiState` ends in `stateIn`, whose
     * StateFlow conflates equal values, so `distinctUntilChanged`'s effect is invisible there.
     * Mirrors ConfigChangesTest's equivalent gate.
     */
    @Test
    fun `an identical profile does not re-emit`() = runTest(dispatcher) {
        val profiles = MutableSharedFlow<Profile?>(replay = 1)
        val seen = mutableListOf<ProfileInputs>()
        val job = launch {
            profileInputsFlow(profiles, ProfileInputs(7, "Jan", false)).collect { seen += it }
        }
        advanceUntilIdle()

        profiles.emit(profile(studentNumber = 7))   // identical to the seed
        advanceUntilIdle()
        profiles.emit(profile(studentNumber = 7))   // and again
        advanceUntilIdle()

        assertEquals(listOf(ProfileInputs(7, "Jan", false)), seen)
        job.cancel()
    }

    /**
     * Ordering: the seed must come FIRST. The source must COMPLETE — against a MutableSharedFlow the
     * `onCompletion` mutation in Task 4 can never fire, so the gate could not discriminate.
     */
    @Test
    fun `profileInputsFlow emits the seed before the first real profile`() = runTest(dispatcher) {
        val seen = mutableListOf<ProfileInputs>()
        val job = launch {
            profileInputsFlow(
                flowOf(profile(studentNumber = 21, name = "Ola")),
                ProfileInputs(7, "Jan", false),
            ).collect { seen += it }
        }
        advanceUntilIdle()
        assertEquals(
            listOf(ProfileInputs(7, "Jan", false), ProfileInputs(21, "Ola", false)),
            seen,
        )
        job.cancel()
    }

    @Test
    fun `profileInputsFlow skips a null profile rather than blanking it`() = runTest(dispatcher) {
        val profiles = MutableSharedFlow<Profile?>(replay = 1)
        val seen = mutableListOf<ProfileInputs>()
        val job = launch {
            profileInputsFlow(profiles, ProfileInputs(7, "Jan", false)).collect { seen += it }
        }
        advanceUntilIdle()
        profiles.emit(profile(studentNumber = 13))
        advanceUntilIdle()
        profiles.emit(null)
        advanceUntilIdle()
        assertEquals(
            listOf(ProfileInputs(7, "Jan", false), ProfileInputs(13, "Jan", false)),
            seen,
        )                                           // the null produced nothing at all
        job.cancel()
    }

    /**
     * The `archived` third of the seam. studentNumber and name deliberately MATCH the seed, so
     * `archived` is the only field that differs — this reddens for the archived wiring alone and
     * cannot be satisfied via studentNumber or profileName.
     */
    @Test
    fun `archived from the profile flow pins the Archive card`() = runTest(dispatcher) {
        val profiles = MutableSharedFlow<Profile?>(replay = 1)
        val model = vm(
            initialCards = listOf(HomeCardModel(1, HomeCard.CARD_LUCKY_NUMBER)),
            profileSource = { profiles },
        )
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(
            listOf(HomeCard.CARD_LUCKY_NUMBER),
            (model.uiState.value as HomeUiState.Content).cards.map { it.cardId },
        )                                           // seed: archived = false

        profiles.emit(profile(studentNumber = 7, name = "Jan", archived = true))
        advanceUntilIdle()
        assertEquals(
            listOf(101, HomeCard.CARD_LUCKY_NUMBER),
            (model.uiState.value as HomeUiState.Content).cards.map { it.cardId },
        )                                           // HomeBuilder pins Wrapped(101) when archived
        job.cancel()
    }

    @Test
    fun `a null profile leaves the last good values on screen`() = runTest(dispatcher) {
        val profiles = MutableSharedFlow<Profile?>(replay = 1)
        val model = vm(initialCards = listOf(HomeCardModel(1, HomeCard.CARD_LUCKY_NUMBER)), profileSource = { profiles })
        val job = launch { model.uiState.collect {} }
        profiles.emit(profile(studentNumber = 13))
        advanceUntilIdle()
        profiles.emit(null)
        advanceUntilIdle()
        val card = luckyCard(model.uiState.value as HomeUiState.Content)
        assertEquals(listOf<Any>("Jan", 13), card.subTextArgs)   // not cleared
        job.cancel()
    }

    /**
     * The reproduction. Swiping the last card away used to persist an empty slice, and the NEXT
     * construction's seedIfEmpty saw empty and wrote back the full defaults — every removal the user
     * had made was undone, with no message.
     *
     * `store` is shared by loadCards and saveCards on purpose: the vm() helper keeps them separate,
     * so a rebuild there re-reads its own argument and could not see the write at all. `defaults`
     * must differ from what is stored, or the rebuild assertion passes even with the bug.
     */
    @Test
    fun `removing the last card persists nothing and survives a rebuild`() = runTest(dispatcher) {
        val store = mutableListOf(HomeCardModel(1, HomeCard.CARD_NOTES))
        val defaults = listOf(HomeCardModel(1, HomeCard.CARD_LUCKY_NUMBER), HomeCardModel(1, HomeCard.CARD_NOTES))
        fun build() = HomeViewModel(
            luckyNumberSource = { flowOf<LuckyNumberFull?>(null) }, eventsSource = { flowOf(emptyList()) },
            gradesSource = { flowOf(emptyList()) }, notesSource = { flowOf(emptyList()) },
            timetableSource = { flowOf(emptyList()) }, profileSource = { flowOf(profile(studentNumber = 7)) },
            loadCards = { store.toList() },
            saveCards = { store.clear(); store.addAll(it) },
            availableFeatures = allFeatures, seedProfile = ProfileInputs(7, "Jan", false),
            updateAvailable = false, locked = false, today = today, config = cfg,
            defaultCards = defaults, profileId = 1, dispatcher = dispatcher,
        )

        val model = build()
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()

        assertFalse(model.removeCard(HomeCard.CARD_NOTES))
        advanceUntilIdle()
        assertEquals(listOf(HomeCard.CARD_NOTES), store.map { it.cardId })
        job.cancel()

        val rebuilt = build()
        val job2 = launch { rebuilt.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(
            listOf(HomeCard.CARD_NOTES),
            (rebuilt.uiState.value as HomeUiState.Content).cards.map { it.cardId },
        )
        job2.cancel()
    }
}
