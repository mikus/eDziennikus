/*
 * Copyright (c) Mikolaj Olszewski 2026-6-25.
 */

package eu.mikus.edziennik.ui.messages.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Message
import eu.mikus.edziennik.data.db.enums.FeatureType
import eu.mikus.edziennik.data.db.full.MessageFull
import eu.mikus.edziennik.databinding.MessagesFragmentBinding
import eu.mikus.edziennik.ext.Bundle
import eu.mikus.edziennik.ui.base.ScreenAction
import eu.mikus.edziennik.ui.base.ScreenFab
import eu.mikus.edziennik.ui.base.enums.NavTarget
import eu.mikus.edziennik.ui.base.syncFeature
import eu.mikus.edziennik.ui.compose.setAppThemeContent
import eu.mikus.edziennik.ui.dialogs.settings.MessagesConfigDialog

class MessagesFragment : Fragment() {

    companion object {
        private const val TAG = "MessagesFragment"
        var pageSelection = 0
    }

    private lateinit var app: App
    private lateinit var activity: MainActivity
    private var b: MessagesFragmentBinding? = null
    private lateinit var viewModel: MessagesViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as? MainActivity) ?: return null
        if (context == null) return null
        app = activity.application as App
        val binding = MessagesFragmentBinding.inflate(inflater, container, false)
        b = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = b ?: return
        if (!isAdded) return

        val messageId = arguments?.getLong("messageId", -1L) ?: -1L
        if (messageId != -1L) {
            arguments?.remove("messageId")
            activity.navigate(navTarget = NavTarget.MESSAGE, args = Bundle("messageId" to messageId))
            return
        }

        viewModel = ViewModelProvider(this, MessagesViewModel.Factory)[MessagesViewModel::class.java]

        activity.setScreenFab(ScreenFab(R.string.compose, CommunityMaterial.Icon3.cmd_pencil_outline) {
            activity.navigate(navTarget = NavTarget.MESSAGE_COMPOSE)
        })
        activity.gainAttentionFAB()

        activity.setScreenActions(listOf(
            ScreenAction(R.string.menu_messages_config, CommunityMaterial.Icon.cmd_cog_outline) {
                MessagesConfigDialog(activity, false, null, null).show()
            },
        ))

        b.messagesCompose.setAppThemeContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val refreshing by app.syncStatus.isRefreshing.collectAsStateWithLifecycle()
            MessagesScreen(
                state = state,
                onQueryChange = viewModel::setQuery,
                onMessageClick = ::onMessageClick,
                onStarClick = { viewModel.setStarred(it, !it.isStarred) },
                initialPage = pageSelection,
                onPageChange = { pageSelection = it },
                isRefreshing = refreshing,
                onRefresh = { type ->
                    when (type) {
                        Message.TYPE_RECEIVED -> syncFeature(activity, FeatureType.MESSAGES_INBOX)
                        Message.TYPE_SENT -> syncFeature(activity, FeatureType.MESSAGES_SENT)
                    }
                },
            )
        }
    }

    private fun onMessageClick(message: MessageFull) {
        if (message.isDraft) {
            activity.navigate(navTarget = NavTarget.MESSAGE_COMPOSE, args = Bundle("message" to app.gson.toJson(message)))
        } else {
            activity.navigate(navTarget = NavTarget.MESSAGE, args = Bundle("messageId" to message.id))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        b = null
    }
}
