/*
 * Copyright (c) Mikolaj Olszewski 2026-6-18.
 */

package eu.mikus.edziennik.ui.behaviour

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.getValue
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.enums.FeatureType
import eu.mikus.edziennik.data.db.enums.MetadataType
import eu.mikus.edziennik.databinding.FragmentBehaviourBinding
import eu.mikus.edziennik.ui.base.syncFeature
import eu.mikus.edziennik.ui.compose.setAppThemeContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetPrimaryItem

class BehaviourFragment : Fragment() {

    companion object {
        private const val TAG = "BehaviourFragment"
    }

    private lateinit var activity: MainActivity
    private var b: FragmentBehaviourBinding? = null
    private lateinit var viewModel: BehaviourViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as? MainActivity) ?: return null
        if (context == null) return null
        val binding = FragmentBehaviourBinding.inflate(inflater, container, false)
        b = binding
        return binding.root
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = b ?: return
        if (!isAdded) return

        viewModel = ViewModelProvider(this, BehaviourViewModel.Factory)[BehaviourViewModel::class.java]

        // Defer the prepend one navlib settle-pass past onViewCreated so it lands after navlib's
        // post-navigation bottom-sheet reset (the legacy fragment used startCoroutineTimer(100L)).
        view.postDelayed({
            if (!isAdded) return@postDelayed
            activity.bottomSheet.prependItems(
                BottomSheetPrimaryItem(true)
                    .withTitle(R.string.menu_mark_as_read)
                    .withIcon(CommunityMaterial.Icon.cmd_eye_check_outline)
                    .withOnClickListener {
                        activity.bottomSheet.close()
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                App.db.metadataDao().setAllSeen(App.profileId, MetadataType.NOTICE, true)
                            }
                            Toast.makeText(activity, R.string.main_menu_mark_as_read_success, Toast.LENGTH_SHORT).show()
                        }
                    }
            )
        }, 100)

        b.behaviourCompose.setAppThemeContent {
            val listState = rememberLazyListState()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val refreshing by activity.app.syncStatus.isRefreshing.collectAsStateWithLifecycle()
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = { syncFeature(activity, FeatureType.BEHAVIOUR) }) {
                BehaviourScreen(
                    state = state,
                    onFilterChange = viewModel::setFilter,
                    onMarkSeen = viewModel::markSeen,
                    listState = listState,
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        b = null
    }
}
