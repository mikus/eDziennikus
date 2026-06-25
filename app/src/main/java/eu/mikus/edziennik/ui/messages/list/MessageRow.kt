/*
 * Copyright (c) Mikolaj Olszewski 2026-6-25.
 */

package eu.mikus.edziennik.ui.messages.list

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.full.MessageFull
import eu.mikus.edziennik.ext.resolveAttr
import eu.mikus.edziennik.ui.compose.IconicsIcon
import eu.mikus.edziennik.ui.compose.withSearchHighlight
import eu.mikus.edziennik.ui.messages.MessagesUtils
import eu.mikus.edziennik.utils.models.Date

@Composable
fun MessageRow(
    message: MessageFull,
    query: String,
    onClick: (MessageFull) -> Unit,
    onStarClick: (MessageFull) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val highlight = remember(context) { Color(R.attr.colorControlHighlight.resolveAttr(context)) }
    val read = message.isSent || message.isDraft || message.seen
    val weight = if (read) FontWeight.Normal else FontWeight.Bold

    val info = remember(message) { MessagesUtils.getMessageInfo(app, message, 48, 24, 18, 12) }
    val name = remember(message, query, highlight) {
        AnnotatedString(info.profileName ?: "").withSearchHighlight(query, highlight)
    }
    val subject = remember(message, query, highlight) {
        AnnotatedString(message.subject).withSearchHighlight(query, highlight)
    }
    val preview = remember(message) { message.bodyHtml?.toString()?.take(200).orEmpty() }
    val date = remember(message) { Date.fromMillis(message.addedDate).formattedStringShort }

    Card(
        onClick = { onClick(message) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            info.profileImage?.let { bmp ->
                Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (message.hasNotes()) {
                        val noteIcon = if (message.hasReplacingNotes()) CommunityMaterial.Icon3.cmd_swap_horizontal else CommunityMaterial.Icon3.cmd_playlist_edit
                        IconicsIcon(noteIcon, contentDescription = null, sizeDp = 16, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = weight, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(date, style = MaterialTheme.typography.labelSmall, fontWeight = weight, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(subject, style = MaterialTheme.typography.bodyMedium, fontWeight = weight, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (preview.isNotEmpty()) {
                    Text(preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (message.hasAttachments) {
                Spacer(Modifier.width(6.dp))
                IconicsIcon(CommunityMaterial.Icon.cmd_attachment, contentDescription = null, sizeDp = 16, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onStarClick(message) }) {
                IconicsIcon(
                    if (message.isStarred) CommunityMaterial.Icon3.cmd_star else CommunityMaterial.Icon3.cmd_star_outline,
                    contentDescription = null,
                    tint = if (message.isStarred) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
