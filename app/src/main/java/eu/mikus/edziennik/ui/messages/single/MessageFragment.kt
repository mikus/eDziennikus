/*
 * Copyright (c) Mikolaj Olszewski 2026-6-24.
 */

package eu.mikus.edziennik.ui.messages.single

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.api.edziennik.EdziennikTask
import eu.mikus.edziennik.data.db.full.MessageFull
import eu.mikus.edziennik.databinding.MessageFragmentBinding
import eu.mikus.edziennik.ext.Bundle
import eu.mikus.edziennik.ui.base.enums.NavTarget
import eu.mikus.edziennik.ui.compose.setAppThemeContent
import eu.mikus.edziennik.ui.dialogs.settings.MessagesConfigDialog
import eu.mikus.edziennik.ui.messages.list.MessagesFragment
import eu.mikus.edziennik.ui.notes.NoteListDialog
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetPrimaryItem

class MessageFragment : Fragment() {

    companion object {
        private const val TAG = "MessageFragment"
    }

    private lateinit var app: App
    private lateinit var activity: MainActivity
    private var b: MessageFragmentBinding? = null
    private lateinit var viewModel: MessageReadViewModel
    private var armedFor: Long = -2L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as? MainActivity) ?: return null
        if (context == null) return null
        app = activity.application as App
        val binding = MessageFragmentBinding.inflate(inflater, container, false)
        b = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = b ?: return
        if (!isAdded) return

        val messageId = arguments?.getLong("messageId", -1L) ?: -1L
        viewModel = ViewModelProvider(
            this, MessageReadViewModel.Factory(messageId, activity.applicationContext),
        )[MessageReadViewModel::class.java]

        view.postDelayed({
            if (!isAdded) return@postDelayed
            activity.bottomSheet.prependItem(
                BottomSheetPrimaryItem(true)
                    .withTitle(R.string.menu_messages_config)
                    .withIcon(CommunityMaterial.Icon.cmd_cog_outline)
                    .withOnClickListener {
                        activity.bottomSheet.close()
                        MessagesConfigDialog(activity, false, null, null).show()
                    }
            )
        }, 100)

        b.messageCompose.setAppThemeContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            // Side effects (navigateUp / FAB arm / pageSelection) run in the EFFECT phase, keyed to state —
            // never in the composition body (the HomeworkFragment precedent does no side effects there).
            LaunchedEffect(state) { onState(state) }
            MessageReadScreen(
                state = state,
                onClose = { activity.navigateUp() },
                onStarClick = { msg -> viewModel.setStarred(msg, !msg.isStarred) },
                onReply = ::reply,
                onForward = ::forward,
                onDelete = ::confirmDelete,
                onDownload = { msg -> EdziennikTask.messageGet(App.profileId, msg).enqueue(activity) },
                onNotes = { msg ->
                    NoteListDialog(activity = activity, owner = msg, onShowListener = null, onDismissListener = null).show()
                },
            )
        }
    }

    private fun onState(state: MessageReadUiState) {
        when (state) {
            MessageReadUiState.NotFound -> activity.navigateUp()
            is MessageReadUiState.Content -> {
                val m = state.message
                MessagesFragment.pageSelection = m.type   // back lands on the message's own tab (type == tab index)
                if (armedFor != m.id) {
                    armedFor = m.id
                    if (m.isReceived || m.isDeleted) {
                        activity.navView.apply {
                            bottomBar.apply {
                                fabEnable = true
                                fabExtendedText = getString(R.string.messages_reply)
                                fabIcon = CommunityMaterial.Icon3.cmd_reply_outline
                            }
                            setFabOnClickListener { reply(m) }
                        }
                        activity.gainAttentionFAB()
                    }
                }
            }
            MessageReadUiState.Loading -> {}
        }
    }

    private fun reply(message: MessageFull) {
        activity.navigate(navTarget = NavTarget.MESSAGE_COMPOSE, args = Bundle(
            "message" to app.gson.toJson(message), "type" to "reply",
        ))
    }

    private fun forward(message: MessageFull) {
        activity.navigate(navTarget = NavTarget.MESSAGE_COMPOSE, args = Bundle(
            "message" to app.gson.toJson(message), "type" to "forward",
        ))
    }

    private fun confirmDelete(message: MessageFull) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.messages_delete_confirmation)
            .setMessage(R.string.messages_delete_confirmation_text)
            .setPositiveButton(R.string.ok) { _, _ ->
                viewModel.delete(message)
                Toast.makeText(activity, R.string.messages_deleted, Toast.LENGTH_SHORT).show()
                activity.navigateUp()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        b = null
    }
}
