/*
 * Copyright (c) Kacper Ziubryniewicz 2020-5-12
 */

package eu.mikus.edziennik.data.api.edziennik.podlasie.firstlogin

import org.greenrobot.eventbus.EventBus
import eu.mikus.edziennik.data.api.PODLASIE_API_LOGOUT_DEVICES_ENDPOINT
import eu.mikus.edziennik.data.api.PODLASIE_API_USER_ENDPOINT
import eu.mikus.edziennik.data.api.edziennik.podlasie.DataPodlasie
import eu.mikus.edziennik.data.api.edziennik.podlasie.data.PodlasieApi
import eu.mikus.edziennik.data.api.edziennik.podlasie.login.PodlasieLoginApi
import eu.mikus.edziennik.data.api.events.FirstLoginFinishedEvent
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.enums.LoginType
import eu.mikus.edziennik.ext.fixName
import eu.mikus.edziennik.ext.getShortName
import eu.mikus.edziennik.ext.getString
import eu.mikus.edziennik.ext.set

class PodlasieFirstLogin(val data: DataPodlasie, val onSuccess: () -> Unit) {
    companion object {
        const val TAG = "PodlasieFirstLogin"
    }

    private val api = PodlasieApi(data, null)

    init {
        PodlasieLoginApi(data) {
            doLogin()
        }
    }

    private fun doLogin() {
        if (data.loginStore.getLoginData("logoutDevices", false)) {
            data.loginStore.removeLoginData("logoutDevices")
            api.apiGet(TAG, PODLASIE_API_LOGOUT_DEVICES_ENDPOINT) {
                doLogin()
            }
            return
        }

        api.apiGet(TAG, PODLASIE_API_USER_ENDPOINT) { json ->
            val uuid = json.getString("Uuid")
            val login = json.getString("Login")
            val firstName = json.getString("FirstName")
            val lastName = json.getString("LastName")
            val studentNameLong = "$firstName $lastName".fixName()
            val studentNameShort = studentNameLong.getShortName()
            val schoolName = json.getString("SchoolName")
            val className = json.getString("SchoolClass")
            val schoolYear = json.getString("ActualSchoolYear")?.replace(' ', '/')
            val semester = json.getString("ActualTermShortcut")?.length
            val apiUrl = json.getString("URL")

            val profile = Profile(
                    data.loginStore.id,
                    data.loginStore.id,
                    LoginType.PODLASIE,
                    studentNameLong,
                    login,
                    studentNameLong,
                    studentNameShort,
                    null
            ).apply {
                studentData["studentId"] = uuid
                studentData["studentLogin"] = login
                studentData["schoolName"] = schoolName
                studentData["className"] = className
                studentData["schoolYear"] = schoolYear
                studentData["currentSemester"] = semester ?: 1
                studentData["apiUrl"] = apiUrl

                schoolYear?.split('/')?.get(0)?.toInt()?.let {
                    studentSchoolYearStart = it
                }
                studentClassName = className
            }

            EventBus.getDefault().postSticky(FirstLoginFinishedEvent(listOf(profile), data.loginStore))
            onSuccess()
        }
    }
}
