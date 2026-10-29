/*
 * Copyright (c) Mikolaj Olszewski 2026-6-29.
 */

package eu.mikus.edziennik.ui.attendance

import eu.mikus.edziennik.data.db.entity.AttendanceType
import eu.mikus.edziennik.data.db.full.AttendanceFull
import eu.mikus.edziennik.utils.models.Date

/** Summary period toggle. */
enum class Period { ALL, SEM1, SEM2 }

/**
 * Stable expand key, minted by the builder/aggregator and carried on each header.
 * The VM's expand set is keyed by this (equals/hashCode). [stableId] is a Bundle-storable string
 * the Screen uses for LazyColumn item keys — NodeKey itself is not Parcelable, and LazyColumn
 * requires Bundle-storable keys for its per-item saveable state.
 */
sealed interface NodeKey {
    val stableId: String

    data class SubjectKey(val subjectId: Long) : NodeKey {
        override val stableId get() = "S$subjectId"
    }
    data class MonthKey(val year: Int, val month: Int) : NodeKey {
        override val stableId get() = "M$year-$month"
    }
    /** Keyed on the whole type, not its id: two custom types share baseType.toLong() as id. */
    data class TypeKey(val type: AttendanceType) : NodeKey {
        override val stableId get() = "T${type.id}-${type.typeName}-${type.typeSymbol}"
    }
    /** anchor = the range END encoded as y*10000+m*100+d (stable under the consecutive-day merge). */
    data class DayRangeKey(val anchor: Long) : NodeKey {
        override val stableId get() = "D$anchor"
    }

    /**
     * The Days bucket of attendances whose date the school did not give. Not a [DayRangeKey]: the
     * parser files those rows at 2019-01-01, and a key minted from that date would collide with a
     * real 2019-01-01 bucket, so two headers would share one LazyColumn item key and it would throw.
     */
    data object UnknownDayKey : NodeKey {
        override val stableId get() = "D?"
    }

    /**
     * The Months twin of [UnknownDayKey], and a separate object on purpose: the VM's expand set is one
     * `Set<NodeKey>` shared by every tab, so a single shared key would expand both buckets at once.
     */
    data object UnknownMonthKey : NodeKey {
        override val stableId get() = "M?"
    }
}

/**
 * An immutable, AttendanceType.compareTo-ordered snapshot of the legacy typeCountMap.
 * Constructed only via [AttendanceCounts.snapshot], which supplies a freshly-built, canonically-sorted list.
 * A data class so it participates in structural equality (Compose recomposition skipping).
 */
data class CountSnapshot(val byType: List<TypeCount>)

data class TypeCount(val type: AttendanceType, val count: Int)

/** A single attendance entry. unseen is captured at build time. */
data class AttendanceLeaf(val attendance: AttendanceFull, val unseen: Boolean)

/** Summary overall stats. overallPercent is a 0f..1f fraction, null when there is no counted data. */
data class SummaryStats(val overallPercent: Float?, val counts: CountSnapshot)

data class SubjectHeader(
    val key: NodeKey,
    val subjectId: Long,
    val name: String,
    val counts: CountSnapshot,
    val percentage: Float?,          // present-counted / counted-total, 0f..1f (null when none counted)
    val hasUnseen: Boolean,
    val expanded: Boolean,
    val leaves: List<AttendanceLeaf>,
)

data class MonthHeader(
    val key: NodeKey,
    val year: Int,                   // the filing date's year when dateUnknown; never read then
    val month: Int,                  // likewise
    val counts: CountSnapshot,
    val percentage: Float?,          // same formula as SubjectHeader; null for the undated bucket
    val hasUnseen: Boolean,
    val expanded: Boolean,
    val leaves: List<AttendanceLeaf>,
) {
    /**
     * True for the bucket of attendances whose date the school did not give; [year] and [month] are
     * then the parser's filing date and must not be shown as a month.
     *
     * Derived from [key] and not stored, so the fact has one encoding here and cannot disagree with
     * the key that is also the LazyColumn item key and the expand-set member; `copy(expanded = ...)`
     * keeps it because it keeps the key. Derived from [key] and NOT from [leaves]: the builder counts
     * every row but drops `TYPE_PRESENT` leaves when `showPresenceInMonth` is off, so an undated
     * bucket of only present rows has no leaves, still has counts, and still renders. Reading the
     * leaves would report it as a real month and print "1.2019".
     */
    val dateUnknown get() = key == NodeKey.UnknownMonthKey
}

data class DayRangeHeader(
    val key: NodeKey,
    val rangeStart: Date,            // the filing date when dateUnknown; never read then
    val rangeEnd: Date,              // == rangeStart for a single day; == newest for a merged run
    val hasUnseen: Boolean,
    val expanded: Boolean,
    val leaves: List<AttendanceLeaf>,
) {
    /**
     * True for the bucket of attendances whose date the school did not give; [rangeStart] and
     * [rangeEnd] are then the parser's filing date and must not be shown as a day. Derived from
     * [key] for the reasons on [MonthHeader.dateUnknown].
     */
    val dateUnknown get() = key == NodeKey.UnknownDayKey
}

data class TypeHeader(
    val key: NodeKey,
    val type: AttendanceType,
    val sharePercent: Float?,        // this type's share of all rows (group/total), 0f..1f — NOT the present/counted formula
    val semesterCount: Int,
    val yearCount: Int,
    val hasUnseen: Boolean,
    val expanded: Boolean,
    val leaves: List<AttendanceLeaf>,
)

/** Concrete payload per tab; the Screen renders the matching composable. List order == tab order. */
sealed interface AttendanceTab {
    data class SummaryTab(val stats: SummaryStats, val subjects: List<SubjectHeader>) : AttendanceTab
    data class DaysTab(val dayRanges: List<DayRangeHeader>) : AttendanceTab
    data class MonthsTab(val months: List<MonthHeader>) : AttendanceTab
    data class TypesTab(val types: List<TypeHeader>) : AttendanceTab
    data class ListTab(val leaves: List<AttendanceLeaf>) : AttendanceTab
}

sealed interface AttendanceUiState {
    data object Loading : AttendanceUiState
    data object Empty : AttendanceUiState
    /** tabs in fixed order: Summary, Days, Months, Types, List (legacy page indices 0..4). */
    data class Content(val tabs: List<AttendanceTab>) : AttendanceUiState
}
