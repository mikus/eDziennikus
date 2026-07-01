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
import eu.mikus.edziennik.data.db.enums.FeatureType
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.data.db.full.GradeFull
import eu.mikus.edziennik.data.db.full.LuckyNumberFull
import eu.mikus.edziennik.data.db.entity.Note
import eu.mikus.edziennik.ext.hasUIFeature
import eu.mikus.edziennik.utils.models.Date
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    luckyNumberSource: () -> Flow<LuckyNumberFull?>,
    eventsSource: () -> Flow<List<EventFull>>,
    gradesSource: () -> Flow<List<GradeFull>>,
    notesSource: () -> Flow<List<Note>>,
    private val loadCards: () -> List<HomeCardModel>,
    private val saveCards: (List<HomeCardModel>) -> Unit,
    private val availableFeatures: Set<FeatureType>,
    private val archived: Boolean,
    private val updateAvailable: Boolean,
    private val locked: Boolean,
    private val studentNumber: Int,
    private val profileName: String,
    private val today: Date,
    private val config: HomeBuilder.Config,
    private val defaultCards: List<HomeCardModel>,
    private val profileId: Int,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _cards = MutableStateFlow(seedIfEmpty(loadCards()))

    val uiState = combine(
        luckyNumberSource(), eventsSource(), gradesSource(), notesSource(), _cards,
    ) { lucky, events, grades, notes, cards ->
        HomeBuilder.build(
            cards = cards, availableFeatures = availableFeatures, archived = archived,
            updateAvailable = updateAvailable, locked = locked, studentNumber = studentNumber,
            profileName = profileName, today = today, config = config,
            data = HomeBuilder.Data(lucky, events, grades, notes),
        )
    }.flowOn(dispatcher).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    fun reorder(fromId: Int, toId: Int) = update(HomeCardOrder.swap(_cards.value, fromId, toId))
    fun removeCard(cardId: Int) = update(HomeCardOrder.remove(_cards.value, cardId))

    private fun update(newCards: List<HomeCardModel>) {
        if (newCards == _cards.value) return
        _cards.value = newCards
        viewModelScope.launch(dispatcher) { saveCards(newCards) }
    }

    private fun seedIfEmpty(loaded: List<HomeCardModel>): List<HomeCardModel> {
        if (loaded.isNotEmpty()) return loaded
        viewModelScope.launch(dispatcher) { saveCards(defaultCards) }
        return defaultCards
    }

    class Factory(appContext: Context) : ViewModelProvider.Factory {
        private val app = appContext.applicationContext as App

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val profile = app.profile
            val profileId = App.profileId
            val ui = profile.config.ui
            val today = Date.getToday()
            val gradesFrom = Date.getToday().stepForward(0, 0, -ui.homeGradesWeeks * 7)
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
            val update = app.config.update
            return HomeViewModel(
                luckyNumberSource = { app.db.luckyNumberDao().getNearestFuture(profileId, today).asFlow() },
                eventsSource = {
                    app.db.eventDao().getNearestNotDone(profileId, today, ui.homeEventsLimit).asFlow()
                        .map { list -> list.onEach { it.filterNotes() } }
                },
                gradesSource = { app.db.gradeDao().getAllFromDate(profileId, gradesFrom).asFlow() },
                notesSource = { app.db.noteDao().getAllNoOwner(profileId).asFlow() },
                loadCards = { ui.homeCards.filter { it.profileId == profileId } },
                saveCards = { cards -> ui.homeCards = HomeCardOrder.mergeForProfile(ui.homeCards, profileId, cards) },
                availableFeatures = available,
                archived = profile.archived,
                updateAvailable = update != null && update.versionCode > BuildConfig.VERSION_CODE,
                locked = ui.homeCardsLocked,
                studentNumber = profile.studentNumber,
                profileName = profile.name,
                today = today,
                config = HomeBuilder.Config(ui.agendaSubjectImportant, ui.homeEventsWeeks),
                defaultCards = defaults,
                profileId = profileId,
            ) as T
        }
    }
}
