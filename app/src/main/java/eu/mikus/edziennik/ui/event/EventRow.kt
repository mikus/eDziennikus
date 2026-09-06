/*
 * Copyright (c) Mikolaj Olszewski 2026-6-24.
 */

package eu.mikus.edziennik.ui.event

import android.content.Context
import android.text.Spanned
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.ext.resolveAttr
import eu.mikus.edziennik.ui.compose.IconicsIcon
import eu.mikus.edziennik.ui.compose.toAnnotatedString
import eu.mikus.edziennik.ui.compose.withSearchHighlight
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Week

/**
 * [showTypeColor] draws the event's category colour as a dot at the head of the details line — the
 * in-Compose stand-in for the legacy `typeColor` circle (event_list_item.xml) and for the whole-card
 * tint the agenda's renderer used. The legacy adapter defaulted it to [showType]; here it defaults
 * off and every surface that wants it opts in, because homework deliberately shows neither (its
 * legacy adapter passed showType = false, so the circle was hidden there too).
 */
@Composable
fun EventRow(
    event: EventFull,
    unseen: Boolean,
    query: String = "",
    showWeekDay: Boolean = true,
    showDate: Boolean = true,
    showTime: Boolean = true,
    showSubject: Boolean = true,
    showType: Boolean = false,
    showTypeColor: Boolean = false,
    showNotes: Boolean = true,
    onClick: (EventFull) -> Unit,
    onEditClick: ((EventFull) -> Unit)? = null,
    onAppear: (EventFull) -> Unit = {},
) {
    LaunchedEffect(event.id) { onAppear(event) }

    val context = LocalContext.current
    val highlight = remember(context) { Color(R.attr.colorControlHighlight.resolveAttr(context)) }
    val details = remember(event, query, showWeekDay, showDate, showTime, showSubject, showType) {
        eventDetails(event, query, highlight, showWeekDay, showDate, showTime, showSubject, showType, context)
    }
    val addedBy = remember(event, query) { eventAddedBy(event, query, highlight, context) }

    Card(
        onClick = { onClick(event) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showTypeColor) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(event.eventColor)))
                    Spacer(Modifier.width(6.dp))
                }
                if (event.hasAttachments) {
                    IconicsIcon(
                        CommunityMaterial.Icon.cmd_attachment,
                        contentDescription = null,
                        sizeDp = 16,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (unseen) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                EventTopicText(event, query, highlight, showNotes)
                if (onEditClick != null && event.addedManually && !event.isDone) {
                    IconButton(onClick = { onEditClick(event) }) {
                        IconicsIcon(
                            CommunityMaterial.Icon3.cmd_pencil_outline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = addedBy,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The topic (in-Compose analog of EventManager.setEventTopic): leading manual/note Iconics glyphs +
 * (note-substitute ?: topicHtml) with search highlight, trailing green done-check. A RowScope extension so
 * the topic Text can take weight(1f) alongside the edit button. NOTE: this duplicates setEventTopic's
 * glyph/done policy, which stays alive for the legacy EventViewHolder (Agenda) — keep the two in sync until
 * EventViewHolder retires (pinned by the emulator pass).
 */
@Composable
private fun RowScope.EventTopicText(event: EventFull, query: String, highlight: Color, showNotes: Boolean) {
    if (event.addedManually) {
        IconicsIcon(CommunityMaterial.Icon.cmd_calendar_edit, contentDescription = null, sizeDp = 18, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
    }
    if (showNotes && event.hasNotes()) {
        val noteIcon = if (event.hasReplacingNotes()) CommunityMaterial.Icon3.cmd_swap_horizontal else CommunityMaterial.Icon3.cmd_playlist_edit
        IconicsIcon(noteIcon, contentDescription = null, sizeDp = 18, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
    }
    val topic = remember(event, query, showNotes) {
        val raw: CharSequence = event.getNoteSubstituteText(showNotes) ?: event.topicHtml
        (raw as? Spanned)?.toAnnotatedString()?.withSearchHighlight(query, highlight)
            ?: buildAnnotatedString { append(raw.toString()) }
    }
    Text(
        text = topic,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
    if (event.isDone) {
        Spacer(Modifier.width(4.dp))
        IconicsIcon(CommunityMaterial.Icon.cmd_check, contentDescription = null, sizeDp = 18, tint = Color(0xFF4CAF50))
    }
}

/** Bullet-joined details: weekday • relative-date • [type] • time • subject(highlighted). Pure (takes
 *  Context, resolved at the composable edge). Mirrors EventViewHolder.onBind's details line. */
private fun eventDetails(
    event: EventFull,
    query: String,
    highlight: Color,
    showWeekDay: Boolean,
    showDate: Boolean,
    showTime: Boolean,
    showSubject: Boolean,
    showType: Boolean,
    context: Context,
): AnnotatedString {
    val parts = buildList {
        if (showWeekDay) add(AnnotatedString(Week.getFullDayName(event.date.weekDay)))
        if (showDate) add(AnnotatedString(event.date.getRelativeString(context, 7) ?: event.date.formattedStringShort))
        if (showType && event.typeName != null) add(AnnotatedString(event.typeName!!))
        if (showTime) add(AnnotatedString(event.time?.stringHM ?: context.getString(R.string.event_all_day)))
        if (showSubject && event.subjectLongName != null) {
            add(AnnotatedString(event.subjectLongName!!).withSearchHighlight(query, highlight))
        }
    }
    val bullet = AnnotatedString(" • ")
    return parts.reduceOrNull { acc, part -> acc + bullet + part } ?: AnnotatedString("")
}

/** "Dodano <date> przez <teacher> • <team>" via the three legacy formats; query highlighted across the
 *  line (legacy highlights only the teacher name — this slight over-highlight is an accepted simplification). */
private fun eventAddedBy(event: EventFull, query: String, highlight: Color, context: Context): AnnotatedString {
    val date = Date.fromMillis(event.addedDate).formattedString
    val teacher = event.teacherName ?: ""
    val team = event.teamName?.let { " • $it" } ?: ""
    val formatRes = when {
        event.addedManually -> R.string.event_list_added_by_self_format
        event.teacherName == null -> R.string.event_list_added_by_unknown_format
        else -> R.string.event_list_added_by_format
    }
    return AnnotatedString(context.getString(formatRes, date, teacher, team)).withSearchHighlight(query, highlight)
}
