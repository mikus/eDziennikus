/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-4.
 */

package eu.mikus.edziennik.ui.messages.compose

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
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
import eu.mikus.edziennik.data.db.entity.Teacher
import eu.mikus.edziennik.data.db.enums.LoginType
import eu.mikus.edziennik.databinding.MessagesComposeFragmentBinding
import eu.mikus.edziennik.ext.Bundle
import eu.mikus.edziennik.ui.base.ScreenFab
import eu.mikus.edziennik.ui.base.enums.NavTarget
import eu.mikus.edziennik.ui.compose.setAppThemeContent
import eu.mikus.edziennik.ui.dialogs.settings.MessagesConfigDialog
import eu.mikus.edziennik.ui.messages.list.MessagesFragment
import eu.mikus.edziennik.utils.Themes
import eu.mikus.edziennik.utils.managers.TextStylingManager.HtmlMode.ORIGINAL
import eu.mikus.edziennik.utils.managers.TextStylingManager.StylingConfigBase
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

    /** Drives the discard-draft row. The fragment is the expert: it learns of a draft at seed time
     *  (`initial.isDraft`) and after every successful save. */
    private var hasDraft = false

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

    /**
     * Which confirm dialog is showing, if any. ONE nullable field rather than three booleans, so two
     * dialogs cannot be open at once: ViewGroup split-touch is on by default, and two TextButtons
     * receiving pointer streams in the same frame could otherwise run saveDraft() + navigate() AND
     * resumePausedNavigation(), or enqueue messageSend twice.
     */
    private sealed interface Prompt {
        data object SaveDraft : Prompt
        data object DiscardDraft : Prompt

        /** Carries the values validated in [sendMessage], so a confirmed send cannot use others. */
        data class Send(val recipients: List<Teacher>, val subject: String) : Prompt
    }

    private var prompt by mutableStateOf<Prompt?>(null)

    /** Reads and clears in one step, so a second press in the same frame is a no-op. */
    private fun consumePrompt(): Prompt? = prompt.also { prompt = null }

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
        // NOTE: the job is deliberately NOT cancelled here - saveDraftAndLeave() saves and navigates
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

        declareScreenActions()
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

            // Sibling of the initialReady branch, not inside its else: a prompt nested there could
            // not render while the loading indicator is up. Defence in depth - that state is
            // currently unreachable (the FAB path returns early on empty recipients, the dirty check
            // cannot be true before the screen composes, and the discard item only exists after
            // applyInitialState has set initialReady) - but it is free.
            when (prompt) {
                null -> Unit
                Prompt.SaveDraft -> ConfirmDialog(
                    title = R.string.messages_compose_save_draft_title,
                    message = R.string.messages_compose_save_draft_text,
                    confirmLabel = R.string.save,
                    dismissLabel = R.string.discard,
                    // Gate on the READ, not just call it: two lambdas that call consumePrompt() and
                    // discard the result both still run if SAVE and DISCARD are pressed in the same
                    // input dispatch, which would save + navigate(MESSAGES) AND then replay the
                    // originally-blocked target via resumePausedNavigation().
                    onConfirm = { if (consumePrompt() == Prompt.SaveDraft) saveDraftAndLeave() },
                    // DISCARD: don't save, but DO let the blocked navigation through
                    onDismissButton = {
                        if (consumePrompt() == Prompt.SaveDraft) activity.resumePausedNavigation()
                    },
                    // dismissed without choosing: stay in the editor, navigation stays paused
                    onDismissRequest = { prompt = null },
                )
                Prompt.DiscardDraft -> ConfirmDialog(
                    title = R.string.messages_compose_discard_draft_title,
                    message = R.string.messages_compose_discard_draft_text,
                    confirmLabel = R.string.remove,
                    dismissLabel = R.string.cancel,
                    onConfirm = { if (consumePrompt() == Prompt.DiscardDraft) discardDraft() },
                    onDismissButton = { prompt = null },
                    onDismissRequest = { prompt = null },
                )
                is Prompt.Send -> ConfirmDialog(
                    title = R.string.messages_compose_confirm_title,
                    message = R.string.messages_compose_confirm_text,
                    confirmLabel = R.string.send,
                    dismissLabel = R.string.cancel,
                    onConfirm = {
                        (consumePrompt() as? Prompt.Send)?.let { send(it.recipients, it.subject) }
                    },
                    onDismissButton = { prompt = null },
                    onDismissRequest = { prompt = null },
                )
            }
        }
    }

    private fun declareScreenActions() {
        activity.setScreenActions(messagesComposeActions(
            hasDraft = hasDraft,
            onSend = ::sendMessage,
            onSaveDraft = ::saveDraft,
            onDiscard = { prompt = Prompt.DiscardDraft },
            onConfig = { MessagesConfigDialog(activity, false, null, null).show() },
        ))
    }

    private fun setUpFab() {
        activity.setScreenFab(ScreenFab(
            labelRes = R.string.messages_compose_send,
            icon = CommunityMaterial.Icon3.cmd_send_outline,
        ) { sendMessage() })

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
        if (initial.isDraft) {
            hasDraft = true
            declareScreenActions()
        }

        changedBody = false
        initialReady = true
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
        prompt = Prompt.SaveDraft
        return false
    }

    private fun saveDraftAndLeave() {
        saveDraft()
        MessagesFragment.pageSelection = Message.TYPE_DRAFT
        activity.navigate(navTarget = NavTarget.MESSAGES, skipBeforeNavigate = true)
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
        hasDraft = true
        declareScreenActions()
    }

    private fun discardDraft() {
        launch {
            vm.draftMessageId.value?.let {
                app.messageManager.deleteDraft(App.profileId, it)
            }
            Toast.makeText(activity, R.string.messages_compose_draft_discarded, Toast.LENGTH_SHORT).show()
            activity.navigateUp(skipBeforeNavigate = true)
        }
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

        // The platform IMM, matching navlib's hideKeyboard() byte-for-byte. Deliberately NOT
        // WindowInsetsControllerCompat: on API 30+ that resolves to Impl30, which routes through the
        // platform WindowInsetsController (async, animated) instead of the IMM — a real behaviour
        // change on most devices, and this refactor must be invisible. (Below 30 it picks Impl23/26,
        // which do use the IMM; the no-op base Impl needs SDK_INT < 20, unreachable at minSdk 23.)
        (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(requireView().windowToken, 0)
        clearBodyComposingSpans()

        prompt = Prompt.Send(recipients = recipients, subject = subject)
    }

    /** [sendBody] is read HERE, at confirm time, not when the prompt was raised. */
    private fun send(recipients: List<Teacher>, subject: String) {
        EdziennikTask.messageSend(
            App.profileId,
            recipients.toSet(),
            subject.trim(),
            sendBody(),
        ).enqueue(activity)
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

/**
 * The shared shape of the three message-compose confirms as a native M3 [AlertDialog], so they get
 * AppTheme's M3 shapes/colours/buttons instead of the platform MD2 dialog theme.
 *
 * File-private on purpose: the fan-out is one file / three call sites, and every helper promoted to
 * a shared location in earlier phases cleared at least two files.
 *
 * All three callbacks are REQUIRED. `onDismissRequest` fires for back press and outside tap only, so
 * the dismiss button needs its own slot - the save-draft prompt's three outcomes (save / discard-and-
 * navigate / stay put) are distinguishable ONLY if those two are separate.
 */
@Composable
private fun ConfirmDialog(
    @StringRes title: Int,
    @StringRes message: Int,
    @StringRes confirmLabel: Int,
    @StringRes dismissLabel: Int,
    onConfirm: () -> Unit,
    onDismissButton: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(title)) },
        // M3 measures the `text` slot with weight(1f, fill = false) and does NOT scroll it, but the
        // AppCompat dialogs these replace wrapped their message in AlertController's NestedScrollView.
        // Without this, the two-paragraph save-draft body clips unreachably at a large font scale or
        // in landscape - an undeclared behaviour change. The buttons sit outside the weighted slot, so
        // they stay reachable either way.
        text = { Text(stringResource(message), modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(confirmLabel)) }
        },
        dismissButton = {
            TextButton(onClick = onDismissButton) { Text(stringResource(dismissLabel)) }
        },
    )
}
