/*
 * Copyright (c) Mikolaj Olszewski 2026-6-30.
 */

package eu.mikus.edziennik.ui.notes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Note
import eu.mikus.edziennik.ui.compose.IconicsIcon
import eu.mikus.edziennik.ui.compose.toAnnotatedString
import eu.mikus.edziennik.ui.compose.withSearchHighlight
import eu.mikus.edziennik.utils.models.Date

/** Reusable note card row (NotesScreen list + Home Notes card). Pencil hidden when [onNoteEditClick] is null. */
@Composable
fun NoteCard(
    note: Note,
    onNoteClick: (Note) -> Unit,
    modifier: Modifier = Modifier,
    query: String = "",
    highlightColor: Color = MaterialTheme.colorScheme.primary,
    onNoteEditClick: ((Note) -> Unit)? = null,
) {
    val container = note.color?.let { Color(ColorUtils.setAlphaComponent(it.toInt(), 0x50)) }
        ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val display = remember(note, query) {
        (note.topicHtml ?: note.bodyHtml).toAnnotatedString().withSearchHighlight(query, highlightColor)
    }
    Card(
        onClick = { onNoteClick(note) },
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = display,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                val date = remember(note.addedDate) { Date.fromMillis(note.addedDate).formattedString }
                Text(
                    text = stringResource(R.string.notes_added_by_you_format, date),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onNoteEditClick != null) {
                IconButton(onClick = { onNoteEditClick(note) }) {
                    IconicsIcon(
                        CommunityMaterial.Icon3.cmd_pencil_outline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
