/*
 * Copyright (c) Kuba Szczodrzyński 2021-10-17.
 */

package eu.mikus.edziennik.ext

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import eu.mikus.edziennik.sync.ConnectivityState
import java.util.*

fun Context.setLanguage(language: String) {
    val locale = Locale(language.lowercase())
    val configuration = resources.configuration
    Locale.setDefault(locale)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
        configuration.setLocale(locale)
    }
    configuration.locale = locale
    resources.updateConfiguration(configuration, resources.displayMetrics)
}

/**
 * The device's current network, as the sync package's [ConnectivityState].
 *
 * Returns `ConnectivityState(connected = false, …)` when there is no active network — **not** null.
 * Null is reserved for "could not be read at all": no ConnectivityManager, or a SecurityException,
 * which WorkManager's own tracker also catches and which does happen on some OEM builds. The
 * distinction is load-bearing: `WorkerUtils.networkConstraintMet` answers *true* for null, so
 * conflating the two would make offline take the unknown path and suppress nothing.
 *
 * Uses getActiveNetwork/getNetworkCapabilities rather than the deprecated activeNetworkInfo; at
 * minSdk 23 both are available with no version branch.
 */
internal fun Context.readConnectivityState(): ConnectivityState? {
    return try {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return null
        val caps = cm.activeNetwork?.let(cm::getNetworkCapabilities)
        if (caps == null) ConnectivityState(connected = false, validated = false, unmetered = false)
        else ConnectivityState(
            connected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
        )
    } catch (e: SecurityException) {
        null
    }
}
