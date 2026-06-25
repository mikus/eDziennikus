/*
 * Copyright (c) Mikolaj Olszewski 2026-6-24.
 */

package eu.mikus.edziennik.ui.messages.single

import android.os.Bundle
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.full.MessageFull
import eu.mikus.edziennik.ext.isNotNullNorEmpty
import eu.mikus.edziennik.ui.compose.IconicsIcon
import eu.mikus.edziennik.ui.messages.MessagesUtils
import eu.mikus.edziennik.ui.views.AttachmentsView
import eu.mikus.edziennik.utils.BetterLink
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time

@Composable
fun MessageReadScreen(
    state: MessageReadUiState,
    onClose: () -> Unit,
    onStarClick: (MessageFull) -> Unit,
    onReply: (MessageFull) -> Unit,
    onForward: (MessageFull) -> Unit,
    onDelete: (MessageFull) -> Unit,
    onDownload: (MessageFull) -> Unit,
    onNotes: (MessageFull) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        MessageReadUiState.Loading, MessageReadUiState.NotFound ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state == MessageReadUiState.Loading) CircularProgressIndicator()
            }
        is MessageReadUiState.Content ->
            MessageContent(
                message = state.message,
                onClose = onClose,
                onStarClick = onStarClick,
                onReply = onReply,
                onForward = onForward,
                onDelete = onDelete,
                onDownload = onDownload,
                onNotes = onNotes,
                modifier = modifier,
            )
    }
}

@Composable
private fun MessageContent(
    message: MessageFull,
    onClose: () -> Unit,
    onStarClick: (MessageFull) -> Unit,
    onReply: (MessageFull) -> Unit,
    onForward: (MessageFull) -> Unit,
    onDelete: (MessageFull) -> Unit,
    onDownload: (MessageFull) -> Unit,
    onNotes: (MessageFull) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val info = remember(message) { MessagesUtils.getMessageInfo(app, message, 40, 20, 14, 10) }
    val dateTime = remember(message) {
        context.getString(
            R.string.messages_date_time_format,
            Date.fromMillis(message.addedDate).formattedStringShort,
            Time.fromMillis(message.addedDate).stringHM,
        )
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                IconicsIcon(CommunityMaterial.Icon3.cmd_window_close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            info.profileImage?.let {
                Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(info.profileName ?: "", style = MaterialTheme.typography.titleMedium)
                Text(dateTime, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onStarClick(message) }) {
                IconicsIcon(
                    if (message.isStarred) CommunityMaterial.Icon3.cmd_star else CommunityMaterial.Icon3.cmd_star_outline,
                    contentDescription = null,
                    tint = if (message.isStarred) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.size(8.dp))
        Text(message.subject, style = MaterialTheme.typography.titleLarge)

        Spacer(Modifier.size(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {   // action row (visibility mirrors legacy MessageFragment)
            if (message.isReceived || message.isDeleted) {
                IconButton(onClick = { onReply(message) }) {
                    IconicsIcon(CommunityMaterial.Icon3.cmd_reply_outline, contentDescription = null)
                }
            }
            IconButton(onClick = { onForward(message) }) {
                IconicsIcon(CommunityMaterial.Icon.cmd_arrow_right, contentDescription = null)
            }
            if (message.isReceived) {
                IconButton(onClick = { onDelete(message) }) {
                    IconicsIcon(CommunityMaterial.Icon.cmd_delete_outline, contentDescription = null)
                }
            }
            if (App.devMode) {
                IconButton(onClick = { onDownload(message) }) {
                    IconicsIcon(CommunityMaterial.Icon.cmd_download_outline, contentDescription = null)
                }
            }
        }

        Spacer(Modifier.size(12.dp))
        // Body: reuse BetterHtml + BetterLink exactly via a hosted TextView. Keyed on the body so the
        // TextView (and its BetterLink TextWatcher) is built ONCE per body — no watcher stacking on recompose.
        key(message.body) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { ctx ->
                    TextView(ctx).also { tv ->
                        tv.text = MessagesUtils.htmlToSpannable(ctx, message.body.orEmpty())
                        BetterLink.attach(tv)
                    }
                },
            )
        }

        MessageRecipients(message)
        MessageAttachments(message)

        Spacer(Modifier.size(12.dp))
        Button(onClick = { onNotes(message) }) {
            IconicsIcon(CommunityMaterial.Icon3.cmd_playlist_edit, contentDescription = null, sizeDp = 20, tint = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.notes_button))
        }
    }
}

@Composable
private fun MessageRecipients(message: MessageFull) {
    val recipients = message.recipients ?: return
    if (recipients.isEmpty()) return
    val context = LocalContext.current
    Spacer(Modifier.size(12.dp))
    Column(Modifier.fillMaxWidth()) {
        recipients.forEach { r ->
            val name = r.fullName ?: ""
            val unread = r.readDate == 0L
            val status = when (r.readDate) {
                -1L -> name
                0L -> "$name — " + context.getString(R.string.messages_read_no)
                1L -> "$name — " + context.getString(R.string.messages_read_yes)
                else -> "$name — " + context.getString(R.string.messages_read_yes) + " " + context.getString(
                    R.string.messages_reply_date_time_format,
                    Date.fromMillis(r.readDate).formattedString,
                    Time.fromMillis(r.readDate).stringHM,
                )
            }
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (unread) Color(0xFFF44336) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessageAttachments(message: MessageFull) {
    if (!message.attachmentIds.isNotNullNorEmpty() || !message.attachmentNames.isNotNullNorEmpty()) return
    Spacer(Modifier.size(12.dp))
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { ctx ->
            AttachmentsView(ctx).apply {
                init(
                    Bundle().also {
                        it.putInt("profileId", message.profileId)
                        it.putLongArray("attachmentIds", message.attachmentIds!!.toLongArray())
                        it.putStringArray("attachmentNames", message.attachmentNames!!.toTypedArray())
                        if (message.attachmentSizes.isNotNullNorEmpty())
                            it.putLongArray("attachmentSizes", message.attachmentSizes!!.toLongArray())
                    },
                    message,
                )
            }
        },
    )
}
