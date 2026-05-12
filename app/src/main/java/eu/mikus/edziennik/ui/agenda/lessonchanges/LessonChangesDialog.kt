package eu.mikus.edziennik.ui.agenda.lessonchanges

import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import eu.mikus.edziennik.R
import eu.mikus.edziennik.databinding.DialogLessonChangeListBinding
import eu.mikus.edziennik.ui.dialogs.base.BindingDialog
import eu.mikus.edziennik.ui.timetable.LessonDetailsDialog
import eu.mikus.edziennik.utils.models.Date

class LessonChangesDialog(
    activity: AppCompatActivity,
    private val profileId: Int,
    private val defaultDate: Date,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : BindingDialog<DialogLessonChangeListBinding>(activity, onShowListener, onDismissListener) {

    override val TAG = "LessonChangesDialog"

    override fun getTitle(): String = defaultDate.formattedString
    override fun getTitleRes(): Int? = null
    override fun inflate(layoutInflater: LayoutInflater) =
        DialogLessonChangeListBinding.inflate(layoutInflater)

    override fun getPositiveButtonText() = R.string.close

    override suspend fun onShow() {
        val lessonChanges = withContext(Dispatchers.Default) {
            app.db.timetableDao().getChangesForDateNow(profileId, defaultDate)
        }

        val adapter = LessonChangesAdapter(
            activity,
            onLessonClick = {
                LessonDetailsDialog(
                    activity,
                    it,
                    onShowListener = onShowListener,
                    onDismissListener = onDismissListener
                ).show()
            }
        ).apply {
            items = lessonChanges
        }

        b.lessonChangeView.adapter = adapter
        b.lessonChangeView.layoutManager = LinearLayoutManager(activity)
    }
}
