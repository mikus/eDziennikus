/*
 * Copyright (c) Kuba Szczodrzyński 2019-9-21.
 */

package eu.mikus.edziennik.data.api.edziennik.librus

import com.google.gson.JsonObject
import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.api.*
import eu.mikus.edziennik.data.api.edziennik.librus.data.LibrusData
import eu.mikus.edziennik.data.api.edziennik.librus.data.api.LibrusApiAnnouncementMarkAsRead
import eu.mikus.edziennik.data.api.edziennik.librus.data.messages.LibrusMessagesGetMessage
import eu.mikus.edziennik.data.api.edziennik.librus.data.messages.LibrusMessagesGetRecipientList
import eu.mikus.edziennik.data.api.edziennik.librus.data.messages.LibrusMessagesSendMessage
import eu.mikus.edziennik.data.api.edziennik.librus.data.synergia.*
import eu.mikus.edziennik.data.api.edziennik.librus.firstlogin.LibrusFirstLogin
import eu.mikus.edziennik.data.api.edziennik.librus.login.LibrusLogin
import eu.mikus.edziennik.data.api.events.UserActionRequiredEvent
import eu.mikus.edziennik.data.api.interfaces.EdziennikCallback
import eu.mikus.edziennik.data.api.interfaces.EdziennikInterface
import eu.mikus.edziennik.data.api.models.ApiError
import eu.mikus.edziennik.data.db.entity.LoginStore
import eu.mikus.edziennik.data.db.entity.Message
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.entity.Teacher
import eu.mikus.edziennik.data.db.enums.FeatureType
import eu.mikus.edziennik.data.db.enums.LoginMethod
import eu.mikus.edziennik.data.db.full.AnnouncementFull
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.data.db.full.MessageFull
import eu.mikus.edziennik.ext.DAY
import eu.mikus.edziennik.ext.HOUR
import eu.mikus.edziennik.ext.WEEK
import eu.mikus.edziennik.utils.Utils.d

class Librus(val app: App, val profile: Profile?, val loginStore: LoginStore, val callback: EdziennikCallback) : EdziennikInterface {
    companion object {
        private const val TAG = "Librus"
    }

    val internalErrorList = mutableListOf<Int>()
    val data: DataLibrus
    private var afterLogin: (() -> Unit)? = null

    init {
        data = DataLibrus(app, profile, loginStore).apply {
            callback = wrapCallback(this@Librus.callback)
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
        data.prepare(LibrusFeatures, featureTypes, onlyEndpoints)
        login()
    }

    private fun login(loginMethod: LoginMethod? = null, afterLogin: (() -> Unit)? = null) {
        d(TAG, "Trying to login with ${data.targetLoginMethods}")
        if (internalErrorList.isNotEmpty()) {
            d(TAG, "  - Internal errors:")
            internalErrorList.forEach { d(TAG, "      - code $it") }
        }
        loginMethod?.let { data.prepareFor(it) }
        afterLogin?.let { this.afterLogin = it }
        LibrusLogin(data) {
            data()
        }
    }

    private fun data() {
        d(TAG, "Endpoint IDs: ${data.targetEndpoints}")
        if (internalErrorList.isNotEmpty()) {
            d(TAG, "  - Internal errors:")
            internalErrorList.forEach { d(TAG, "      - code $it") }
        }
        afterLogin?.invoke() ?: LibrusData(data) {
            completed()
        }
    }

    override fun getMessage(message: MessageFull) {
        login(LoginMethod.LIBRUS_MESSAGES) {
            if (data.messagesLoginSuccessful) LibrusMessagesGetMessage(data, message) { completed() }
            else LibrusSynergiaGetMessage(data, message) { completed() }
        }
    }

    override fun sendMessage(recipients: Set<Teacher>, subject: String, text: String) {
        login(LoginMethod.LIBRUS_MESSAGES) {
            LibrusMessagesSendMessage(data, recipients, subject, text) {
                completed()
            }
        }
    }

    override fun markAllAnnouncementsAsRead() {
        login(LoginMethod.LIBRUS_SYNERGIA) {
            LibrusSynergiaMarkAllAnnouncementsAsRead(data) {
                completed()
            }
        }
    }

    override fun getAnnouncement(announcement: AnnouncementFull) {
        login(LoginMethod.LIBRUS_API) {
            LibrusApiAnnouncementMarkAsRead(data, announcement) {
                completed()
            }
        }
    }

    override fun getAttachment(owner: Any, attachmentId: Long, attachmentName: String) {
        when (owner) {
            is Message -> {
                login(LoginMethod.LIBRUS_SYNERGIA) {
                    // Synergia only. This used to read `if (data.messagesLoginSuccessful)
                    // LibrusMessagesGetAttachment(...)` followed - with no `else` - by the Synergia
                    // call, so BOTH downloaders ran on every tap: messagesLoginSuccessful defaults
                    // to true (DataLibrus:282) and is only ever cleared by LibrusLoginMessages,
                    // which a SYNERGIA login never runs. Two Librus round trips per attachment, and
                    // the loser's late completed() posted ApiTaskFinishedEvent carrying a
                    // taskProfileId that clearTask() had already reset to -1 - which MainActivity
                    // ignores, stranding the toolbar subtitle at "Syncing…" until the next sync.
                    //
                    // Adding the missing `else` would be wrong: this block logs in with
                    // LIBRUS_SYNERGIA, whereas LibrusMessagesGetAttachment calls the Messages
                    // endpoint (LibrusMessages.messagesGet) that only LoginMethod.LIBRUS_MESSAGES
                    // authenticates - cf. getMessage() above, which pairs them correctly. The
                    // messages branch was never reachable-and-correct here; the Synergia call is the
                    // one that has always actually run and worked, so it is the one that stays.
                    LibrusSynergiaGetAttachment(data, owner, attachmentId, attachmentName) { completed() }
                }
            }
            is EventFull -> {
                login(LoginMethod.LIBRUS_SYNERGIA) {
                    LibrusSynergiaHomeworkGetAttachment(data, owner, attachmentId, attachmentName) {
                        completed()
                    }
                }
            }
            else -> completed()
        }
    }

    override fun getRecipientList() {
        login(LoginMethod.LIBRUS_MESSAGES) {
            LibrusMessagesGetRecipientList(data) {
                completed()
            }
        }
    }

    override fun getEvent(eventFull: EventFull) {
        login(LoginMethod.LIBRUS_SYNERGIA) {
            LibrusSynergiaGetHomework(data, eventFull) {
                completed()
            }
        }
    }

    override fun firstLogin() { LibrusFirstLogin(data) { completed() } }
    override fun cancel() {
        d(TAG, "Cancelled")
        data.cancel()
    }

    private fun wrapCallback(callback: EdziennikCallback): EdziennikCallback {
        return object : EdziennikCallback {
            override fun onCompleted() { callback.onCompleted() }
            override fun onRequiresUserAction(event: UserActionRequiredEvent) { callback.onRequiresUserAction(event) }
            override fun onProgress(step: Float) { callback.onProgress(step) }
            override fun onStartProgress(stringRes: Int) { callback.onStartProgress(stringRes) }
            override fun onError(apiError: ApiError) {
                if (apiError.errorCode in internalErrorList) {
                    // finish immediately if the same error occurs twice during the same sync
                    callback.onError(apiError)
                    return
                }
                internalErrorList.add(apiError.errorCode)
                when (apiError.errorCode) {
                    ERROR_LIBRUS_PORTAL_ACCESS_DENIED -> {
                        data.loginMethods.remove(LoginMethod.LIBRUS_PORTAL)
                        data.prepareFor(LoginMethod.LIBRUS_PORTAL)
                        data.portalTokenExpiryTime = 0
                        login()
                    }
                    ERROR_LIBRUS_API_ACCESS_DENIED,
                    ERROR_LIBRUS_API_TOKEN_EXPIRED -> {
                        data.loginMethods.remove(LoginMethod.LIBRUS_API)
                        data.prepareFor(LoginMethod.LIBRUS_API)
                        data.apiTokenExpiryTime = 0
                        login()
                    }
                    ERROR_LIBRUS_SYNERGIA_ACCESS_DENIED -> {
                        data.loginMethods.remove(LoginMethod.LIBRUS_SYNERGIA)
                        data.prepareFor(LoginMethod.LIBRUS_SYNERGIA)
                        data.synergiaSessionIdExpiryTime = 0
                        login()
                    }
                    ERROR_LIBRUS_MESSAGES_ACCESS_DENIED -> {
                        data.loginMethods.remove(LoginMethod.LIBRUS_MESSAGES)
                        data.prepareFor(LoginMethod.LIBRUS_MESSAGES)
                        data.messagesSessionIdExpiryTime = 0
                        login()
                    }
                    ERROR_LOGIN_LIBRUS_PORTAL_NO_CODE,
                    ERROR_LOGIN_LIBRUS_PORTAL_CSRF_MISSING,
                    ERROR_LOGIN_LIBRUS_PORTAL_CSRF_EXPIRED,
                    ERROR_LOGIN_LIBRUS_PORTAL_CODE_REVOKED,
                    ERROR_LOGIN_LIBRUS_PORTAL_CODE_EXPIRED -> {
                        login()
                    }
                    ERROR_LOGIN_LIBRUS_PORTAL_NO_REFRESH,
                    ERROR_LOGIN_LIBRUS_PORTAL_REFRESH_REVOKED,
                    ERROR_LOGIN_LIBRUS_PORTAL_REFRESH_INVALID -> {
                        data.portalRefreshToken = null
                        login()
                    }
                    ERROR_LOGIN_LIBRUS_SYNERGIA_TOKEN_INVALID,
                    ERROR_LOGIN_LIBRUS_SYNERGIA_NO_TOKEN,
                    ERROR_LOGIN_LIBRUS_SYNERGIA_NO_SESSION_ID -> {
                        login()
                    }
                    ERROR_LOGIN_LIBRUS_MESSAGES_NO_SESSION_ID -> {
                        login()
                    }
                    ERROR_LIBRUS_API_TIMETABLE_NOT_PUBLIC -> {
                        data.timetableNotPublic = true
                        data()
                    }
                    ERROR_LIBRUS_API_LUCKY_NUMBER_NOT_ACTIVE,
                    ERROR_LIBRUS_API_NOTES_NOT_ACTIVE -> {
                        data()
                    }
                    ERROR_LIBRUS_API_TEACHER_FREE_DAYS_NOT_PUBLIC -> {
                        d(TAG, "Student not have access to Teacher Free Days resource")
                        data.setSyncNext(ENDPOINT_LIBRUS_API_TEACHER_FREE_DAYS, 1 * WEEK, FeatureType.AGENDA)
                        data()
                    }
                    /* A parser threw: the response arrived and was well formed at the transport
                     * level, and one FIELD in it could not be read. That is one endpoint's problem,
                     * not the profile's - but LibrusApi.apiGet wraps the whole parser, so the throw
                     * also skipped the endpoint's own setSyncNext() and, worse, its
                     * onSuccess(endpointId), which is LibrusData.nextEndpoint's continuation and the
                     * only thing that advances the chain from inside it. With the error critical,
                     * ApiService cancels the task and every endpoint with a higher id never runs -
                     * on every sync, forever, because targetEndpoints is a sorted map walked by
                     * firstKey().
                     *
                     * So both halves are decided here, together: non-critical (ApiService then
                     * leaves the task alone) AND data(), which re-enters LibrusData. They cannot
                     * disagree, and marking without resuming would hang the sync - ApiService's
                     * non-critical arm counts the error and returns, and runTask() is the only
                     * caller of checkIfTaskFrozen(). nextEndpoint() removes the id from
                     * targetEndpoints BEFORE running the endpoint, so the re-entry resumes at the
                     * NEXT one and cannot loop.
                     *
                     * setSyncNext is deliberately NOT called for the failed endpoint: it did not
                     * sync, so it stays due and is retried. What this buys is that every OTHER
                     * endpoint reaches its own setSyncNext and the chain reaches completed() ->
                     * saveData().
                     *
                     * afterLogin != null means a single-operation task (getMessage, sendMessage,
                     * getAttachment, ...). There is no rest-of-the-profile to go on with, and data()
                     * would re-invoke the OPERATION: sendMessage would send the message a second
                     * time. Those stay critical, exactly as before.
                     * firstLogin() leaves afterLogin null too, so a throw in its own apiGet takes this arm and
                     * runs data() on an empty endpoint map: reported, harmless, no profile created either way.
                     *
                     * The guard above this when() keeps it bounded to once per code per task.
                     * Deliberate: ApiService.errorList is never drained and each ApiError retains
                     * the entire response body. */
                    EXCEPTION_LIBRUS_API_REQUEST,
                    EXCEPTION_LIBRUS_SYNERGIA_REQUEST,
                    EXCEPTION_LIBRUS_MESSAGES_REQUEST -> if (afterLogin != null) {
                        callback.onError(apiError)
                    } else {
                        d(TAG, "A parser failed on one endpoint; skipping it and going on")
                        callback.onError(apiError.setCritical(false))
                        data()
                    }
                    else -> callback.onError(apiError)
                }
            }
        }
    }
}
