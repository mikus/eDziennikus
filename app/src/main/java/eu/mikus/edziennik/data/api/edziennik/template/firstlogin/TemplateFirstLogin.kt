/*
 * Copyright (c) Kuba Szczodrzyński 2019-10-27. 
 */

package eu.mikus.edziennik.data.api.edziennik.template.firstlogin

import eu.mikus.edziennik.data.api.edziennik.template.DataTemplate
import eu.mikus.edziennik.data.api.edziennik.template.data.TemplateApi
import eu.mikus.edziennik.data.api.edziennik.template.data.TemplateWeb
import eu.mikus.edziennik.data.db.entity.Profile

class TemplateFirstLogin(val data: DataTemplate, val onSuccess: () -> Unit) {
    companion object {
        private const val TAG = "TemplateFirstLogin"
    }

    private val web = TemplateWeb(data, null)
    private val api = TemplateApi(data, null)
    private val profileList = mutableListOf<Profile>()

    init {
        /*TemplateLoginWeb(data) {
            web.webGet(TAG, "get all accounts") { text ->
                //val accounts = json.getJsonArray("accounts")

                EventBus.getDefault().post(FirstLoginFinishedEvent(profileList, data.loginStore))
                onSuccess()
            }
        }*/
    }
}
