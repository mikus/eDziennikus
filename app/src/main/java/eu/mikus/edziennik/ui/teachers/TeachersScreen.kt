/*
 * Copyright (c) Mikolaj Olszewski 2026-6-18.
 */

package eu.mikus.edziennik.ui.teachers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Subject
import eu.mikus.edziennik.data.db.entity.Teacher
import eu.mikus.edziennik.ui.compose.IconicsIcon

@Composable
fun TeachersScreen(
    state: TeachersUiState,
    onCopy: (String) -> Unit,
    onSendMessage: (Teacher) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    when (state) {
        TeachersUiState.Loading ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        TeachersUiState.Empty ->
            Box(modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.teachers_no_data),
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                )
            }
        is TeachersUiState.Content ->
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(state.rows, key = { it.teacher.id }) { row ->
                    TeacherRowItem(row, state.subjects, onCopy, onSendMessage)
                }
            }
    }
}

@Composable
private fun TeacherRowItem(
    row: TeacherRow,
    subjects: List<Subject>,
    onCopy: (String) -> Unit,
    onSendMessage: (Teacher) -> Unit,
) {
    val context = LocalContext.current
    val teacher = row.teacher
    val name = teacher.fullName
    // Context-bound entity formatter, resolved at the composable edge (not in the VM).
    val typeText = remember(teacher, subjects) { teacher.getTypeText(context, subjects) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val initials = name
            .split(" ")
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .take(2)
            .joinToString("")
            .ifEmpty { "?" }
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(initials, color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (typeText.isNotBlank()) {
                Text(
                    typeText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = { onCopy(name) }) {
            IconicsIcon(
                CommunityMaterial.Icon.cmd_clipboard_text_multiple_outline,
                contentDescription = stringResource(R.string.copy_to_clipboard),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (row.canSendMessage) {
            IconButton(onClick = { onSendMessage(teacher) }) {
                IconicsIcon(
                    CommunityMaterial.Icon.cmd_email_plus_outline,
                    contentDescription = stringResource(R.string.send_message),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
