/*
 * Copyright (c) Mikolaj Olszewski 2026-11-11.
 */
package eu.mikus.edziennik.data.api.edziennik.librus.login

import com.google.gson.JsonObject
import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.api.ERROR_LOGIN_LIBRUS_MESSAGES_NO_SESSION_ID
import eu.mikus.edziennik.data.api.edziennik.librus.DataLibrus
import eu.mikus.edziennik.data.api.events.UserActionRequiredEvent
import eu.mikus.edziennik.data.api.interfaces.EdziennikCallback
import eu.mikus.edziennik.data.api.models.ApiError
import eu.mikus.edziennik.data.db.entity.LoginStore
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.enums.LoginMethod
import eu.mikus.edziennik.data.db.enums.LoginMode
import eu.mikus.edziennik.data.db.enums.LoginType
import okhttp3.Dispatcher
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

/**
 * Pins that the Messages login finishes only when it has a session. Two replies end the login:
 * a `Location` naming `AutoLogon`, and a body holding `<status>ok</status>`. Both read the session id
 * from the cookie jar. Without one, the login raises `ERROR_LOGIN_LIBRUS_MESSAGES_NO_SESSION_ID`, and
 * it used to call `onSuccess()` straight after anyway: `LibrusLogin` then marked the failed login done
 * and went on to the data phase, while `Librus`'s arm for that code started a second `LibrusLogin`,
 * which reached the data phase too.
 *
 * Every request this login makes goes through `App.httpLazy`, which the test swaps for a client that
 * answers from an interceptor. Its dispatcher runs each call on the calling thread, and mhttp runs the
 * response handler on that thread too (its default `BACKGROUND` mode), so the whole login has finished
 * when the constructor returns. A reply "with a session" also puts a `DZIENNIKSID` into the app's cookie
 * jar for `wiadomosci.librus.pl`, as the real redirect chain does.
 *
 * The callback records errors directly, without the `Librus` wrapper, so that only the login's own
 * behaviour is observed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibrusLoginMessagesSessionTest {

    private val app: App
        get() = RuntimeEnvironment.getApplication() as App

    private lateinit var savedHttpLazy: OkHttpClient

    @Before
    fun saveClient() {
        savedHttpLazy = app.httpLazy
    }

    @After
    fun restoreClient() {
        app.httpLazy = savedHttpLazy
    }

    @Test
    fun `a reply without a session cookie fails the Messages login and does not finish it`() {
        assertEquals(
            listOf(
                "AutoLogon: errors=[LoginLibrusMessages/$ERROR_LOGIN_LIBRUS_MESSAGES_NO_SESSION_ID] finished=0 session=null",
                "status ok: errors=[LoginLibrusMessages/$ERROR_LOGIN_LIBRUS_MESSAGES_NO_SESSION_ID] finished=0 session=null",
            ),
            listOf(
                "AutoLogon: " + login(Reply.AUTO_LOGON, withSession = false),
                "status ok: " + login(Reply.STATUS_OK, withSession = false),
            ),
        )
    }

    @Test
    fun `a reply with a session cookie finishes the Messages login`() {
        assertEquals(
            listOf(
                "AutoLogon: errors=[] finished=1 session=$SESSION",
                "status ok: errors=[] finished=1 session=$SESSION",
            ),
            listOf(
                "AutoLogon: " + login(Reply.AUTO_LOGON, withSession = true),
                "status ok: " + login(Reply.STATUS_OK, withSession = true),
            ),
        )
    }

    private enum class Reply { AUTO_LOGON, STATUS_OK }

    /** Runs one Messages login against [reply] and reports what reached the callback. */
    private fun login(reply: Reply, withSession: Boolean): String {
        val errors = mutableListOf<String>()
        var finished = 0
        app.httpLazy = OkHttpClient.Builder()
            .dispatcher(Dispatcher(InlineExecutor()))
            .addInterceptor { chain ->
                if (withSession) app.cookieJar.set("wiadomosci.librus.pl", "DZIENNIKSID", SESSION)
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .apply { if (reply == Reply.AUTO_LOGON) header("Location", "https://wiadomosci.librus.pl/AutoLogon") }
                    .body(ResponseBody.create(MediaType.parse("text/xml"), if (reply == Reply.STATUS_OK) "<status>ok</status>" else ""))
                    .build()
            }
            .build()
        val data = DataLibrus(app, profile(), LoginStore(L, LoginType.LIBRUS, LoginMode.LIBRUS_EMAIL, JsonObject())).apply {
            callback = object : EdziennikCallback {
                override fun onCompleted() {}
                override fun onRequiresUserAction(event: UserActionRequiredEvent) {}
                override fun onProgress(step: Float) {}
                override fun onStartProgress(stringRes: Int) {}
                override fun onError(apiError: ApiError) {
                    errors += "${apiError.tag}/${apiError.errorCode}"
                }
            }
            loginMethods += LoginMethod.LIBRUS_SYNERGIA
        }

        LibrusLoginMessages(data) { finished++ }

        return "errors=$errors finished=$finished session=${data.messagesSessionId}"
    }

    private fun profile() = Profile(
        id = P,
        loginStoreId = L,
        loginStoreType = LoginType.LIBRUS,
        name = "Messages",
        studentData = JsonObject(),
    )

    /** Runs every call on the thread that enqueued it. */
    private class InlineExecutor : AbstractExecutorService() {
        override fun execute(command: Runnable) = command.run()
        override fun shutdown() {}
        override fun shutdownNow(): List<Runnable> = emptyList()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }

    private companion object {
        const val P = 9063
        const val L = 9163
        const val SESSION = "session-9063"
    }
}
