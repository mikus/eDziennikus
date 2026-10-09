/*
 * Copyright (c) Mikolaj Olszewski 2026-6-30.
 */

package eu.mikus.edziennik.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import eu.mikus.edziennik.App
import eu.mikus.edziennik.BuildConfig
import eu.mikus.edziennik.config.Config
import eu.mikus.edziennik.config.ProfileConfig
import eu.mikus.edziennik.config.ProfileConfigUI
import eu.mikus.edziennik.config.configFlow
import eu.mikus.edziennik.data.db.enums.FeatureType
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.data.db.full.GradeFull
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.data.db.full.LuckyNumberFull
import eu.mikus.edziennik.data.db.entity.Note
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.ext.getStudentData
import eu.mikus.edziennik.ext.hasUIFeature
import eu.mikus.edziennik.utils.models.Date
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * The [Profile] fields HomeBuilder renders, in one value so [distinctUntilChanged] compares them
 * structurally and `combine`'s arity does not grow per field. The field list is named in three
 * places — here, [profileInputsFlow]'s `map`, and the Factory that builds the seed.
 *
 * Public rather than `internal` because [HomeViewModel]'s constructor takes one, and a public
 * constructor cannot expose an internal parameter type. Mirrors `GradesInputs`.
 */
data class ProfileInputs(
    val studentNumber: Int,
    val profileName: String,
    val archived: Boolean,
)

/**
 * [ProfileInputs] re-derived whenever the profile row changes, primed with [seed].
 *
 * The priming emission is what makes this safe inside a `combine`, which produces nothing until every
 * input has emitted — and here it is load-bearing for a stronger reason than latency: `filterNotNull`
 * is upstream, so a profile row that does not exist emits nothing at all, and without [seed] Home
 * would stay on `HomeUiState.Loading` indefinitely. [seed] also re-fires whenever `WhileSubscribed`
 * restarts the upstream, not only on a cold start. Same shape as `configFlow`.
 *
 * Top-level so a test can drive the derivation without widening [HomeViewModel]'s surface. Its gate
 * collects this directly rather than `uiState`, because `uiState` ends in `stateIn`, whose StateFlow
 * conflates equal values — asserted through `uiState`, [distinctUntilChanged] would be invisible.
 */
internal fun profileInputsFlow(source: Flow<Profile?>, seed: ProfileInputs): Flow<ProfileInputs> =
    source.filterNotNull()
        .map { ProfileInputs(it.studentNumber, it.name, it.archived) }
        .onStart { emit(seed) }
        .distinctUntilChanged()

/**
 * Everything Home derives from config, in one value so [configFlow]'s `distinctUntilChanged` can
 * dedupe on it.
 *
 * Nine keys across BOTH config trees. Per-profile: `homeCardsLocked`, `agendaSubjectImportant`,
 * `homeEventsWeeks`, `homeEventsLimit`, `homeGradesWeeks`. Global: `update`, `bellSyncDiff`,
 * `bellSyncMultiplier`, `countInSeconds`. `App.config` and `App.profile.config` are distinct
 * `BaseConfig` instances with separate value maps, so the Factory passes BOTH to [configFlow] — a
 * reader of both that subscribes to one silently misses half its changes.
 *
 * [notPublic] is NOT config; it comes from `profile.getStudentData` and is captured. It rides along
 * because [HomeBuilder.Config] carries it, and re-reading a captured constant per config write is a
 * no-op.
 *
 * Public rather than `internal` because [HomeViewModel]'s constructor takes one, and a public
 * constructor cannot expose an internal parameter type (EXPOSED_PARAMETER_TYPE). Same reason
 * [ProfileInputs] above is public; mirrors `GradesInputs`.
 */
data class HomeInputs(
    val updateAvailable: Boolean,
    val locked: Boolean,
    val config: HomeBuilder.Config,
)

/**
 * Top-level so a test can drive it without a real [App], exactly as `readGradesInputs` is.
 *
 * `bellSyncDiffMillis` collapses TWO global keys through Time arithmetic into one field, so both
 * must be re-read here; a reader that captured the multiplier would never react to it changing.
 */
internal fun readHomeInputs(
    global: Config,
    profileConfig: ProfileConfig,
    notPublic: Boolean,
): HomeInputs {
    val ui = profileConfig.ui
    val tt = global.timetable
    val update = global.update
    val bellSyncDiffMillis = tt.bellSyncDiff?.let {
        (it.hour * 3600L + it.minute * 60L + it.second) * 1000L * tt.bellSyncMultiplier
    } ?: 0L
    return HomeInputs(
        updateAvailable = update != null && update.versionCode > BuildConfig.VERSION_CODE,
        locked = ui.homeCardsLocked,
        config = HomeBuilder.Config(
            agendaSubjectImportant = ui.agendaSubjectImportant,
            homeEventsLimit = ui.homeEventsLimit,
            homeEventsWeeks = ui.homeEventsWeeks,
            homeGradesWeeks = ui.homeGradesWeeks,
            bellSyncDiffMillis = bellSyncDiffMillis,
            countInSeconds = tt.countInSeconds,
            notPublic = notPublic,
        ),
    )
}

class HomeViewModel(
    luckyNumberSource: () -> Flow<LuckyNumberFull?>,
    eventsSource: () -> Flow<List<EventFull>>,
    gradesSource: () -> Flow<List<GradeFull>>,
    notesSource: () -> Flow<List<Note>>,
    timetableSource: () -> Flow<List<LessonFull>>,
    profileSource: () -> Flow<Profile?>,
    seedProfile: ProfileInputs,
    private val loadCards: () -> List<HomeCardModel>?,
    private val saveCards: (List<HomeCardModel>) -> Unit,
    private val availableFeatures: Set<FeatureType>,
    inputs: Flow<HomeInputs>,
    profileConfig: ProfileConfig,
    private val today: Date,
    private val defaultCards: List<HomeCardModel>,
    private val profileId: Int,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    /**
     * The `?: defaultCards` fallback is defensive, not load-bearing: [init] below guarantees a stored
     * row before anything collects this, so `loadCards()` is non-null by then. A mutation replacing
     * it with `emptyList()` reddens nothing — it survives only because [saveCards] is an injected
     * seam a caller could stub out.
     */
    private val cardsFlow = configFlow(profileConfig) { loadCards() ?: defaultCards }

    /** The mutators need the value the delegate cache holds *now*, not the last emission. */
    private fun cardsNow(): List<HomeCardModel> = loadCards() ?: defaultCards

    init {
        // Seed once per profile, ever. `has` flips to true permanently on the first set, whereas
        // emptiness is a value the write itself changes — an emptiness-gated write inside the reader
        // would be a feedback edge, because the reader now runs on EVERY config write.
        if (!profileConfig.has("homeCards")) saveCards(defaultCards)
    }

    private val dataFlow = combine(
        luckyNumberSource(), eventsSource(), gradesSource(), notesSource(), timetableSource(),
    ) { lucky, events, grades, notes, timetable ->
        HomeBuilder.Data(lucky, events, grades, notes, timetable)
    }

    private val profileFlow = profileInputsFlow(profileSource(), seedProfile)

    val uiState = combine(dataFlow, cardsFlow, profileFlow, inputs) { data, cards, profile, cfg ->
        HomeBuilder.build(
            cards = cards, availableFeatures = availableFeatures, archived = profile.archived,
            updateAvailable = cfg.updateAvailable, locked = cfg.locked, studentNumber = profile.studentNumber,
            profileName = profile.profileName, today = today, config = cfg.config, data = data,
        )
    }.flowOn(dispatcher).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    fun reorder(fromId: Int, toId: Int) = update(HomeCardOrder.swap(cardsNow(), fromId, toId))

    /**
     * Returns false when the removal was refused — [HomeCardOrder.remove] returns null rather than
     * persist an empty slice. HomeScreen resets its SwipeToDismissBox on false so the card springs
     * back; swallowing it would leave the card swiped off a list that still contains it.
     */
    fun removeCard(cardId: Int): Boolean {
        val kept = HomeCardOrder.remove(cardsNow(), cardId) ?: return false
        update(kept)
        return true
    }

    /**
     * The write runs on the calling thread on purpose. With the card list read from config rather
     * than a StateFlow, `cardsNow()` reads the delegate cache, and the cache only reflects a write
     * that has actually run — so a deferred write leaves a window where a second drag step computes
     * from the pre-write list and silently discards the first. (Other writers reach the same cache:
     * HomeCardsDialog and ProfileConfigMigration both assign `ui.homeCards`. They are not racing
     * this, but the cache is not this class's private channel.) Reproduced by `a second reorder before the write lands computes
     * from a stale list` before this line was changed; its control, `two reorders with the first
     * write landed in between compose in order`, pins that the expectation itself is right.
     *
     * The expensive half is already off this thread: BaseConfig.set does the DB row on
     * Dispatchers.IO. What runs here is a gson serialize of a <=5-element list, which every config
     * dialog already does on main.
     */
    private fun update(newCards: List<HomeCardModel>) {
        if (newCards == cardsNow()) return
        saveCards(newCards)
    }

    class Factory(appContext: Context) : ViewModelProvider.Factory {
        private val app = appContext.applicationContext as App

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val profile = app.profile
            val profileId = App.profileId
            val ui = profile.config.ui
            val today = Date.getToday()
            val gradesFrom = Date.getToday().stepForward(0, 0, -ProfileConfigUI.MAX_HOME_GRADES_WEEKS * 7)
            val available = setOf(
                FeatureType.LUCKY_NUMBER, FeatureType.TIMETABLE, FeatureType.AGENDA, FeatureType.GRADES,
            ).filter { profile.hasUIFeature(it) }.toSet()
            val defaults = listOfNotNull(
                HomeCardModel(profileId, HomeCard.CARD_LUCKY_NUMBER).takeIf { profile.hasUIFeature(FeatureType.LUCKY_NUMBER) },
                HomeCardModel(profileId, HomeCard.CARD_TIMETABLE).takeIf { profile.hasUIFeature(FeatureType.TIMETABLE) },
                HomeCardModel(profileId, HomeCard.CARD_EVENTS).takeIf { profile.hasUIFeature(FeatureType.AGENDA) },
                HomeCardModel(profileId, HomeCard.CARD_GRADES).takeIf { profile.hasUIFeature(FeatureType.GRADES) },
                HomeCardModel(profileId, HomeCard.CARD_NOTES),
            )
            val notPublic = profile.getStudentData("timetableNotPublic", false)
            return HomeViewModel(
                luckyNumberSource = { app.db.luckyNumberDao().getNearestFuture(profileId, today).asFlow() },
                eventsSource = {
                    app.db.eventDao().getNearestNotDone(profileId, today, ProfileConfigUI.MAX_HOME_EVENTS_LIMIT).asFlow()
                        .map { list -> list.onEach { it.filterNotes() } }
                },
                gradesSource = { app.db.gradeDao().getAllFromDate(profileId, gradesFrom).asFlow() },
                notesSource = { app.db.noteDao().getAllNoOwner(profileId).asFlow() },
                timetableSource = {
                    if (FeatureType.TIMETABLE in available && !notPublic)
                        app.db.timetableDao().getBetweenDates(today, today.clone().stepForward(0, 0, 7)).asFlow()
                            .map { list -> list.filter { it.profileId == profileId } }   // getBetweenDates is cross-profile
                    else flowOf(emptyList())
                },
                profileSource = { app.db.profileDao().getById(profileId).asFlow() },
                seedProfile = ProfileInputs(profile.studentNumber, profile.name, profile.archived),
                loadCards = {
                    if (profile.config.has("homeCards")) ui.homeCards.filter { it.profileId == profileId }
                    else null
                },
                saveCards = { cards -> ui.homeCards = HomeCardOrder.mergeForProfile(ui.homeCards, profileId, cards) },
                availableFeatures = available,
                inputs = configFlow(app.config, profile.config) { readHomeInputs(app.config, profile.config, notPublic) },
                profileConfig = profile.config,
                today = today,
                defaultCards = defaults,
                profileId = profileId,
            ) as T
        }
    }
}
