/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-4.
 */

package eu.mikus.edziennik.ui.messages.compose

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import eu.mikus.edziennik.*
import eu.mikus.edziennik.data.api.ERROR_MESSAGE_NOT_SENT
import eu.mikus.edziennik.data.api.edziennik.EdziennikTask
import eu.mikus.edziennik.data.api.events.MessageSentEvent
import eu.mikus.edziennik.data.api.events.RecipientListGetEvent
import eu.mikus.edziennik.data.api.models.ApiError
import eu.mikus.edziennik.data.db.entity.Message
import eu.mikus.edziennik.data.db.enums.LoginType
import eu.mikus.edziennik.databinding.MessagesComposeFragmentBinding
import eu.mikus.edziennik.ext.Bundle
import eu.mikus.edziennik.ui.base.enums.NavTarget
import eu.mikus.edziennik.ui.compose.setAppThemeContent
import eu.mikus.edziennik.ui.dialogs.settings.MessagesConfigDialog
import eu.mikus.edziennik.ui.messages.list.MessagesFragment
import eu.mikus.edziennik.utils.Themes
import eu.mikus.edziennik.utils.managers.TextStylingManager.HtmlMode.ORIGINAL
import eu.mikus.edziennik.utils.managers.TextStylingManager.StylingConfigBase
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetPrimaryItem
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetSeparatorItem
import kotlin.coroutines.CoroutineContext

/**
 * Shell host for the Compose write-message editor. Owns everything that is NOT editor state: the
 * bottom-sheet actions, the FAB, the send/save-draft/discard-draft flows, the save-on-leave prompt
 * and the two EventBus results. The recipient/subject state lives in [MessagesComposeViewModel]; the
 * rich-text body lives in the AndroidView bridge inside [MessagesComposeScreen], and is reached
 * through the [bodyConfig] published by it.
 */
class MessagesComposeFragment : Fragment(), CoroutineScope {
    companion object {
        private const val TAG = "MessagesComposeFragment"
    }

    private lateinit var app: App
    private lateinit var activity: MainActivity
    private lateinit var b: MessagesComposeFragmentBinding
    private lateinit var vm: MessagesComposeViewModel

    private val job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    /**
     * The body field's styling config, published once from the bridge's AndroidView factory - the
     * only way to read/serialize the body (there is no Compose rich-text editor).
     */
    private var bodyConfig: StylingConfigBase? = null

    /** Computed once by [applyInitialState]; the bridge seeds its field from it a single time. */
    private var initialBody: CharSequence? = null

    /** The body is not VM state, so its dirty flag is tracked here (legacy `changedBody`). */
    private var changedBody = false

    private var discardDraftItem: BottomSheetPrimaryItem? = null

    /**
     * True once [applyInitialState] has run - it both gates the editor's first composition and is
     * the run-once guard for [applyInitialState] itself.
     */
    private var initialReady by mutableStateOf(false)

    // The three inline validation errors. Snapshot state so sendMessage() - a plain method - can
    // push them into the composition, replacing the legacy `xxxLayout.error = ...` assignments.
    private var recipientsError by mutableStateOf<String?>(null)
    private var subjectError by mutableStateOf<String?>(null)
    private var bodyError by mutableStateOf<String?>(null)

    private val isLibrus
        get() = app.profile.loginStoreType == LoginType.LIBRUS

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as MainActivity?) ?: return null
        context ?: return null
        app = activity.application as App
        requireContext().theme.applyStyle(Themes.appTheme, true)
        // activity, context and profile is valid
        b = MessagesComposeFragmentBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        // NOTE: the job is deliberately NOT cancelled here - saveDraftDialog() saves and navigates
        // away in the same handler, so cancelling on destroy would abort the draft write.
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (!isAdded)
            return

        vm = ViewModelProvider(
            this,
            MessagesComposeViewModel.Factory(app),
        )[MessagesComposeViewModel::class.java]

        // Register AFTER the VM exists: EventBus delivers STICKY events synchronously inside
        // register(), and both sticky handlers below dereference `vm`. A leftover sticky
        // RecipientListGetEvent (posted by a sync the user navigated away from) would otherwise
        // crash on `lateinit vm` the next time this editor opens.
        EventBus.getDefault().register(this)

        setUpBottomSheet()
        setUpFab()

        if (vm.loadTeachers())
            activity.snackbar(getString(R.string.messages_compose_recipients_downloading))

        viewLifecycleOwner.lifecycleScope.launch {
            // fillFromBundle resolves the recipient IDs against the teacher list, so the seed can
            // only be computed once that list is in.
            vm.isRecipientListReady.first { it }
            applyInitialState()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.duplicateRecipientEvents.collect {
                Toast.makeText(activity, R.string.messages_compose_recipient_exists, Toast.LENGTH_SHORT).show()
            }
        }

        b.composeView.setAppThemeContent {
            val isReady by vm.isRecipientListReady.collectAsStateWithLifecycle()

            if (!initialReady) {
                // The body field seeds itself ONCE, in its AndroidView factory, so the editor must
                // not compose before `initialBody` exists - a later value would be ignored. The
                // legacy fragment showed its fields disabled during the same wait.
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val teachers by vm.teachers.collectAsStateWithLifecycle()
                val selectedRecipients by vm.selectedRecipients.collectAsStateWithLifecycle()
                val recipientQuery by vm.recipientQuery.collectAsStateWithLifecycle()
                val subject by vm.subject.collectAsStateWithLifecycle()

                MessagesComposeScreen(
                    app = app,
                    activity = activity,
                    teachers = teachers,
                    selectedRecipients = selectedRecipients,
                    recipientQuery = recipientQuery,
                    subject = subject,
                    isRecipientListReady = isReady,
                    initialBody = initialBody,
                    textStylingEnabled = app.data.messagesConfig.textStyling,
                    isLibrus = isLibrus,
                    recipientsError = recipientsError,
                    subjectError = subjectError,
                    bodyError = bodyError,
                    suggestions = vm::suggestions,
                    categoryMembers = vm::categoryMembers,
                    // every field edit clears that field's error, as the legacy text watchers did
                    onQueryChange = {
                        recipientsError = null
                        vm.onQueryChange(it)
                    },
                    onSubjectChange = {
                        subjectError = null
                        vm.onSubjectChange(it)
                    },
                    onAddRecipient = {
                        recipientsError = null
                        vm.addRecipient(it)
                    },
                    onRemoveRecipient = {
                        recipientsError = null
                        vm.removeRecipient(it)
                    },
                    onCommitCategory = { shownIds, checkedIds ->
                        recipientsError = null
                        vm.commitCategorySelection(shownIds, checkedIds)
                    },
                    onBodyConfigReady = { bodyConfig = it },
                    onBodyChanged = {
                        bodyError = null
                        changedBody = true
                    },
                )
            }
        }
    }

    private fun setUpBottomSheet() {
        discardDraftItem = BottomSheetPrimaryItem(true)
            .withTitle(R.string.messages_compose_discard_draft)
            .withIcon(CommunityMaterial.Icon3.cmd_text_box_remove_outline)
            .withOnClickListener {
                activity.bottomSheet.close()
                discardDraftDialog()
            }

        activity.bottomSheet.prependItems(
            BottomSheetPrimaryItem(true)
                .withTitle(R.string.messages_compose_send_long)
                .withIcon(CommunityMaterial.Icon3.cmd_send_outline)
                .withOnClickListener {
                    activity.bottomSheet.close()
                    sendMessage()
                },
            BottomSheetPrimaryItem(true)
                .withTitle(R.string.messages_compose_save_draft)
                .withIcon(CommunityMaterial.Icon.cmd_content_save_edit_outline)
                .withOnClickListener {
                    activity.bottomSheet.close()
                    saveDraft()
                },
            BottomSheetSeparatorItem(true),
            BottomSheetPrimaryItem(true)
                .withTitle(R.string.menu_messages_config)
                .withIcon(CommunityMaterial.Icon.cmd_cog_outline)
                .withOnClickListener {
                    activity.bottomSheet.close()
                    MessagesConfigDialog(activity, false, null, null).show()
                }
        )
    }

    private fun setUpFab() {
        activity.navView.bottomBar.apply {
            fabEnable = true
            fabExtendedText = getString(R.string.messages_compose_send)
            fabIcon = CommunityMaterial.Icon3.cmd_send_outline

            setFabOnClickListener {
                sendMessage()
            }
        }

        activity.gainAttentionFAB()
    }

    /**
     * Seeds the editor from the nav arguments - the reply/forward/draft/message-a-teacher payloads.
     * Runs exactly once per fragment: the recipient list can be re-synced (and the view re-created)
     * afterwards, and re-applying would clobber whatever the user has typed.
     */
    private fun applyInitialState() {
        if (initialReady)
            return

        val initial = app.messageManager.fillFromBundle(
            context = activity,
            args = arguments,
            teachers = vm.teachers.value,
            greeting = vm.greeting,
        )
        vm.applyInitial(initial)
        initialBody = initial.body
        if (initial.isDraft)
            addDiscardDraftItem()

        changedBody = false
        initialReady = true
    }

    /** The discard-draft action only exists once there IS a draft, and is added a single time. */
    private fun addDiscardDraftItem() {
        discardDraftItem?.let {
            activity.bottomSheet.addItemAt(2, it)
        }
        discardDraftItem = null
    }

    /**
     * The body as sent: HTML only when the styling toolbar is on, plain text otherwise
     * (legacy `getMessageBody`).
     */
    private fun sendBody(): String {
        val config = bodyConfig ?: return ""
        return if (app.data.messagesConfig.textStyling)
            app.textStylingManager.getHtmlText(config, htmlMode = ORIGINAL)
        else
            config.editText.text?.toString() ?: ""
    }

    /**
     * The body as stored in a draft - ALWAYS serialized to HTML, regardless of the styling setting,
     * because that is what the legacy `saveAsDraft` did (and what the draft loader expects).
     */
    private fun draftBody(): String {
        val config = bodyConfig ?: return ""
        return app.textStylingManager.getHtmlText(config, htmlMode = ORIGINAL)
    }

    private fun onBeforeNavigate(): Boolean {
        val bodyText = bodyConfig?.editText?.text?.toString()?.trim() ?: ""
        val greeting = vm.greeting.text.trim()
        // navigateUp if nothing changed
        if ((!vm.changedRecipients || vm.selectedRecipients.value.isEmpty())
            && (!vm.changedSubject || vm.subject.value.isBlank())
            && (!changedBody || bodyText.isEmpty() || bodyText == greeting)
        )
            return true
        saveDraftDialog()
        return false
    }

    private fun saveDraftDialog() {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.messages_compose_save_draft_title)
            .setMessage(R.string.messages_compose_save_draft_text)
            .setPositiveButton(R.string.save) { _, _ ->
                saveDraft()
                MessagesFragment.pageSelection = Message.TYPE_DRAFT
                activity.navigate(navTarget = NavTarget.MESSAGES, skipBeforeNavigate = true)
            }
            .setNegativeButton(R.string.discard) { _, _ ->
                activity.resumePausedNavigation()
            }
            .show()
    }

    /**
     * Drops the IME's live composing spans before the body is serialized. The legacy fragment did
     * this with a `subject.requestFocus(); subject.clearFocus(); text.clearFocus(); setSelection(0)`
     * dance ("apparently this removes an underline span") - without it a composing `UnderlineSpan`
     * over the last-typed word survives `HtmlCompat.toHtml` as a stray `<u>` in the sent/saved body.
     */
    private fun clearBodyComposingSpans() {
        bodyConfig?.editText?.let {
            it.clearFocus()
            it.setSelection(0)
        }
    }

    private fun saveDraft() {
        clearBodyComposingSpans()
        launch {
            app.messageManager.saveAsDraft(
                profileId = App.profileId,
                messageId = vm.draftMessageId.value,
                recipients = vm.selectedRecipients.value,
                subject = vm.subject.value,
                bodyHtml = draftBody(),
            )
            Toast.makeText(activity, R.string.messages_compose_draft_saved, Toast.LENGTH_SHORT).show()
            vm.markSaved()
            changedBody = false
        }
        addDiscardDraftItem()
    }

    private fun discardDraftDialog() {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.messages_compose_discard_draft_title)
            .setMessage(R.string.messages_compose_discard_draft_text)
            .setPositiveButton(R.string.remove) { _, _ ->
                launch {
                    vm.draftMessageId.value?.let {
                        app.messageManager.deleteDraft(App.profileId, it)
                    }
                    Toast.makeText(activity, R.string.messages_compose_draft_discarded, Toast.LENGTH_SHORT).show()
                    activity.navigateUp(skipBeforeNavigate = true)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun sendMessage() {
        recipientsError = null
        subjectError = null
        bodyError = null

        // a leftover, un-chipped token in the recipient field (legacy `tokenValues.isNotEmpty()`)
        if (vm.recipientQuery.value.isNotBlank()) {
            recipientsError = getString(R.string.messages_compose_recipients_error)
            return
        }
        val recipients = vm.selectedRecipients.value
        if (recipients.isEmpty()) {
            recipientsError = getString(R.string.messages_compose_recipients_empty)
            return
        }
        val subject = vm.subject.value
        if (subject.isBlank() || subject.length < 3) {
            subjectError = getString(R.string.messages_compose_subject_empty)
            return
        }
        val bodyText = bodyConfig?.editText?.text?.toString() ?: ""
        if (bodyText.isBlank() || bodyText.length < 3) {
            bodyError = getString(R.string.messages_compose_text_empty)
            return
        }

        // Librus is the only profile type with length limits; the screen shows the counters, and -
        // as in the legacy fragment - going over them silently blocks the send.
        if (isLibrus) {
            if (subject.length > SUBJECT_MAX_LENGTH)
                return
            if (bodyText.length > BODY_MAX_LENGTH)
                return
        }

        activity.bottomSheet.hideKeyboard()
        clearBodyComposingSpans()

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.messages_compose_confirm_title)
            .setMessage(R.string.messages_compose_confirm_text)
            .setPositiveButton(R.string.send) { _, _ ->
                EdziennikTask.messageSend(
                    App.profileId,
                    recipients.toSet(),
                    subject.trim(),
                    sendBody(),
                ).enqueue(activity)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (!isAdded || !this::activity.isInitialized)
            return
        activity.onBeforeNavigate = this::onBeforeNavigate
    }

    override fun onPause() {
        super.onPause()
        if (!this::activity.isInitialized)
            return
        activity.onBeforeNavigate = null
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onRecipientListGetEvent(event: RecipientListGetEvent) {
        if (event.profileId != App.profileId)
            return
        EventBus.getDefault().removeStickyEvent(event)

        activity.snackbarDismiss()
        vm.setTeachers(event.teacherList)
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onMessageSentEvent(event: MessageSentEvent) {
        if (event.profileId != App.profileId)
            return
        EventBus.getDefault().removeStickyEvent(event)

        if (event.message == null) {
            activity.error(ApiError(TAG, ERROR_MESSAGE_NOT_SENT))
            return
        }

        vm.draftMessageId.value?.let { messageId ->
            launch {
                app.messageManager.deleteDraft(App.profileId, messageId)
            }
        }

        activity.snackbar(app.getString(R.string.messages_sent_success), app.getString(R.string.ok))
        activity.navigate(navTarget = NavTarget.MESSAGE, args = Bundle(
                "messageId" to event.message.id,
                "message" to app.gson.toJson(event.message),
                "sentDate" to event.sentDate
        ), skipBeforeNavigate = true)
    }
}
