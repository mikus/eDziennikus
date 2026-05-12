/*
 * Copyright (c) Kuba Szczodrzyński 2020-1-11.
 */

package eu.mikus.edziennik.data.firebase

import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.api.edziennik.EdziennikTask
import eu.mikus.edziennik.data.api.task.IApiTask
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.enums.FeatureType
import eu.mikus.edziennik.data.db.enums.LoginType
import eu.mikus.edziennik.ext.getLong
import eu.mikus.edziennik.ext.getString
import eu.mikus.edziennik.ext.getStudentData

class SzkolnyMobidziennikFirebase(val app: App, val profiles: List<Profile>, val message: FirebaseService.Message) {
    /*{
      "id": "123456",
      "body": "Janósz Kowalski (Nauczyciele) - Temat wiadomości",
      "icon": "push",
      "type": "wiadOdebrana",
      "color": "#025b8e",
      "login": "1234@2019@szkola",
      "notId": "1234567",
      "sound": "default",
      "title": "Nowa wiadomość - mobiDziennik",
      "global_id": "123456",
      "vibrate": "true",
      "sync_url": "https://szkola.mobidziennik.pl/api2/logowanie"
    }*/
    /*{
      "body": "Kowalski Janósz - zapowiedziany sprawdzian na jutro:\njęzyk niemiecki (kartkówka - nieregularne 2)",
      "icon": "push",
      "type": "sprawdzianyJutro",
      "color": "#025b8e",
      "login": "1234@2019@szkola",
      "notId": "1234567",
      "sound": "default",
      "title": "Sprawdziany jutro - mobiDziennik",
      "global_id": "123456",
      "vibrate": "true",
      "sync_url": "https://szkola.mobidziennik.pl/api2/logowanie"
    }*/
    init { run {
        val type = message.data.getString("type") ?: return@run
        if (type == "sprawdzianyJutro" || type == "zadaniaJutro" || type == "autoryzacjaUrzadzenia")
            return@run
        val globalId = message.data.getLong("global_id")

        /* assets/www/js/push.js */
        val featureType = when (type) {
            "wiadOdebrana" -> FeatureType.MESSAGES_INBOX
            "oceny", "ocenyKoncowe", "zachowanie" -> FeatureType.GRADES
            "uwagi" -> FeatureType.BEHAVIOUR
            "nieobecnoscPierwszaLekcja", "nieobecnosciDzisiaj" -> FeatureType.ATTENDANCE
            else -> return@run
        }

        val tasks = profiles.filter {
            it.loginStoreType == LoginType.MOBIDZIENNIK &&
                    it.getStudentData("globalId", 0L) == globalId
        }.map {
            EdziennikTask.syncProfile(it.id, setOf(featureType))
        }
        IApiTask.enqueueAll(app, tasks)
    }}
}
