/*
 * Copyright (c) Kuba Szczodrzyński 2022-10-11.
 */

package eu.mikus.edziennik.data.api.edziennik.usos

import com.google.gson.JsonObject
import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.api.models.Data
import eu.mikus.edziennik.data.db.entity.LoginStore
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.enums.LoginMethod
import eu.mikus.edziennik.ext.getStudentData
import eu.mikus.edziennik.ext.set

class DataUsos(
    app: App,
    profile: Profile?,
    loginStore: LoginStore,
) : Data(app, profile, loginStore) {

    fun isApiLoginValid() = oauthTokenKey != null && oauthTokenSecret != null && oauthTokenIsUser

    override fun satisfyLoginMethods() {
        loginMethods.clear()
        if (isApiLoginValid()) {
            loginMethods += LoginMethod.USOS_API
        }
    }

    override fun generateUserCode() = "$schoolId:${profile?.studentNumber ?: studentId}"

    var schoolId: String?
        get() { mSchoolId = mSchoolId ?: loginStore.getLoginData("schoolId", null); return mSchoolId }
        set(value) { loginStore.putLoginData("schoolId", value); mSchoolId = value }
    private var mSchoolId: String? = null
    
    var instanceUrl: String?
        get() { mInstanceUrl = mInstanceUrl ?: loginStore.getLoginData("instanceUrl", null); return mInstanceUrl }
        set(value) { loginStore.putLoginData("instanceUrl", value); mInstanceUrl = value }
    private var mInstanceUrl: String? = null

    var oauthLoginResponse: String?
        get() { mOauthLoginResponse = mOauthLoginResponse ?: loginStore.getLoginData("oauthLoginResponse", null); return mOauthLoginResponse }
        set(value) { loginStore.putLoginData("oauthLoginResponse", value); mOauthLoginResponse = value }
    private var mOauthLoginResponse: String? = null

    var oauthConsumerKey: String?
        get() { mOauthConsumerKey = mOauthConsumerKey ?: loginStore.getLoginData("oauthConsumerKey", null); return mOauthConsumerKey }
        set(value) { loginStore.putLoginData("oauthConsumerKey", value); mOauthConsumerKey = value }
    private var mOauthConsumerKey: String? = null

    var oauthConsumerSecret: String?
        get() { mOauthConsumerSecret = mOauthConsumerSecret ?: loginStore.getLoginData("oauthConsumerSecret", null); return mOauthConsumerSecret }
        set(value) { loginStore.putLoginData("oauthConsumerSecret", value); mOauthConsumerSecret = value }
    private var mOauthConsumerSecret: String? = null

    var oauthTokenKey: String?
        get() { mOauthTokenKey = mOauthTokenKey ?: loginStore.getLoginData("oauthTokenKey", null); return mOauthTokenKey }
        set(value) { loginStore.putLoginData("oauthTokenKey", value); mOauthTokenKey = value }
    private var mOauthTokenKey: String? = null

    var oauthTokenSecret: String?
        get() { mOauthTokenSecret = mOauthTokenSecret ?: loginStore.getLoginData("oauthTokenSecret", null); return mOauthTokenSecret }
        set(value) { loginStore.putLoginData("oauthTokenSecret", value); mOauthTokenSecret = value }
    private var mOauthTokenSecret: String? = null

    var oauthTokenIsUser: Boolean
        get() { mOauthTokenIsUser = mOauthTokenIsUser ?: loginStore.getLoginData("oauthTokenIsUser", false); return mOauthTokenIsUser ?: false }
        set(value) { loginStore.putLoginData("oauthTokenIsUser", value); mOauthTokenIsUser = value }
    private var mOauthTokenIsUser: Boolean? = null

    var studentId: Int
        get() { mStudentId = mStudentId ?: profile?.getStudentData("studentId", 0); return mStudentId ?: 0 }
        set(value) { profile["studentId"] = value; mStudentId = value }
    private var mStudentId: Int? = null

    var termNames: Map<String, String> = mapOf()
        get() { mTermNames = mTermNames ?: profile?.getStudentData("termNames", null)?.let { app.gson.fromJson(it, field.toMutableMap()::class.java) }; return mTermNames ?: mapOf() }
        set(value) { profile["termNames"] = app.gson.toJson(value); mTermNames = value }
    private var mTermNames: Map<String, String>? = null
}
