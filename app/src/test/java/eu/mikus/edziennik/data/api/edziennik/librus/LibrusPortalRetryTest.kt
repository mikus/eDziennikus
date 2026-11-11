/*
 * Copyright (c) Mikolaj Olszewski 2026-11-11.
 */
package eu.mikus.edziennik.data.api.edziennik.librus

import com.google.gson.JsonObject
import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.api.ERROR_LOGIN_DATA_MISSING
import eu.mikus.edziennik.data.api.ERROR_LOGIN_LIBRUS_PORTAL_CODE_EXPIRED
import eu.mikus.edziennik.data.api.ERROR_LOGIN_LIBRUS_PORTAL_CODE_REVOKED
import eu.mikus.edziennik.data.api.ERROR_LOGIN_LIBRUS_PORTAL_CSRF_EXPIRED
import eu.mikus.edziennik.data.api.ERROR_LOGIN_LIBRUS_PORTAL_CSRF_MISSING
import eu.mikus.edziennik.data.api.ERROR_LOGIN_LIBRUS_PORTAL_NO_CODE
import eu.mikus.edziennik.data.api.ERROR_LOGIN_LIBRUS_PORTAL_NO_REFRESH
import eu.mikus.edziennik.data.api.ERROR_LOGIN_LIBRUS_PORTAL_REFRESH_INVALID
import eu.mikus.edziennik.data.api.ERROR_LOGIN_LIBRUS_PORTAL_REFRESH_REVOKED
import eu.mikus.edziennik.data.api.events.UserActionRequiredEvent
import eu.mikus.edziennik.data.api.interfaces.EdziennikCallback
import eu.mikus.edziennik.data.api.models.ApiError
import eu.mikus.edziennik.data.db.entity.LoginStore
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.enums.LoginMethod
import eu.mikus.edziennik.data.db.enums.LoginMode
import eu.mikus.edziennik.data.db.enums.LoginType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins what `Librus` does when the portal login fails with an error it answers by logging in again:
 * a stale refresh token, or a lost authorisation code. The retry has to run the portal login again,
 * followed by the logins that were still queued behind it.
 *
 * `LibrusLogin` takes each method off `targetLoginMethods` as it starts it, so by the time the portal
 * reports the error it is no longer queued. A retry that only calls `login()` starts at `LIBRUS_API`,
 * which needs the portal, and the sync fails with `ERROR_LOGIN_METHOD_NOT_SATISFIED` as a critical
 * error. That was measured on a device on 2026-11-11, with a portal-revoked refresh token. A retry
 * that re-plans with `prepareFor(LIBRUS_PORTAL)` instead runs the portal and nothing after it, since
 * `prepareFor` clears the queue.
 *
 * No request is made. The login store has no email or password, so the retried portal login stops at
 * its first check with `ERROR_LOGIN_DATA_MISSING`, before building any request. That error and the
 * queue at that moment are what each case records. On the old code `LibrusLoginApi` stops at its own
 * first check, `ERROR_LOGIN_METHOD_NOT_SATISFIED`, which is the device failure.
 *
 * The profile exists only in memory: `Data`'s init reads content tables by its id, and finds none.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibrusPortalRetryTest {

    private val app: App
        get() = RuntimeEnvironment.getApplication() as App

    @Test
    fun `a refresh-token error retries the portal before the logins queued behind it`() {
        val codes = listOf(
            ERROR_LOGIN_LIBRUS_PORTAL_NO_REFRESH,
            ERROR_LOGIN_LIBRUS_PORTAL_REFRESH_REVOKED,
            ERROR_LOGIN_LIBRUS_PORTAL_REFRESH_INVALID,
        )

        assertEquals(
            codes.map { "$it: ${retried(refreshTokenCleared = true)}" },
            codes.map { "$it: ${runAfterPortalError(it)}" },
        )
    }

    @Test
    fun `an authorisation-code error retries the portal before the logins queued behind it`() {
        val codes = listOf(
            ERROR_LOGIN_LIBRUS_PORTAL_NO_CODE,
            ERROR_LOGIN_LIBRUS_PORTAL_CSRF_MISSING,
            ERROR_LOGIN_LIBRUS_PORTAL_CSRF_EXPIRED,
            ERROR_LOGIN_LIBRUS_PORTAL_CODE_REVOKED,
            ERROR_LOGIN_LIBRUS_PORTAL_CODE_EXPIRED,
        )

        assertEquals(
            codes.map { "$it: ${retried(refreshTokenCleared = false)}" },
            codes.map { "$it: ${runAfterPortalError(it)}" },
        )
    }

    /** What a retry that ran the portal again, with the rest still queued, reports. */
    private fun retried(refreshTokenCleared: Boolean) =
        "LoginLibrusPortal/$ERROR_LOGIN_DATA_MISSING queued=$QUEUED_AFTER_PORTAL " +
            "refreshTokenCleared=$refreshTokenCleared"

    /**
     * A fresh `Librus` in the state the portal leaves when it fails: taken off the queue, not logged
     * in, a refresh token stored. Reports the first error that reaches the task's callback, which
     * login step raised it, the queue at that moment, and whether the refresh token was cleared.
     */
    private fun runAfterPortalError(errorCode: Int): String {
        val reported = mutableListOf<String>()
        lateinit var librus: Librus
        val callback = object : EdziennikCallback {
            override fun onCompleted() {}
            override fun onRequiresUserAction(event: UserActionRequiredEvent) {}
            override fun onProgress(step: Float) {}
            override fun onStartProgress(stringRes: Int) {}
            override fun onError(apiError: ApiError) {
                reported += "${apiError.tag}/${apiError.errorCode} queued=${librus.data.targetLoginMethods} " +
                    "refreshTokenCleared=${librus.data.portalRefreshToken == null}"
            }
        }
        val store = LoginStore(
            L,
            LoginType.LIBRUS,
            LoginMode.LIBRUS_EMAIL,
            JsonObject().apply { addProperty("refreshToken", "stale") },
        )
        librus = Librus(app, profile(), store, callback)
        librus.data.targetLoginMethods = QUEUED_AFTER_PORTAL.toMutableList()

        librus.data.error(ApiError("LoginLibrusPortal", errorCode))

        return reported.firstOrNull() ?: "nothing reported"
    }

    private fun profile() = Profile(
        id = P,
        loginStoreId = L,
        loginStoreType = LoginType.LIBRUS,
        name = "Retry",
        studentData = JsonObject(),
    )

    private companion object {
        const val P = 9061
        const val L = 9161
        val QUEUED_AFTER_PORTAL = listOf(LoginMethod.LIBRUS_API, LoginMethod.LIBRUS_SYNERGIA, LoginMethod.LIBRUS_MESSAGES)
    }
}
