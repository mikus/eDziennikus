/*
 * Copyright (c) Kuba Szczodrzyński 2019-12-14.
 */

package eu.mikus.edziennik.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.Base64
import java.security.MessageDigest

/**
 * Reads the APK's own signing certificate so [eu.mikus.edziennik.utils.managers.BuildManager]
 * can verify the build identity (the keystore-recognition check used by
 * `isSigned`).
 *
 * Originally `Signing` under `data/api/szkolny/interceptor/` — siblings
 * (`SignatureInterceptor` and the JNI `szkolny-signing` native library)
 * signed outbound requests to szkolny.eu's API. Those were removed with
 * SzkolnyApi; this cert-reader survives standalone because BuildManager
 * still uses it.
 */
object AppCertificateReader {

    var appCertificate = ""

    fun getCert(context: Context) {
        with(context) {
            try {
                val packageInfo: PackageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                for (signature in packageInfo.signatures ?: arrayOf()) {
                    val signatureBytes = signature.toByteArray()
                    val md = MessageDigest.getInstance("SHA")
                    md.update(signatureBytes)
                    appCertificate = Base64.encodeToString(md.digest(), Base64.NO_WRAP)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
