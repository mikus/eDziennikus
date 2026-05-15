/*
 * Copyright (c) Kacper Ziubryniewicz 2020-1-6
 */

package eu.mikus.edziennik.data.db.entity

import android.content.Context
import android.widget.ImageView
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import com.google.gson.JsonObject
import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.db.enums.LoginType
import eu.mikus.edziennik.ext.dateToSemester
import eu.mikus.edziennik.ext.getDrawable
import eu.mikus.edziennik.ext.getHolder
import eu.mikus.edziennik.utils.models.Date
import pl.szczodrzynski.navlib.drawer.IDrawerProfile

@Entity(tableName = "profiles", primaryKeys = ["profileId"])
open class Profile(
        @ColumnInfo(name = "profileId")
        override var id: Int, /* needs to be var for ProfileArchiver */
        val loginStoreId: Int,
        val loginStoreType: LoginType,

        override var name: String = "",
        override var subname: String? = null,

        /**
         * The name of the student.
         * This doesn't change, no matter if it's a parent or student account.
         */
        var studentNameLong: String = "",
        var studentNameShort: String = "",
        /**
         * A full name of the account owner.
         * If null, then it's a student account.
         * If not null, then it's a parent account with this name.
         */
        var accountName: String? = null,

        val studentData: JsonObject = JsonObject()

) : IDrawerProfile {
    companion object {
        const val AGENDA_DEFAULT = 0
        const val AGENDA_CALENDAR = 1
    }

    override var image: String? = null
    var empty = true
    var archived = false
    var syncEnabled = true
    var userCode = ""

    /**
     * A unique ID matching [archived] profiles with current ones
     * and vice-versa.
     */
    var archiveId: Int? = null

    /**
     * The student's number in the class register.
     */
    var studentNumber = -1
    var studentClassName: String? = null
    var studentSchoolYearStart = Date.getToday().let { if (it.month < 9) it.year - 1 else it.year }
    var dateSemester1Start = Date(studentSchoolYearStart, 9, 1)
    var dateSemester2Start = Date(studentSchoolYearStart + 1, 2, 1)
    var dateYearEnd = Date(studentSchoolYearStart + 1, 6, 30)
    var disabledNotifications: List<Long>? = null
    var lastReceiversSync: Long = 0

    val currentSemester
        get() = dateToSemester(Date.getToday())
    val isParent
        get() = accountName != null
    val accountOwnerName
        get() = accountName ?: studentNameLong
    val registerName
        get() = loginStoreType.name.lowercase()

    @delegate:Ignore
    @delegate:Transient
    val config by lazy { App.config[this.id] }

    override fun getImageDrawable(context: Context) = this.getDrawable(context)
    override fun getImageHolder(context: Context) = this.getHolder()
    override fun applyImageTo(imageView: ImageView) {
        getImageHolder(imageView.context).applyTo(imageView)
    }
}
