/*
 * Copyright (c) Mikolaj Olszewski 2026-6-30.
 */

package eu.mikus.edziennik.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial.Icon
import eu.szkolny.font.SzkolnyFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.api.edziennik.EdziennikTask
import eu.mikus.edziennik.data.db.entity.Noteable
import eu.mikus.edziennik.data.db.enums.FeatureType
import eu.mikus.edziennik.databinding.HomeFragmentBinding
import eu.mikus.edziennik.ext.JsonObject
import eu.mikus.edziennik.ui.base.enums.NavTarget
import eu.mikus.edziennik.ui.compose.setAppThemeContent
import eu.mikus.edziennik.ui.dialogs.BellSyncTimeChooseDialog
import eu.mikus.edziennik.ui.dialogs.settings.HomeConfigDialog
import eu.mikus.edziennik.ui.dialogs.settings.StudentNumberDialog
import eu.mikus.edziennik.ui.event.EventDetailsDialog
import eu.mikus.edziennik.ui.event.EventManualDialog
import eu.mikus.edziennik.ui.home.cards.HomeArchiveCard
import eu.mikus.edziennik.ui.home.cards.HomeAvailabilityCard
import eu.mikus.edziennik.ui.notes.NoteDetailsDialog
import eu.mikus.edziennik.ui.notes.NoteEditorDialog
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetPrimaryItem
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetSeparatorItem

class HomeFragment : Fragment() {
    companion object {
        private const val TAG = "HomeFragment"
    }

    private lateinit var app: App
    private lateinit var activity: MainActivity
    private var b: HomeFragmentBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as? MainActivity) ?: return null
        if (context == null) return null
        app = activity.application as App
        val binding = HomeFragmentBinding.inflate(inflater, container, false)
        b = binding
        binding.refreshLayout.setParent(activity.swipeRefreshLayout)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = b ?: return
        if (!isAdded) return

        val viewModel = ViewModelProvider(
            this, HomeViewModel.Factory(activity.applicationContext),
        )[HomeViewModel::class.java]

        activity.gainAttention()

        view.postDelayed({
            if (!isAdded) return@postDelayed
            activity.bottomSheet.prependItems(
                BottomSheetPrimaryItem(true)
                    .withTitle(R.string.menu_add_remove_cards)
                    .withIcon(Icon.cmd_card_bulleted_settings_outline)
                    .withOnClickListener(View.OnClickListener {
                        activity.bottomSheet.close()
                        HomeCardsDialog(activity, reloadOnDismiss = true).show()
                    }),
                BottomSheetPrimaryItem(true)
                    .withTitle(R.string.menu_home_config)
                    .withIcon(Icon.cmd_cog_outline)
                    .withOnClickListener(View.OnClickListener {
                        activity.bottomSheet.close()
                        HomeConfigDialog(activity, reloadOnDismiss = true).show()
                    }),
                BottomSheetPrimaryItem(true)
                    .withTitle(R.string.menu_set_student_number)
                    .withIcon(SzkolnyFont.Icon.szf_clipboard_list_outline)
                    .withOnClickListener(View.OnClickListener {
                        activity.bottomSheet.close()
                        StudentNumberDialog(activity, app.profile, onDismissListener = { app.profileSave() }).show()
                    }),
                BottomSheetSeparatorItem(true),
                BottomSheetPrimaryItem(true)
                    .withTitle(R.string.menu_mark_everything_as_read)
                    .withIcon(Icon.cmd_eye_check_outline)
                    .withOnClickListener(View.OnClickListener {
                        activity.bottomSheet.close()
                        lifecycleScope.launch {
                            withContext(Dispatchers.Default) {
                                if (!app.data.uiConfig.enableMarkAsReadAnnouncements) {
                                    app.db.metadataDao().setAllSeenExceptMessagesAndAnnouncements(App.profileId, true)
                                } else {
                                    app.db.metadataDao().setAllSeenExceptMessages(App.profileId, true)
                                }
                            }
                            Toast.makeText(activity, R.string.main_menu_mark_as_read_success, Toast.LENGTH_SHORT).show()
                        }
                    }),
            )
        }, 100)

        b.composeView.setAppThemeContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            HomeScreen(
                state = state,
                onReorder = viewModel::reorder,
                onRemove = viewModel::removeCard,
                onConfigureCards = { HomeCardsDialog(activity, reloadOnDismiss = true).show() },
                gradeColor = { Color(app.gradesManager.getGradeColor(it)) },
                onLuckyClick = { StudentNumberDialog(activity, app.profile, onDismissListener = { app.profileSave() }).show() },
                onEventClick = { EventDetailsDialog(activity, it).show() },
                onEventEditClick = { EventManualDialog(activity, it.profileId, editingEvent = it).show() },
                onOpenAgenda = { activity.navigate(navTarget = NavTarget.AGENDA) },
                onOpenGrades = { activity.navigate(navTarget = NavTarget.GRADES) },
                onNoteClick = { note ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        val owner = withContext(Dispatchers.IO) { app.noteManager.getOwner(note) } as? Noteable
                        NoteDetailsDialog(activity, owner, note).show()
                    }
                },
                onAddNote = { NoteEditorDialog(activity, owner = null, editingNote = null, profileId = App.profileId).show() },
                onOpenNotes = { activity.navigate(navTarget = NavTarget.NOTES) },
                onOpenTimetable = { activity.navigate(navTarget = NavTarget.TIMETABLE) },
                onTimetableBellSync = { BellSyncTimeChooseDialog(activity).show() },
                onTimetableFullscreen = { activity.startActivity(Intent(activity, CounterActivity::class.java)) },
                onTimetableSync = { weekStart ->
                    EdziennikTask.syncProfile(
                        profileId = App.profileId,
                        featureTypes = setOf(FeatureType.TIMETABLE),
                        arguments = JsonObject("weekStart" to weekStart),
                    ).enqueue(activity)
                },
                wrappedCardContent = { cardId -> WrappedCard(cardId) },
                setRefreshEnabled = { b.refreshLayout.isEnabled = it },
            )
        }
    }

    /** Hosts a legacy HomeCard (Timetable/Archive/Availability) inside Compose via its existing bind(). */
    @androidx.compose.runtime.Composable
    private fun WrappedCard(cardId: Int) {
        AndroidView(factory = { ctx ->
            val root = LayoutInflater.from(ctx).inflate(R.layout.card_home, null) as MaterialCardView
            val holder = HomeCardAdapter.ViewHolder(root)
            val card: HomeCard = when (cardId) {
                102 -> HomeAvailabilityCard(102, app, activity, this, app.profile)
                else -> HomeArchiveCard(101, app, activity, this, app.profile)
            }
            card.bind(0, holder)
            root
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        b = null
    }
}
