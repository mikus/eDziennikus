/*
 * Copyright (c) Kuba Szczodrzyński 2022-2-21.
 */

package eu.mikus.edziennik.network

import android.content.Context
import okhttp3.OkHttpClient

object SSLProviderInstaller {

    fun install(applicationContext: Context, rebuildCallback: () -> Unit) {

    }

    fun enableSupportedTls(builder: OkHttpClient.Builder, enableCleartext: Boolean = true) {

    }
}
