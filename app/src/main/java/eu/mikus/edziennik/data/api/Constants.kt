/*
 * Copyright (c) Kuba Szczodrzyński 2019-9-19.
 */

package eu.mikus.edziennik.data.api

import android.os.Build
import eu.mikus.edziennik.BuildConfig

const val GET = 0
const val POST = 1

val SYSTEM_USER_AGENT = System.getProperty("http.agent") ?: "Dalvik/2.1.0 Android"

val SERVER_USER_AGENT = "Szkolny.eu/${BuildConfig.VERSION_NAME} $SYSTEM_USER_AGENT"

const val FAKE_LIBRUS_API = "https://librus.szkolny.eu/api"
const val FAKE_LIBRUS_PORTAL = "https://librus.szkolny.eu"
const val FAKE_LIBRUS_AUTHORIZE = "https://librus.szkolny.eu/authorize.php"
const val FAKE_LIBRUS_LOGIN = "https://librus.szkolny.eu/login_action.php"
const val FAKE_LIBRUS_TOKEN = "https://librus.szkolny.eu/access_token.php"
const val FAKE_LIBRUS_ACCOUNT = "/synergia_accounts_fresh.php?login="
const val FAKE_LIBRUS_ACCOUNTS = "/synergia_accounts.php"

val LIBRUS_USER_AGENT = "${SYSTEM_USER_AGENT}LibrusMobileApp"
const val SYNERGIA_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Gecko/20100101 Firefox/62.0"
const val LIBRUS_CLIENT_ID = "VaItV6oRutdo8fnjJwysnTjVlvaswf52ZqmXsJGP"
const val LIBRUS_REDIRECT_URL = "app://librus"
const val LIBRUS_AUTHORIZE_URL = "https://portal.librus.pl/konto-librus/redirect/dru"
const val LIBRUS_LOGIN_URL = "https://portal.librus.pl/konto-librus/login/action"
const val LIBRUS_TOKEN_URL = "https://portal.librus.pl/oauth2/access_token"
const val LIBRUS_HEADER = "pl.librus.synergiaDru2"

const val LIBRUS_ACCOUNT_URL = "/v3/SynergiaAccounts/fresh/" // + login
const val LIBRUS_ACCOUNTS_URL = "/v3/SynergiaAccounts"

/** https://api.librus.pl/2.0 */
const val LIBRUS_API_URL = "https://api.librus.pl/2.0"
/** https://portal.librus.pl/api */
const val LIBRUS_PORTAL_URL = "https://portal.librus.pl/api"
/** https://api.librus.pl/OAuth/Token */
const val LIBRUS_API_TOKEN_URL = "https://api.librus.pl/OAuth/Token"
const val LIBRUS_API_AUTHORIZATION = "Mjg6ODRmZGQzYTg3YjAzZDNlYTZmZmU3NzdiNThiMzMyYjE="

const val LIBRUS_SYNERGIA_URL = "https://synergia.librus.pl"
/** https://synergia.librus.pl/loguj/token/TOKEN/przenies */
const val LIBRUS_SYNERGIA_TOKEN_LOGIN_URL = "https://synergia.librus.pl/loguj/token/TOKEN/przenies"

const val LIBRUS_MESSAGES_URL = "https://wiadomosci.librus.pl/module"
const val LIBRUS_SANDBOX_URL = "https://sandbox.librus.pl/index.php?action="

const val LIBRUS_SYNERGIA_HOMEWORK_ATTACHMENT_URL = "https://synergia.librus.pl/homework/downloadFile"
const val LIBRUS_SYNERGIA_MESSAGES_ATTACHMENT_URL = "https://synergia.librus.pl/wiadomosci/pobierz_zalacznik"
