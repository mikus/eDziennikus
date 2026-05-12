package eu.mikus.edziennik.ui.homework

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import eu.mikus.edziennik.*
import eu.mikus.edziennik.data.db.entity.Event
import eu.mikus.edziennik.databinding.HomeworkListFragmentBinding
import eu.mikus.edziennik.ext.getInt
import eu.mikus.edziennik.ext.startCoroutineTimer
import eu.mikus.edziennik.ui.base.lazypager.LazyFragment
import eu.mikus.edziennik.ui.event.EventDetailsDialog
import eu.mikus.edziennik.ui.event.EventListAdapter
import eu.mikus.edziennik.ui.event.EventManualDialog
import eu.mikus.edziennik.utils.SimpleDividerItemDecoration
import eu.mikus.edziennik.utils.models.Date
import kotlin.coroutines.CoroutineContext

class HomeworkListFragment : LazyFragment(), CoroutineScope {
    companion object {
        private const val TAG = "HomeworkListFragment"
    }

    private lateinit var app: App
    private lateinit var activity: MainActivity
    private lateinit var b: HomeworkListFragmentBinding

    private val job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    // local/private variables go here

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as MainActivity?) ?: return null
        context ?: return null
        app = activity.application as App
        b = HomeworkListFragmentBinding.inflate(inflater)
        return b.root
    }

    override fun onPageCreated(): Boolean { startCoroutineTimer(100L) {
        val homeworkDate = arguments.getInt("homeworkDate", HomeworkDate.CURRENT)

        val today = Date.getToday()
        val filter = when(homeworkDate) {
            HomeworkDate.CURRENT -> "eventDate >= '${today.stringY_m_d}' AND eventIsDone = 0"
            else -> "(eventDate < '${today.stringY_m_d}' OR eventIsDone = 1)"
        }

        val adapter = EventListAdapter(
            activity,
            showWeekDay = true,
            showDate = true,
            showType = false,
            showTime = true,
            showSubject = true,
            markAsSeen = true,
            isReversed = homeworkDate == HomeworkDate.PAST,
            onEventClick = {
                EventDetailsDialog(
                    activity,
                    it
                ).show()
            },
            onEventEditClick = {
                EventManualDialog(
                    activity,
                    it.profileId,
                    editingEvent = it
                ).show()
            }
        )

        app.db.eventDao().getAllByType(App.profileId, Event.TYPE_HOMEWORK, filter).observe(this@HomeworkListFragment, Observer { events ->
            if (!isAdded) return@Observer

            events.forEach {
                it.filterNotes()
            }

            // show/hide relevant views
            setSwipeToRefresh(events.isEmpty())
            b.progressBar.isVisible = false
            b.list.isVisible = events.isNotEmpty()
            b.noData.isVisible = events.isEmpty()
            if (events.isEmpty()) {
                return@Observer
            }

            // apply the new event list
            adapter.setAllItems(events, addSearchField = true)

            // configure the adapter & recycler view
            if (b.list.adapter == null) {
                b.list.apply {
                    setHasFixedSize(true)
                    layoutManager = LinearLayoutManager(context)
                    addItemDecoration(SimpleDividerItemDecoration(context))
                    addOnScrollListener(onScrollListener)
                    this.adapter = adapter
                }
            }

            // reapply the filter
            adapter.getSearchField()?.applyTo(adapter)
        })
    }; return true }
}
