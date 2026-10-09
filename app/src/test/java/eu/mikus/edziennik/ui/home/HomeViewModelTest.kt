/*
 * Copyright (c) Mikolaj Olszewski 2026-6-30.
 */

package eu.mikus.edziennik.ui.home

import eu.mikus.edziennik.R
import eu.mikus.edziennik.config.ProfileConfig
import eu.mikus.edziennik.config.ProfileConfigUI
import eu.mikus.edziennik.data.db.AppDb
import eu.mikus.edziennik.data.db.enums.FeatureType
import eu.mikus.edziennik.data.db.enums.LoginType
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.data.db.full.GradeFull
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.data.db.full.LuckyNumberFull
import eu.mikus.edziennik.data.db.entity.Note
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.utils.models.Date
import io.mockk.every
import io.mockk.mockk
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
        agendaSubjectImportant = false,
        homeEventsLimit = ProfileConfigUI.MAX_HOME_EVENTS_LIMIT, homeEventsWeeks = 4,
        homeGradesWeeks = ProfileConfigUI.MAX_HOME_GRADES_WEEKS,
        bellSyncDiffMillis = 0L, countInSeconds = false, notPublic = false,
    )
    private val defaultInputs = HomeInputs(updateAvailable = false, locked = false, config = cfg)
    private val allFeatures = setOf(FeatureType.LUCKY_NUMBER, FeatureType.TIMETABLE, FeatureType.AGENDA, FeatureType.GRADES)

    /**
     * A real [ProfileConfig] with no rows, built fresh on EVERY call and never held in a field. A
     * shared instance would carry `homeCards` from one test into the next, and the init seed's
     * `has("homeCards")` gate would then pass or fail depending on execution order. The db is a
     * relaxed mock, so a write lands in the in-memory `values` map and the delegate cache and
     * nowhere else. Same shape as GradesInputsTest and HomeInputsTest.
     */
    private fun freshProfileConfig(): ProfileConfig {
        val db = mockk<AppDb>(relaxed = true)
        every { db.configDao().getAllNow() } returns emptyList()
        return ProfileConfig(db, 1, emptyList())   // entries != null skips getAllNow
    }

    /**
     * Mirrors the loadCards lambda in `HomeViewModel.Factory.create`, which is inline there and so
     * cannot be called from a test: `null` is "this profile has no stored row", `emptyList()` is
     * "stored, deliberately empty". If the Factory's lambda changes, this copy does not follow it.
     */
    private fun ProfileConfig.loadHomeCards(): List<HomeCardModel>? =
        if (has("homeCards")) ui.homeCards.filter { it.profileId == 1 } else null

    /** Mirrors the saveCards lambda in `HomeViewModel.Factory.create`; same caveat as [loadHomeCards]. */
    private fun ProfileConfig.saveHomeCards(cards: List<HomeCardModel>) {
        ui.homeCards = HomeCardOrder.mergeForProfile(ui.homeCards, 1, cards)
    }

    /**
     * [initialCards] is the stored `homeCards` row, written into [profileConfig] before the
     * ViewModel is built. `null` writes nothing, so there is NO row and the init seed fires;
     * `emptyList()` IS a row (stored, deliberately empty) and must not seed.
     *
     * loadCards and saveCards both go through [profileConfig], so a write is visible to the next read
     * and to a rebuild that shares the config. [saved] logs each saveCards call; it is not the store.
     * Pass a [profileConfig] you built yourself to read the store back or to share it between builds.
     */
    private fun vm(
        initialCards: List<HomeCardModel>?,
        defaults: List<HomeCardModel> = listOf(HomeCardModel(1, HomeCard.CARD_NOTES)),
        saved: MutableList<List<HomeCardModel>> = mutableListOf(),
        profileSource: () -> Flow<Profile?> = { flowOf(profile(studentNumber = 7)) },
        inputs: Flow<HomeInputs> = flowOf(defaultInputs),
        profileConfig: ProfileConfig = freshProfileConfig(),
    ): HomeViewModel {
        if (initialCards != null) profileConfig.ui.homeCards = initialCards
        return HomeViewModel(
            luckyNumberSource = { flowOf<LuckyNumberFull?>(null) },
            eventsSource = { flowOf(emptyList<EventFull>()) },
            gradesSource = { flowOf(emptyList<GradeFull>()) },
            notesSource = { flowOf(emptyList<Note>()) },
            timetableSource = { flowOf(emptyList<LessonFull>()) },
            profileSource = profileSource,
            loadCards = { profileConfig.loadHomeCards() },
            saveCards = { saved.add(it); profileConfig.saveHomeCards(it) },
            availableFeatures = allFeatures,
            inputs = inputs,
            profileConfig = profileConfig,
            seedProfile = ProfileInputs(7, "Jan", false),
            today = today,
            defaultCards = defaults,
            profileId = 1,
            dispatcher = dispatcher,
        )
    }

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

    /**
     * The seed gate is whether the profile has a `homeCards` ROW, not whether the list is empty.
     * `uiState` would show the defaults here even if nothing were seeded — the reader falls back to
     * them for a missing row — so the assertions that discriminate are the write count and the
     * config's own `has`/value, not the cards on screen.
     */
    @Test
    fun `seeds the defaults once when the profile has no stored homeCards row`() = runTest(dispatcher) {
        val saved = mutableListOf<List<HomeCardModel>>()
        val profileConfig = freshProfileConfig()
        val model = vm(
            initialCards = null,
            defaults = listOf(HomeCardModel(1, HomeCard.CARD_NOTES)),
            saved = saved,
            profileConfig = profileConfig,
        )
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(listOf(HomeCard.CARD_NOTES), (model.uiState.value as HomeUiState.Content).cards.map { it.cardId })
        assertEquals(1, saved.size)
        assertEquals(listOf(HomeCard.CARD_NOTES), saved.single().map { it.cardId })
        assertTrue(profileConfig.has("homeCards"))   // the row exists now, so the next construction will not seed
        assertEquals(listOf(HomeCard.CARD_NOTES), profileConfig.ui.homeCards.map { it.cardId })
        job.cancel()
    }

    /**
     * The deliberate behaviour change of this phase. An empty list used to mean "never configured",
     * so a stored empty slice was reseeded with the defaults on the next build. The signal is now the
     * row itself: a row holding an empty slice is the user's choice, and it stays.
     */
    @Test
    fun `a stored empty list stays empty and is not reseeded`() = runTest(dispatcher) {
        val saved = mutableListOf<List<HomeCardModel>>()
        val profileConfig = freshProfileConfig()
        val model = vm(
            initialCards = emptyList(),
            defaults = listOf(HomeCardModel(1, HomeCard.CARD_NOTES)),
            saved = saved,
            profileConfig = profileConfig,
        )
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(profileConfig.has("homeCards"))   // a row, holding an empty slice
        assertTrue(profileConfig.ui.homeCards.isEmpty())
        assertTrue((model.uiState.value as HomeUiState.Content).cards.isEmpty())
        assertTrue(saved.isEmpty())        // nothing was written: no seed
        job.cancel()
    }

    @Test
    fun `reorder swaps and persists`() = runTest(dispatcher) {
        val saved = mutableListOf<List<HomeCardModel>>()
        val profileConfig = freshProfileConfig()
        val model = vm(initialCards = listOf(HomeCardModel(1, 3), HomeCardModel(1, 5)), saved = saved, profileConfig = profileConfig)
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        model.reorder(fromId = 3, toId = 5)
        advanceUntilIdle()
        assertEquals(listOf(5, 3), (model.uiState.value as HomeUiState.Content).cards.map { it.cardId })
        assertEquals(listOf(5, 3), saved.last().map { it.cardId })
        assertEquals(listOf(5, 3), profileConfig.ui.homeCards.map { it.cardId })   // read back from the store the write went to
        job.cancel()
    }

    @Test
    fun `removeCard drops and persists`() = runTest(dispatcher) {
        val saved = mutableListOf<List<HomeCardModel>>()
        val profileConfig = freshProfileConfig()
        val model = vm(initialCards = listOf(HomeCardModel(1, 3), HomeCardModel(1, 5)), saved = saved, profileConfig = profileConfig)
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(model.removeCard(cardId = 3))
        advanceUntilIdle()
        assertEquals(listOf(5), (model.uiState.value as HomeUiState.Content).cards.map { it.cardId })
        assertEquals(listOf(5), saved.last().map { it.cardId })
        assertEquals(listOf(5), profileConfig.ui.homeCards.map { it.cardId })   // read back from the store the write went to
        job.cancel()
    }

    /**
     * Two reorders without the dispatcher running in between. `cardsNow()` reads the config delegate
     * cache, which only `saveCards` updates — so while that write is still queued on the dispatcher,
     * the second reorder computes from the PRE-first-reorder list and silently produces the wrong
     * order. Reproduced before changing where saveCards runs; see the commit body.
     */
    @Test
    fun `a second reorder before the write lands computes from a stale list`() = runTest(dispatcher) {
        val profileConfig = freshProfileConfig()
        val model = vm(
            initialCards = listOf(HomeCardModel(1, 1), HomeCardModel(1, 2), HomeCardModel(1, 3)),
            profileConfig = profileConfig,
        )

        // HomeCardOrder.swap exchanges by card ID, not by position. Applied one after the other:
        //   [1, 2, 3] --swap(1, 2)--> [2, 1, 3] --swap(2, 3)--> [3, 1, 2]
        model.reorder(fromId = 1, toId = 2)
        model.reorder(fromId = 2, toId = 3)   // no advanceUntilIdle() between: the first write is still queued
        advanceUntilIdle()

        assertEquals(listOf(3, 1, 2), profileConfig.ui.homeCards.map { it.cardId })
    }

    /**
     * Control for the test above: the same two swaps with the first write allowed to land in
     * between. It pins the expected order as the true serial result of HomeCardOrder.swap, so a red
     * race test is red because of the interleaving and not because the arithmetic was wrong.
     */
    @Test
    fun `two reorders with the first write landed in between compose in order`() = runTest(dispatcher) {
        val profileConfig = freshProfileConfig()
        val model = vm(
            initialCards = listOf(HomeCardModel(1, 1), HomeCardModel(1, 2), HomeCardModel(1, 3)),
            profileConfig = profileConfig,
        )

        model.reorder(fromId = 1, toId = 2)
        advanceUntilIdle()
        model.reorder(fromId = 2, toId = 3)
        advanceUntilIdle()

        assertEquals(listOf(3, 1, 2), profileConfig.ui.homeCards.map { it.cardId })
    }

    @Test
    fun `gated-off card stays persisted but is hidden`() = runTest(dispatcher) {
        val saved = mutableListOf<List<HomeCardModel>>()
        val profileConfig = freshProfileConfig().also {
            it.ui.homeCards = listOf(HomeCardModel(1, HomeCard.CARD_GRADES), HomeCardModel(1, HomeCard.CARD_NOTES))
        }
        val model = HomeViewModel(
            luckyNumberSource = { flowOf<LuckyNumberFull?>(null) }, eventsSource = { flowOf(emptyList()) },
            gradesSource = { flowOf(emptyList()) }, notesSource = { flowOf(emptyList()) },
            timetableSource = { flowOf(emptyList()) },
            profileSource = { flow {} },
            loadCards = { profileConfig.loadHomeCards() },
            saveCards = { saved.add(it); profileConfig.saveHomeCards(it) }, availableFeatures = emptySet(), inputs = flowOf(defaultInputs),
            profileConfig = profileConfig,
            seedProfile = ProfileInputs(7, "Jan", false), today = today,
            defaultCards = emptyList(), profileId = 1, dispatcher = dispatcher,
        )
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(listOf(HomeCard.CARD_NOTES), (model.uiState.value as HomeUiState.Content).cards.map { it.cardId })
        model.removeCard(HomeCard.CARD_NOTES)
        advanceUntilIdle()
        assertTrue(saved.last().any { it.cardId == HomeCard.CARD_GRADES })
        assertEquals(listOf(HomeCard.CARD_GRADES), profileConfig.ui.homeCards.map { it.cardId })   // what the store holds
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
     * construction's emptiness-gated seed saw empty and wrote back the full defaults — every removal
     * the user had made was undone, with no message.
     *
     * `profileConfig` is shared by both builds and backs both loadCards and saveCards, so the rebuild
     * reads the same store the first build wrote — the round trip the Factory makes through
     * `profile.config`. `defaults` must differ from what is stored, or the rebuild assertion passes
     * even if the rebuild seeds.
     */
    @Test
    fun `removing the last card persists nothing and survives a rebuild`() = runTest(dispatcher) {
        val profileConfig = freshProfileConfig().also { it.ui.homeCards = listOf(HomeCardModel(1, HomeCard.CARD_NOTES)) }
        val defaults = listOf(HomeCardModel(1, HomeCard.CARD_LUCKY_NUMBER), HomeCardModel(1, HomeCard.CARD_NOTES))
        fun build() = HomeViewModel(
            luckyNumberSource = { flowOf<LuckyNumberFull?>(null) }, eventsSource = { flowOf(emptyList()) },
            gradesSource = { flowOf(emptyList()) }, notesSource = { flowOf(emptyList()) },
            timetableSource = { flowOf(emptyList()) }, profileSource = { flowOf(profile(studentNumber = 7)) },
            loadCards = { profileConfig.loadHomeCards() },
            saveCards = { profileConfig.saveHomeCards(it) },
            availableFeatures = allFeatures, inputs = flowOf(defaultInputs),
            profileConfig = profileConfig,
            seedProfile = ProfileInputs(7, "Jan", false), today = today,
            defaultCards = defaults, profileId = 1, dispatcher = dispatcher,
        )

        val model = build()
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()

        assertFalse(model.removeCard(HomeCard.CARD_NOTES))
        advanceUntilIdle()
        assertEquals(listOf(HomeCard.CARD_NOTES), profileConfig.ui.homeCards.map { it.cardId })
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

    /**
     * The point of the phase: a config write re-derives uiState on the SAME ViewModel. `locked` and
     * `config` used to be constructor values, so a change reached the screen only once the fragment
     * (and with it the ViewModel) was rebuilt. `locked` is the field that differs because
     * HomeUiState.Content carries it straight through — no card data has to move for the assertion
     * to see it. Mirrors AttendanceViewModelTest's `re-derives when the config flow emits a new value`.
     */
    @Test
    fun `a config change re-derives uiState without rebuilding`() = runTest(dispatcher) {
        val inputs = MutableSharedFlow<HomeInputs>(replay = 1)
        inputs.emit(defaultInputs)
        val model = vm(initialCards = listOf(HomeCardModel(1, HomeCard.CARD_NOTES)), inputs = inputs)
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertFalse((model.uiState.value as HomeUiState.Content).locked)

        inputs.emit(defaultInputs.copy(locked = true))
        advanceUntilIdle()
        assertTrue((model.uiState.value as HomeUiState.Content).locked)   // same `model`, never rebuilt
        job.cancel()
    }
}
