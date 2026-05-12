/*
 * Copyright (c) Kuba Szczodrzyński 2022-2-21.
 */

package eu.mikus.edziennik.sync

import android.app.IntentService
import android.content.Intent
import android.widget.Toast
import eu.mikus.edziennik.utils.Utils

class UpdateDownloaderService : IntentService(UpdateDownloaderService::class.java.simpleName) {

    override fun onHandleIntent(intent: Intent?) {
        try {
            Utils.openGooglePlay(this, application.packageName)
        }
        catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Nie znaleziono Google Play. Pobierz aktualizację ręcznie.", Toast.LENGTH_SHORT).show()
        }
    }
}
