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

/**
 * Pure aggregation for the Home dashboard. Owns gating + pinned-prepend + ordering policy: from the
 * full per-profile [cards] list (ungated) it emits the display-ordered List<HomeCardUi>. An
 * unavailable feature's card is OMITTED from the UI but is never removed from [cards] (persistence
 * keeps it). Pinned cards (Availability 102, then Archive 101) prepend, matching legacy.
 */
object HomeBuilder {

    data class Config(
        val agendaSubjectImportant: Boolean,
        val homeEventsLimit: Int,
        val homeEventsWeeks: Int,
        val homeGradesWeeks: Int,
        val bellSyncDiffMillis: Long,
        val countInSeconds: Boolean,
        val notPublic: Boolean,
    )

    data class Data(
        val luckyNumber: LuckyNumberFull?,
        val events: List<EventFull>,
        val grades: List<GradeFull>,
        val notes: List<Note>,
        val timetableLessons: List<LessonFull>,
    )

    fun build(
        cards: List<HomeCardModel>,
        availableFeatures: Set<FeatureType>,
        archived: Boolean,
        updateAvailable: Boolean,
        locked: Boolean,
        studentNumber: Int,
        profileName: String,
        today: Date,
        config: Config,
        data: Data,
    ): HomeUiState.Content {
        val pinned = buildList {
            if (updateAvailable) add(HomeCardUi.Wrapped(102))
            if (archived) add(HomeCardUi.Wrapped(101))
        }

        val userCards = cards.mapNotNull { model ->
            when (model.cardId) {
                HomeCard.CARD_LUCKY_NUMBER ->
                    if (FeatureType.LUCKY_NUMBER in availableFeatures)
                        luckyCard(data.luckyNumber, studentNumber, profileName, today) else null
                HomeCard.CARD_TIMETABLE ->
                    if (FeatureType.TIMETABLE in availableFeatures)
                        HomeCardUi.Timetable(
                            lessons = data.timetableLessons,
                            notPublic = config.notPublic,
                            bellSyncDiffMillis = config.bellSyncDiffMillis,
                            countInSeconds = config.countInSeconds,
                            today = today,
                        ) else null
                HomeCard.CARD_EVENTS ->
                    if (FeatureType.AGENDA in availableFeatures) eventsCard(data.events, today, config) else null
                HomeCard.CARD_GRADES ->
                    if (FeatureType.GRADES in availableFeatures)
                        HomeCardUi.Grades(HomeGradesGrouper.group(recentGrades(data.grades, today, config))) else null
                HomeCard.CARD_NOTES ->
                    HomeCardUi.Notes(data.notes.take(4))
                else -> null   // unknown/dev card ids: not shown by the native dashboard
            }
        }

        return HomeUiState.Content(pinned + userCards, locked)
    }

    private fun luckyCard(ln: LuckyNumberFull?, studentNumber: Int, profileName: String, today: Date): HomeCardUi.LuckyNumber {
        val ui = LuckyNumberMessage.build(
            luckyNumber = ln?.number, luckyDate = ln?.date, number = ln?.number ?: -1,
            today = today, studentNumber = studentNumber,
        )
        val (subRes, subArgs) = LuckyNumberMessage.subText(profileName, studentNumber)
        return HomeCardUi.LuckyNumber(ui.titleRes, ui.titleArgs, subRes, subArgs, ui.emojiRes)
    }

    private fun eventsCard(events: List<EventFull>, today: Date, config: Config): HomeCardUi.Events {
        val toDate = Date.fromValue(today.value).stepForward(0, 0, config.homeEventsWeeks * 7)
        // take mirrors the SQL LIMIT, which applied before any date window. The two operations
        // COMMUTE here and that is worth stating so nobody "fixes" the order: EventDao.ORDER_BY is
        // `ORDER BY eventDate, eventTime, addedDate ASC`, so the list arrives sorted ascending by
        // date; `filter { it.date <= toDate }` therefore keeps a prefix, `take` keeps a prefix, and
        // prefix-of-prefix is the shorter prefix either way. Order is chosen to match what SQL did,
        // not because the result depends on it.
        val rows = events.take(config.homeEventsLimit).filter { it.date <= toDate }
        return HomeCardUi.Events(
            rows = rows,
            showType = !config.agendaSubjectImportant,
            showSubject = config.agendaSubjectImportant,
        )
    }

    /**
     * The grades window, which used to be `addedDate > N` inside the @RawQuery. The query now fetches
     * a fixed ceiling of weeks and this narrows to the stored value.
     *
     * `Date.stepForward` mutates, so step a copy — never [today] itself, which the caller reuses.
     */
    private fun recentGrades(grades: List<GradeFull>, today: Date, config: Config): List<GradeFull> {
        val from = Date.fromValue(today.value).stepForward(0, 0, -config.homeGradesWeeks * 7)
        return grades.filter { it.addedDate > from.inMillis }
    }
}
