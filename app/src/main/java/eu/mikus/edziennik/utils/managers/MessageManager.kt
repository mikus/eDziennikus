/*
 * Copyright (c) Kuba Szczodrzyński 2021-10-7.
 */

package eu.mikus.edziennik.utils.managers

import android.content.Context
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.widget.EditText
import com.hootsuite.nachos.NachoTextView
import com.hootsuite.nachos.chip.ChipInfo
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.utils.colorRes
import com.mikepenz.iconics.view.IconicsImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import eu.mikus.edziennik.*
import eu.mikus.edziennik.data.db.entity.Message
import eu.mikus.edziennik.data.db.entity.MessageRecipient
import eu.mikus.edziennik.data.db.entity.Metadata
import eu.mikus.edziennik.data.db.entity.Teacher
import eu.mikus.edziennik.data.db.enums.MetadataType
import eu.mikus.edziennik.data.db.full.MessageFull
import eu.mikus.edziennik.ext.appendSpan
import eu.mikus.edziennik.ext.appendText
import eu.mikus.edziennik.ext.fixName
import eu.mikus.edziennik.ext.setText
import eu.mikus.edziennik.ui.messages.MessagesUtils
import eu.mikus.edziennik.ui.messages.compose.SubjectMode
import eu.mikus.edziennik.ui.messages.compose.greetingFor
import eu.mikus.edziennik.ui.messages.compose.subjectMode
import eu.mikus.edziennik.utils.TextInputKeyboardEdit
import eu.mikus.edziennik.utils.html.BetterHtml
import eu.mikus.edziennik.utils.managers.TextStylingManager.HtmlMode.ORIGINAL
import eu.mikus.edziennik.utils.managers.TextStylingManager.StylingConfig
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time
import eu.mikus.edziennik.utils.span.BoldSpan
import eu.mikus.edziennik.utils.span.ItalicSpan
import eu.mikus.edziennik.compat.colorAttr

class MessageManager(private val app: App) {

    class UIConfig(
        val context: Context,
        val recipients: NachoTextView,
        val subject: EditText,
        val body: TextInputKeyboardEdit,
        val teachers: List<Teacher>,
        val greetingOnCompose: Boolean,
        val greetingOnReply: Boolean,
        val greetingOnForward: Boolean,
        val greetingText: String,
    )

    /** Everything the Compose editor needs to seed itself from a nav-args bundle. */
    data class InitialCompose(
        val recipients: List<Teacher>,
        val subject: String?,
        val body: CharSequence?,
        val draftMessageId: Long?,
        val isDraft: Boolean,
    )

    data class GreetingConfig(
        val onCompose: Boolean,
        val onReply: Boolean,
        val onForward: Boolean,
        val text: String,
    )

    private val textStylingManager
        get() = app.textStylingManager

    suspend fun getMessage(profileId: Int, args: Bundle?): MessageFull? {
        val id = args?.getLong("messageId") ?: return null
        val json = args.getString("message")
        val addedDate = args.getLong("sentDate")
        return getMessage(profileId, id, json, addedDate)
    }

    suspend fun getMessage(
        profileId: Int,
        id: Long,
        json: String?,
        sentDate: Long = 0L
    ): MessageFull? {
        val message = if (json != null) {
            app.gson.fromJson(json, MessageFull::class.java)?.also {
                if (sentDate > 0L) {
                    it.addedDate = sentDate
                }
                withContext(Dispatchers.IO) {
                    it.recipients = app.db.messageRecipientDao().getAllByMessageId(profileId, it.id)
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                app.db.messageDao().getByIdNow(profileId, id)
            }
        } ?: return null

        // this helps when multiple profiles receive the same message
        // (there are multiple -1 recipients for the same message ID)
        val recipientsDistinct = message.recipients?.filter { it.profileId == profileId } ?: return null
        message.recipients?.clear()
        message.recipients?.addAll(recipientsDistinct)

        // load recipients for sent messages
        val teachers = withContext(Dispatchers.IO) {
            app.db.teacherDao().getAllNow(profileId)
        }

        message.recipients?.forEach { recipient ->
            // store the account name as a recipient
            if (recipient.id == -1L)
                recipient.fullName = app.profile.accountName ?: app.profile.studentNameLong

            // lookup a teacher by the recipient ID
            if (recipient.fullName == null)
                recipient.fullName = teachers.firstOrNull { it.id == recipient.id }?.fullName ?: ""

            // unset the readByEveryone flag
            if (recipient.readDate < 1 && message.isSent)
                message.readByEveryone = false
        }

        // store the account name as sender for sent messages
        if (message.isSent && message.senderName == null) {
            message.senderName = app.profile.accountName ?: app.profile.studentNameLong
        }

        // set the message as seen
        if (message.body != null && !message.seen) {
            app.db.metadataDao().setSeen(profileId, message, true)
        }
        //msg.recipients = app.db.messageRecipientDao().getAllByMessageId(msg.profileId, msg.id)

        return message
    }

    fun setStarIcon(image: IconicsImageView, message: Message) {
        if (message.isStarred) {
            image.icon?.colorRes = R.color.md_amber_500
            image.icon?.icon = CommunityMaterial.Icon3.cmd_star
        } else {
            image.icon?.colorAttr(image.context, android.R.attr.textColorSecondary)
            image.icon?.icon = CommunityMaterial.Icon3.cmd_star_outline
        }
    }

    suspend fun starMessage(message: Message, isStarred: Boolean) {
        message.isStarred = isStarred
        withContext(Dispatchers.Default) {
            app.db.messageDao().replace(message)
        }
    }

    suspend fun markAsDeleted(message: Message) {
        message.type = Message.TYPE_DELETED
        withContext(Dispatchers.Default) {
            app.db.messageDao().replace(message)
        }
    }

    suspend fun deleteDraft(profileId: Int, messageId: Long) {
        withContext(Dispatchers.Default) {
            app.db.messageRecipientDao().clearFor(profileId, messageId)
            app.db.messageDao().delete(profileId, messageId)
            app.db.metadataDao().delete(profileId, MetadataType.MESSAGE, messageId)
        }
    }

    suspend fun saveAsDraft(config: UIConfig, stylingConfig: StylingConfig, profileId: Int, messageId: Long?) {
        val teachers = config.recipients.allChips.mapNotNull { it.data as? Teacher }
        val subject = config.subject.text?.toString() ?: ""
        val body = textStylingManager.getHtmlText(stylingConfig, htmlMode = ORIGINAL)

        withContext(Dispatchers.Default) {
            if (messageId != null) {
                app.db.messageRecipientDao().clearFor(profileId, messageId)
            }

            val message = Message(
                profileId = profileId,
                id = messageId ?: System.currentTimeMillis(),
                type = Message.TYPE_DRAFT,
                subject = subject,
                body = body,
                senderId = -1L,
                addedDate = System.currentTimeMillis(),
            )
            val metadata = Metadata(profileId, MetadataType.MESSAGE, message.id, true, true)

            val recipients = teachers.map {
                MessageRecipient(profileId, it.id, message.id)
            }

            app.db.messageDao().replace(message)
            app.db.messageRecipientDao().addAll(recipients)
            app.db.metadataDao().add(metadata)
        }
    }

    /**
     * Data-based counterpart of the legacy [saveAsDraft] - takes the editor state as plain values
     * instead of reading it out of Views. Same DB writes.
     */
    suspend fun saveAsDraft(
        profileId: Int,
        messageId: Long?,
        recipients: List<Teacher>,
        subject: String,
        bodyHtml: String,
    ) {
        withContext(Dispatchers.Default) {
            if (messageId != null) {
                app.db.messageRecipientDao().clearFor(profileId, messageId)
            }

            val message = Message(
                profileId = profileId,
                id = messageId ?: System.currentTimeMillis(),
                type = Message.TYPE_DRAFT,
                subject = subject,
                body = bodyHtml,
                senderId = -1L,
                addedDate = System.currentTimeMillis(),
            )
            val metadata = Metadata(profileId, MetadataType.MESSAGE, message.id, true, true)

            app.db.messageDao().replace(message)
            app.db.messageRecipientDao().addAll(recipients.map {
                MessageRecipient(profileId, it.id, message.id)
            })
            app.db.metadataDao().add(metadata)
        }
    }

    /**
     * Data-based counterpart of [fillWithBundle] (+ the fillWith* helpers) - returns what the
     * Compose editor should start with, instead of writing into Views.
     *
     * A null [args] is treated as an empty bundle (MainActivity always hands the fragment
     * `args ?: Bundle()`, so this is the "new message" case).
     */
    fun fillFromBundle(
        context: Context,
        args: Bundle?,
        teachers: List<Teacher>,
        greeting: GreetingConfig,
    ): InitialCompose {
        val messageJson = args?.getString("message")
        val teacherId = args?.getLong("messageRecipientId") ?: 0L
        val argsSubject = args?.getString("messageSubject")
        val payloadType = args?.getString("type")

        val message = messageJson?.let { app.gson.fromJson(it, MessageFull::class.java) }

        return when {
            message != null && message.isDraft -> InitialCompose(
                recipients = resolveRecipients(teachers, message.recipients?.map { it.id } ?: emptyList()),
                subject = message.subject,
                body = BetterHtml.fromHtml(
                    context,
                    message.body ?: context.getString(R.string.messages_compose_body_load_failed),
                ),
                draftMessageId = message.id,
                isDraft = true,
            )
            message != null -> InitialCompose(
                recipients = resolveRecipients(teachers, listOfNotNull(message.senderId)),
                subject = when (subjectMode(payloadType)) {
                    SubjectMode.REPLY ->
                        context.getString(R.string.messages_compose_subject_reply_format, message.subject)
                    SubjectMode.FORWARD ->
                        context.getString(R.string.messages_compose_subject_forward_format, message.subject)
                    SubjectMode.NONE -> argsSubject
                },
                body = buildReplyForwardBody(context, message, payloadType, greeting),
                draftMessageId = null,
                isDraft = false,
            )
            // message-a-teacher (a recipient ID was passed) and a plain new message differ
            // only in the pre-filled recipient
            else -> InitialCompose(
                recipients = if (teacherId != 0L) resolveRecipients(teachers, listOf(teacherId)) else emptyList(),
                subject = argsSubject,
                body = if (greeting.onCompose) greeting.text else null,
                draftMessageId = null,
                isDraft = false,
            )
        }
    }

    /**
     * Looks the IDs up in [teachers], attaching the avatar the legacy chips had
     * (see [createRecipientChips]). Unknown IDs are dropped.
     */
    private fun resolveRecipients(teachers: List<Teacher>, teacherIds: List<Long>): List<Teacher> {
        return teacherIds.mapNotNull { teacherId ->
            teachers.firstOrNull { it.id == teacherId }?.also { teacher ->
                teacher.image = MessagesUtils.getProfileImage(
                    diameterDp = 48,
                    textSizeBigDp = 24,
                    textSizeMediumDp = 16,
                    textSizeSmallDp = 12,
                    count = 1,
                    teacher.fullName
                )
            }
        }
    }

    /** The quoted-original body of a reply/forward, incl. the greeting - as in [fillWithMessage]. */
    private fun buildReplyForwardBody(
        context: Context,
        message: MessageFull,
        payloadType: String?,
        greetingConfig: GreetingConfig,
    ): CharSequence {
        val spanned = SpannableStringBuilder()

        val dateString = context.getString(
            R.string.messages_reply_date_time_format,
            Date.fromMillis(message.addedDate).formattedStringShort,
            Time.fromMillis(message.addedDate).stringHM,
        )
        // add original message info
        spanned.appendText("W dniu ")
        spanned.appendSpan(dateString, ItalicSpan(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spanned.appendText(", ")
        spanned.appendSpan(message.senderName.fixName(), ItalicSpan(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spanned.appendText(" napisał(a):")
        spanned.setSpan(BoldSpan(), 0, spanned.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spanned.appendText("\n\n")

        val greeting = greetingFor(
            payloadType = payloadType,
            greetingText = greetingConfig.text,
            onReply = greetingConfig.onReply,
            onForward = greetingConfig.onForward,
        )

        if (greeting == null) {
            spanned.replace(0, 0, "\n\n")
        } else {
            spanned.replace(0, 0, "$greeting\n\n\n")
        }

        val body = message.body ?: context.getString(R.string.messages_compose_body_load_failed)
        spanned.appendText(BetterHtml.fromHtml(context, body))
        return spanned
    }

    fun fillWithBundle(config: UIConfig, args: Bundle?): Message? {
        args ?: return null
        val messageJson = args.getString("message")
        val teacherId = args.getLong("messageRecipientId")
        val subject = args.getString("messageSubject")
        val payloadType = args.getString("type")

        if (config.greetingOnCompose)
            config.body.setText(config.greetingText)
        if (subject != null)
            config.subject.setText(subject)

        val message = if (messageJson != null)
            app.gson.fromJson(messageJson, MessageFull::class.java)
        else null

        when {
            message != null && message.isDraft -> {
                fillWithDraftMessage(config, message)
            }
            message != null -> {
                fillWithMessage(config, message, payloadType)
            }
            teacherId != 0L -> {
                fillWithRecipientIds(config, teacherId)
            }
        }

        return message
    }

    private fun createRecipientChips(config: UIConfig, vararg teacherIds: Long?): List<ChipInfo> {
        return teacherIds.mapNotNull { teacherId ->
            val teacher = config.teachers.firstOrNull { it.id == teacherId } ?: return@mapNotNull null
            teacher.image = MessagesUtils.getProfileImage(
                diameterDp = 48,
                textSizeBigDp = 24,
                textSizeMediumDp = 16,
                textSizeSmallDp = 12,
                count = 1,
                teacher.fullName
            )
            ChipInfo(teacher.fullName, teacher)
        }
    }

    private fun fillWithRecipientIds(config: UIConfig, vararg teacherIds: Long?) {
        config.recipients.addTextWithChips(createRecipientChips(config, *teacherIds))
    }

    private fun fillWithMessage(config: UIConfig, message: MessageFull, payloadType: String?) {
        val spanned = SpannableStringBuilder()

        val dateString = config.context.getString(
            R.string.messages_reply_date_time_format,
            Date.fromMillis(message.addedDate).formattedStringShort,
            Time.fromMillis(message.addedDate).stringHM,
        )
        // add original message info
        spanned.appendText("W dniu ")
        spanned.appendSpan(dateString, ItalicSpan(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spanned.appendText(", ")
        spanned.appendSpan(message.senderName.fixName(), ItalicSpan(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spanned.appendText(" napisał(a):")
        spanned.setSpan(BoldSpan(), 0, spanned.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spanned.appendText("\n\n")

        val greeting = when (payloadType) {
            "reply" -> {
                config.subject.setText(R.string.messages_compose_subject_reply_format, message.subject)
                if (config.greetingOnReply)
                    config.greetingText
                else null
            }
            "forward" -> {
                config.subject.setText(R.string.messages_compose_subject_forward_format, message.subject)
                if (config.greetingOnForward)
                    config.greetingText
                else null
            }
            else -> null
        }

        if (greeting == null) {
            spanned.replace(0, 0, "\n\n")
        } else {
            spanned.replace(0, 0, "$greeting\n\n\n")
        }

        val body = message.body ?: config.context.getString(R.string.messages_compose_body_load_failed)
        spanned.appendText(BetterHtml.fromHtml(config.context, body))

        fillWithRecipientIds(config, message.senderId)
        config.body.text = spanned
    }

    private fun fillWithDraftMessage(config: UIConfig, message: MessageFull) {
        val recipientIds = message.recipients?.map { it.id }?.toTypedArray() ?: emptyArray()
        fillWithRecipientIds(config, *recipientIds)

        config.subject.setText(message.subject)

        val body = message.body ?: config.context.getString(R.string.messages_compose_body_load_failed)
        config.body.setText(BetterHtml.fromHtml(config.context, body))
    }
}
