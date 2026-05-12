/*
 * Copyright (c) Kuba Szczodrzyński 2019-10-5.
 */

package eu.mikus.edziennik.data.api.edziennik.template

import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.api.models.Data
import eu.mikus.edziennik.data.db.entity.LoginStore
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.enums.LoginMethod
import eu.mikus.edziennik.ext.currentTimeUnix
import eu.mikus.edziennik.ext.getStudentData
import eu.mikus.edziennik.ext.isNotNullNorEmpty
import eu.mikus.edziennik.ext.set

/**
 * Use http://patorjk.com/software/taag/#p=display&f=Big for the ascii art
 *
 * Use https://codepen.io/kubasz/pen/RwwwbGN to easily generate the student data getters/setters
 */
class DataTemplate(app: App, profile: Profile?, loginStore: LoginStore) : Data(app, profile, loginStore) {

    fun isWebLoginValid() = webExpiryTime-30 > currentTimeUnix() && webCookie.isNotNullNorEmpty()
    fun isApiLoginValid() = apiExpiryTime-30 > currentTimeUnix() && apiToken.isNotNullNorEmpty()

    override fun satisfyLoginMethods() {
        loginMethods.clear()
        if (isWebLoginValid()) {
            loginMethods += LoginMethod.TEMPLATE_WEB
            app.cookieJar.set("eregister.example.com", "AuthCookie", webCookie)
        }
        if (isApiLoginValid())
            loginMethods += LoginMethod.TEMPLATE_API
    }

    override fun generateUserCode() = "TEMPLATE:DO_NOT_USE"

    /*   __          __  _
         \ \        / / | |
          \ \  /\  / /__| |__
           \ \/  \/ / _ \ '_ \
            \  /\  /  __/ |_) |
             \/  \/ \___|_._*/
    private var mWebCookie: String? = null
    var webCookie: String?
        get() { mWebCookie = mWebCookie ?: profile?.getStudentData("webCookie", null); return mWebCookie }
        set(value) { profile["webCookie"] = value; mWebCookie = value }

    private var mWebExpiryTime: Long? = null
    var webExpiryTime: Long
        get() { mWebExpiryTime = mWebExpiryTime ?: profile?.getStudentData("webExpiryTime", 0L); return mWebExpiryTime ?: 0L }
        set(value) { profile["webExpiryTime"] = value; mWebExpiryTime = value }

    /*                   _
             /\         (_)
            /  \   _ __  _
           / /\ \ | '_ \| |
          / ____ \| |_) | |
         /_/    \_\ .__/|_|
                  | |
                  |*/
    private var mApiToken: String? = null
    var apiToken: String?
        get() { mApiToken = mApiToken ?: profile?.getStudentData("apiToken", null); return mApiToken }
        set(value) { profile["apiToken"] = value; mApiToken = value }

    private var mApiExpiryTime: Long? = null
    var apiExpiryTime: Long
        get() { mApiExpiryTime = mApiExpiryTime ?: profile?.getStudentData("apiExpiryTime", 0L); return mApiExpiryTime ?: 0L }
        set(value) { profile["apiExpiryTime"] = value; mApiExpiryTime = value }
}
