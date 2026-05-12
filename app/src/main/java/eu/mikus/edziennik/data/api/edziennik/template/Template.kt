/*
 * Copyright (c) Kuba Szczodrzyński 2019-10-5.
 */

package eu.mikus.edziennik.data.api.edziennik.template

import com.google.gson.JsonObject
import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.api.CODE_INTERNAL_LIBRUS_ACCOUNT_410
import eu.mikus.edziennik.data.api.edziennik.template.data.TemplateData
import eu.mikus.edziennik.data.api.edziennik.template.firstlogin.TemplateFirstLogin
import eu.mikus.edziennik.data.api.edziennik.template.login.TemplateLogin
import eu.mikus.edziennik.data.api.events.UserActionRequiredEvent
import eu.mikus.edziennik.data.api.interfaces.EdziennikCallback
import eu.mikus.edziennik.data.api.interfaces.EdziennikInterface
import eu.mikus.edziennik.data.api.models.ApiError
import eu.mikus.edziennik.data.api.prepare
import eu.mikus.edziennik.data.db.entity.LoginStore
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.entity.Teacher
import eu.mikus.edziennik.data.db.enums.FeatureType
import eu.mikus.edziennik.data.db.full.AnnouncementFull
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.data.db.full.MessageFull
import eu.mikus.edziennik.utils.Utils.d

class Template(val app: App, val profile: Profile?, val loginStore: LoginStore, val callback: EdziennikCallback) : EdziennikInterface {
    companion object {
        private const val TAG = "Template"
    }

    val internalErrorList = mutableListOf<Int>()
    val data: DataTemplate

    init {
        data = DataTemplate(app, profile, loginStore).apply {
            callback = wrapCallback(this@Template.callback)
            satisfyLoginMethods()
        }
    }

    private fun completed() {
        data.saveData()
        callback.onCompleted()
    }

    /*    _______ _                     _                  _ _   _
         |__   __| |              /\   | |                (_) | | |
            | |  | |__   ___     /  \  | | __ _  ___  _ __ _| |_| |__  _ __ ___
            | |  | '_ \ / _ \   / /\ \ | |/ _` |/ _ \| '__| | __| '_ \| '_ ` _ \
            | |  | | | |  __/  / ____ \| | (_| | (_) | |  | | |_| | | | | | | | |
            |_|  |_| |_|\___| /_/    \_\_|\__, |\___/|_|  |_|\__|_| |_|_| |_| |_|
                                           __/ |
                                          |__*/
    override fun sync(featureTypes: Set<FeatureType>?, onlyEndpoints: Set<Int>?, arguments: JsonObject?) {
        data.arguments = arguments
        data.prepare(TemplateFeatures, featureTypes, onlyEndpoints)
        d(TAG, "LoginMethod IDs: ${data.targetLoginMethods}")
        d(TAG, "Endpoint IDs: ${data.targetEndpoints}")
        TemplateLogin(data) {
            TemplateData(data) {
                completed()
            }
        }
    }

    override fun getMessage(message: MessageFull) {

    }

    override fun sendMessage(recipients: Set<Teacher>, subject: String, text: String) {

    }

    override fun markAllAnnouncementsAsRead() {

    }

    override fun getAnnouncement(announcement: AnnouncementFull) {

    }

    override fun getAttachment(owner: Any, attachmentId: Long, attachmentName: String) {

    }

    override fun getRecipientList() {

    }

    override fun getEvent(eventFull: EventFull) {

    }

    override fun firstLogin() {
        TemplateFirstLogin(data) {
            completed()
        }
    }

    override fun cancel() {
        d(TAG, "Cancelled")
        data.cancel()
    }

    private fun wrapCallback(callback: EdziennikCallback): EdziennikCallback {
        return object : EdziennikCallback {
            override fun onCompleted() {
                callback.onCompleted()
            }

            override fun onRequiresUserAction(event: UserActionRequiredEvent) {
                callback.onRequiresUserAction(event)
            }

            override fun onProgress(step: Float) {
                callback.onProgress(step)
            }

            override fun onStartProgress(stringRes: Int) {
                callback.onStartProgress(stringRes)
            }

            override fun onError(apiError: ApiError) {
                when (apiError.errorCode) {
                    in internalErrorList -> {
                        // finish immediately if the same error occurs twice during the same sync
                        callback.onError(apiError)
                    }
                    CODE_INTERNAL_LIBRUS_ACCOUNT_410 -> {
                        internalErrorList.add(apiError.errorCode)
                        loginStore.removeLoginData("refreshToken") // force a clean login
                        //loginLibrus()
                    }
                    else -> callback.onError(apiError)
                }
            }
        }
    }
}
