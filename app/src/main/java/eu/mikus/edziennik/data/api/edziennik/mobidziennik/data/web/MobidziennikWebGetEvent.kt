/*
 * Copyright (c) Kuba Szczodrzyński 2021-10-1.
 */

package eu.mikus.edziennik.data.api.edziennik.mobidziennik.data.web

import org.greenrobot.eventbus.EventBus
import eu.mikus.edziennik.data.api.POST
import eu.mikus.edziennik.data.api.Regexes
import eu.mikus.edziennik.data.api.edziennik.mobidziennik.DataMobidziennik
import eu.mikus.edziennik.data.api.edziennik.mobidziennik.data.MobidziennikWeb
import eu.mikus.edziennik.data.api.events.EventGetEvent
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.ext.get
import eu.mikus.edziennik.utils.models.Date

class MobidziennikWebGetEvent(
    override val data: DataMobidziennik,
    val event: EventFull,
    val onSuccess: () -> Unit
) : MobidziennikWeb(data, null) {
    companion object {
        private const val TAG = "MobidziennikWebGetEvent"
    }

    init {
        val params = listOf(
            "typ" to "kalendarz",
            "uczen" to data.studentId,
            "id" to event.id,
        )

        webGet(TAG, "/dziennik/ajaxkalendarzklasowy", method = POST, parameters = params) { text ->
            Regexes.MOBIDZIENNIK_EVENT_CONTENT.find(text)?.let {
                val topic = it[1]
                val teacherName = it[2]
                val teacher = data.getTeacherByLastFirst(teacherName)
                val addedDate = Date.fromY_m_d(it[3])
                val body = it[4]
                    .replace("\n", "")
                    .replace(Regexes.HTML_BR, "\n")

                event.topic = topic
                event.homeworkBody = body
                event.isDownloaded = true
                event.teacherId = teacher.id
                event.addedDate = addedDate.inMillis
            }

            data.eventList.add(event)
            data.eventListReplace = true

            EventBus.getDefault().postSticky(EventGetEvent(event))
            onSuccess()
        }
    }
}
