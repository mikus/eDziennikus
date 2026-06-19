/*
 * Copyright (c) Mikolaj Olszewski 2026-6-18.
 */

package eu.mikus.edziennik.ui.teachers

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Teacher
import eu.mikus.edziennik.databinding.TeachersListFragmentBinding
import eu.mikus.edziennik.ext.Intent
import eu.mikus.edziennik.ext.copyToClipboard
import eu.mikus.edziennik.ui.base.enums.NavTarget
import eu.mikus.edziennik.ui.compose.setAppThemeContent

class TeachersListFragment : Fragment() {

    companion object {
        private const val TAG = "TeachersListFragment"
    }

    private lateinit var activity: MainActivity
    private var b: TeachersListFragmentBinding? = null
    private lateinit var viewModel: TeachersViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as? MainActivity) ?: return null
        if (context == null) return null
        val binding = TeachersListFragmentBinding.inflate(inflater, container, false)
        b = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = b ?: return
        if (!isAdded) return

        viewModel = ViewModelProvider(this, TeachersViewModel.Factory)[TeachersViewModel::class.java]

        b.teachersCompose.setAppThemeContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            TeachersScreen(
                state = state,
                onCopy = ::onCopy,
                onSendMessage = ::onSendMessage,
            )
        }
    }

    private fun onCopy(name: String) {
        name.copyToClipboard(activity)
        Toast.makeText(activity, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun onSendMessage(teacher: Teacher) {
        val intent = Intent(
            Intent.ACTION_MAIN,
            "fragmentId" to NavTarget.MESSAGE_COMPOSE,
            "messageRecipientId" to teacher.id,
        )
        activity.sendBroadcast(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        b = null
    }
}
