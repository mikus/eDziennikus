/*
 * Copyright (c) Mikolaj Olszewski 2026-7-31.
 */
package eu.mikus.edziennik.ui.messages.compose

import android.content.Context
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Teacher
import eu.mikus.edziennik.ext.asSpannable
import eu.mikus.edziennik.ext.concat
import eu.mikus.edziennik.ext.getNameInitials
import eu.mikus.edziennik.ext.resolveAttr
import eu.mikus.edziennik.ui.compose.IconicsIcon
import eu.mikus.edziennik.ui.dialogs.base.RichTextFieldBridge
import eu.mikus.edziennik.ui.dialogs.base.RichTextStyling
import eu.mikus.edziennik.utils.Colors
import eu.mikus.edziennik.utils.managers.TextStylingManager.HtmlMode
import eu.mikus.edziennik.utils.managers.TextStylingManager.StylingConfigBase

/**
 * The id range of the synthetic type-group entries the ViewModel appends to the suggestion list
 * (`-Teacher.TYPE_*`); mirrors MessagesComposeViewModel's private `CATEGORY_IDS`. Such an entry is
 * never a recipient - tapping it opens the multi-choice category picker.
 */
private val CATEGORY_IDS = -24L..0L

/** The Librus counter limits the legacy fragment set on the subject / body layouts. */
private const val SUBJECT_MAX_LENGTH = 150
private const val BODY_MAX_LENGTH = 20000

/**
 * The write-message editor. Stateless w.r.t. business logic: everything is a parameter, and the only
 * local state is the recipient dropdown's presentation (is it open, was it opened by the end icon)
 * plus which category picker is showing.
 *
 * Ports the legacy `messages_compose_fragment.xml` field order - recipients, subject, body - where
 * the Nacho chip field becomes selected-recipient [InputChip]s over a plain query [OutlinedTextField]
 * with an [ExposedDropdownMenuBox] of suggestions, and the body stays a [RichTextFieldBridge]
 * (there is no Compose rich-text editor; the styling pipeline needs the real TextInputLayout).
 *
 * [initialBody] must be computed ONCE by the host (the bridge seeds the field a single time, in its
 * AndroidView `factory`); recomposing with a different value has no effect. [onBodyConfigReady] fires
 * from that factory, so the host may only stash the config in a plain field.
 */
@Composable
fun MessagesComposeScreen(
    app: App,
    activity: AppCompatActivity,
    teachers: List<Teacher>,
    selectedRecipients: List<Teacher>,
    recipientQuery: String,
    subject: String,
    isRecipientListReady: Boolean,
    initialBody: CharSequence?,
    textStylingEnabled: Boolean,
    isLibrus: Boolean,
    recipientsError: String?,
    subjectError: String?,
    bodyError: String?,
    suggestions: (String?) -> List<Teacher>,
    categoryMembers: (Int) -> List<Teacher>,
    onQueryChange: (String) -> Unit,
    onSubjectChange: (String) -> Unit,
    onAddRecipient: (Teacher) -> Unit,
    onRemoveRecipient: (Teacher) -> Unit,
    onToggleCategoryMember: (Teacher, Boolean) -> Unit,
    onBodyConfigReady: (StylingConfigBase) -> Unit,
    onBodyChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recipientsFocus = remember { FocusRequester() }
    val subjectFocus = remember { FocusRequester() }
    // The category picker currently open (a Teacher.TYPE_* constant), null = none.
    var categoryType by remember { mutableStateOf<Int?>(null) }

    // Legacy updateRecipientList focused the first empty field once the list was ready. The body is
    // an AndroidView, so the "else -> body.requestFocus()" branch is deliberately dropped - the
    // draft/reply case just leaves focus alone instead of stealing it.
    LaunchedEffect(isRecipientListReady) {
        if (!isRecipientListReady)
            return@LaunchedEffect
        when {
            selectedRecipients.isEmpty() && recipientQuery.isBlank() -> recipientsFocus.requestFocus()
            subject.isBlank() -> subjectFocus.requestFocus()
            else -> Unit
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // half of the FAB's size, as in the legacy layout's paddingBottom
            .padding(bottom = 40.dp),
    ) {
        RecipientsField(
            teachers = teachers,
            selectedRecipients = selectedRecipients,
            recipientQuery = recipientQuery,
            isRecipientListReady = isRecipientListReady,
            recipientsError = recipientsError,
            suggestions = suggestions,
            onQueryChange = onQueryChange,
            onAddRecipient = onAddRecipient,
            onRemoveRecipient = onRemoveRecipient,
            onCategoryClick = { categoryType = it },
            focusRequester = recipientsFocus,
        )

        OutlinedTextField(
            value = subject,
            onValueChange = onSubjectChange,
            enabled = isRecipientListReady,
            singleLine = true,
            label = { Text(stringResource(R.string.messages_compose_subject_hint)) },
            isError = subjectError != null,
            supportingText = subjectSupportingText(subjectError, subject.length, isLibrus),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .focusRequester(subjectFocus),
        )

        RichTextFieldBridge(
            app = app,
            activity = activity,
            hint = stringResource(R.string.messages_compose_text_hint),
            initialHtml = initialBody,
            htmlMode = HtmlMode.ORIGINAL,
            stylingMode = if (textStylingEnabled) RichTextStyling.INLINE else RichTextStyling.PLAIN,
            counterEnabled = isLibrus,
            counterMaxLength = if (isLibrus) BODY_MAX_LENGTH else -1,
            onConfigReady = onBodyConfigReady,
            onChanged = onBodyChanged,
            error = bodyError,
            minLines = 3,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }

    categoryType?.let { type ->
        RecipientCategoryDialog(
            activity = activity,
            type = type,
            members = categoryMembers(type),
            selectedRecipients = selectedRecipients,
            onToggleCategoryMember = onToggleCategoryMember,
            onDismiss = { categoryType = null },
        )
    }
}

/**
 * The error takes the supporting-text slot when there is one; otherwise Librus profiles get the
 * visible character counter the legacy `subjectLayout.counterMaxLength = 150` produced.
 */
@Composable
private fun subjectSupportingText(
    subjectError: String?,
    length: Int,
    isLibrus: Boolean,
): (@Composable () -> Unit)? = when {
    subjectError != null -> {
        { Text(subjectError) }
    }
    isLibrus -> {
        {
            Text(
                text = stringResource(R.string.messages_compose_counter_format, length, SUBJECT_MAX_LENGTH),
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    else -> null
}

/**
 * The recipient block: the already-selected teachers as removable [InputChip]s, plus the query field
 * whose suggestion menu replaces the Nacho auto-complete dropdown.
 *
 * Two local flags drive the menu, mirroring the legacy Nacho behaviour:
 * - `showAll` - the end icon was tapped, i.e. the legacy `ignoreThreshold = true; filter(null)` path.
 *   `suggestions(null)` deliberately returns ONLY the synthetic type-group entries: that icon is the
 *   "browse categories" affordance, not "list every teacher".
 * - `dismissed` - the menu was closed without picking anything. Without it a non-empty query would
 *   immediately re-open the menu the user just tapped away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipientsField(
    teachers: List<Teacher>,
    selectedRecipients: List<Teacher>,
    recipientQuery: String,
    isRecipientListReady: Boolean,
    recipientsError: String?,
    suggestions: (String?) -> List<Teacher>,
    onQueryChange: (String) -> Unit,
    onAddRecipient: (Teacher) -> Unit,
    onRemoveRecipient: (Teacher) -> Unit,
    onCategoryClick: (Int) -> Unit,
    focusRequester: FocusRequester,
) {
    var showAll by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }

    // [teachers] is not read directly - the ranking lives behind [suggestions] - but it IS the key
    // that makes the menu recompute once the recipient list finally arrives (or is re-synced).
    val items = remember(teachers, recipientQuery, showAll) {
        when {
            showAll -> suggestions(null)
            recipientQuery.isEmpty() -> emptyList()
            else -> suggestions(recipientQuery)
        }
    }
    // An empty query yields no items unless the end icon was tapped, so this is exactly
    // "(query.isNotEmpty() && items.isNotEmpty()) || showAll", minus the empty-menu case.
    val expanded = !dismissed && items.isNotEmpty()

    Column {
        if (selectedRecipients.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                selectedRecipients.forEach { teacher ->
                    InputChip(
                        selected = true,
                        onClick = {},
                        label = { Text(teacher.fullName) },
                        avatar = { TeacherAvatar(teacher, InputChipDefaults.AvatarSize) },
                        trailingIcon = {
                            IconicsIcon(
                                icon = CommunityMaterial.Icon.cmd_close,
                                contentDescription = null,
                                sizeDp = 18,
                                modifier = Modifier.clickable { onRemoveRecipient(teacher) },
                            )
                        },
                    )
                }
            }
        }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { wantExpanded ->
                dismissed = !wantExpanded
                if (!wantExpanded)
                    showAll = false
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            OutlinedTextField(
                value = recipientQuery,
                onValueChange = {
                    // any edit leaves "browse categories" mode and re-opens the suggestion menu
                    showAll = false
                    dismissed = false
                    onQueryChange(it)
                },
                enabled = isRecipientListReady,
                singleLine = true,
                label = { Text(stringResource(R.string.messages_compose_to_hint)) },
                isError = recipientsError != null,
                supportingText = recipientsError?.let { { Text(it) } },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            showAll = true
                            dismissed = false
                        },
                    ) {
                        Icon(painterResource(R.drawable.dropdown_arrow), contentDescription = null)
                    }
                },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryEditable)
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = {
                    dismissed = true
                    showAll = false
                },
            ) {
                items.forEach { teacher ->
                    RecipientSuggestionRow(
                        teacher = teacher,
                        query = if (showAll) "" else recipientQuery,
                        onClick = {
                            dismissed = true
                            showAll = false
                            if (teacher.id in CATEGORY_IDS)
                                onCategoryClick((teacher.id * -1).toInt())
                            else
                                onAddRecipient(teacher)
                        },
                    )
                }
            }
        }
    }
}

/**
 * One suggestion row - the Compose port of `teacher_item.xml` + MessagesComposeSuggestionAdapter.
 * A synthetic type-group entry shows no avatar and the "browse category" subtitle; a real teacher
 * shows its avatar, the name with the matched [query] substring highlighted, and its role list.
 */
@Composable
private fun RecipientSuggestionRow(
    teacher: Teacher,
    query: String,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val isCategory = teacher.id in CATEGORY_IDS
    // The legacy adapter highlighted the match with a colorControlHighlight BackgroundColorSpan.
    val highlight = remember(context) { Color(R.attr.colorControlHighlight.resolveAttr(context)) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (!isCategory) {
            TeacherAvatar(teacher, 40.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column {
            Text(
                text = if (isCategory)
                    AnnotatedString(teacher.fullName)
                else
                    highlightedName(teacher.fullName, query, highlight),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (isCategory)
                    stringResource(R.string.teachers_browse_category)
                else
                    teacher.getTypeText(context),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * [name] with every case-insensitive occurrence of [query] bolded and background-highlighted. The
 * legacy span also ignored diacritics; matching the raw name keeps the highlight offsets honest
 * (a diacritic-insensitive match can still rank the row in - it just is not highlighted).
 */
private fun highlightedName(name: String, query: String, highlight: Color): AnnotatedString =
    buildAnnotatedString {
        append(name)
        if (query.isEmpty())
            return@buildAnnotatedString
        val style = SpanStyle(fontWeight = FontWeight.Bold, background = highlight)
        var index = name.indexOf(query, ignoreCase = true)
        while (index >= 0) {
            addStyle(style, index, index + query.length)
            index = name.indexOf(query, startIndex = index + query.length, ignoreCase = true)
        }
    }

/**
 * The teacher avatar used by both the chips and the suggestion rows. A cached
 * [Teacher.image] bitmap wins; otherwise the MessagesUtils.getProfileImage look (a
 * `stringToMaterialColor` circle with the name initials) is drawn natively - no Bitmap is allocated
 * per row, which matters because a dropdown composes every match at once.
 */
@Composable
private fun TeacherAvatar(teacher: Teacher, size: Dp) {
    val bitmap = teacher.image
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
        )
    } else {
        val colorInt = Colors.stringToMaterialColor(teacher.fullName)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(colorInt)),
        ) {
            Text(
                text = teacher.fullName.getNameInitials(),
                color = Color(ColorUtils.blendARGB(Colors.legibleTextColor(colorInt), colorInt, 0.30f)),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/**
 * The multi-choice category picker MessagesComposeChipCreator used to show when a type-group chip was
 * created. Still a platform [MaterialAlertDialogBuilder]: it is a stock multi-choice list, and the
 * item labels are two-line spannables the platform dialog renders for free.
 *
 * Everything it shows is snapshotted when it opens (as the legacy dialog did), so the effect keys on
 * [type] alone; the checkbox callbacks go straight to [onToggleCategoryMember].
 */
@Composable
private fun RecipientCategoryDialog(
    activity: AppCompatActivity,
    type: Int,
    members: List<Teacher>,
    selectedRecipients: List<Teacher>,
    onToggleCategoryMember: (Teacher, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    DisposableEffect(type) {
        val labels = members.map { categoryItemLabel(activity, type, it) }.toTypedArray()
        val checked = members.map { member -> selectedRecipients.any { it.id == member.id } }.toBooleanArray()
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(
                activity.getString(
                    R.string.messages_compose_add_recipients_format,
                    Teacher.typeName(activity, type),
                ),
            )
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                onToggleCategoryMember(members[which], isChecked)
            }
            .setPositiveButton(R.string.ok, null)
            .setNeutralButton(R.string.cancel, null)
            .setOnDismissListener { onDismiss() }
            .show()
        onDispose { dialog.dismiss() }
    }
}

/**
 * A category row label: the name, plus - for the types the legacy ChipCreator gave a description to -
 * a smaller, secondary-coloured `typeDescription` on a second line. The legacy `when` listed every
 * other type explicitly as null and fell through to `typeDescription` for anything unlisted, i.e.
 * TYPE_OTHER; since the picker is only ever opened for a [Teacher.types] entry, enumerating the five
 * described types is equivalent.
 */
private fun categoryItemLabel(context: Context, type: Int, teacher: Teacher): CharSequence {
    val description = when (type) {
        Teacher.TYPE_PARENTS_COUNCIL,
        Teacher.TYPE_EDUCATOR,
        Teacher.TYPE_PARENT,
        Teacher.TYPE_STUDENT,
        Teacher.TYPE_OTHER -> teacher.typeDescription
        else -> null
    }
    return listOfNotNull(
        teacher.fullName.asSpannable(
            ForegroundColorSpan(android.R.attr.textColorPrimary.resolveAttr(context)),
        ),
        description?.asSpannable(
            ForegroundColorSpan(android.R.attr.textColorSecondary.resolveAttr(context)),
            AbsoluteSizeSpan(14, true),
        ),
    ).concat("\n")
}
