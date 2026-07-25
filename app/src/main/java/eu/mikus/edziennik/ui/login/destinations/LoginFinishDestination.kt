/*
 * Copyright (c) Mikolaj Olszewski 2026-7-25.
 */
package eu.mikus.edziennik.ui.login.destinations

import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.ext.Intent
import eu.mikus.edziennik.ui.base.enums.NavTarget
import eu.mikus.edziennik.ui.login.LoginFinishScreen
import eu.mikus.edziennik.ui.login.LoginViewModel

@Composable
fun LoginFinishDestination(vm: LoginViewModel, activity: AppCompatActivity) {
    val app = activity.application as App
    // Capture firstRun THEN mark loginFinished, once per entry (matches the deleted fragment).
    val firstRun = remember {
        val wasFirstRun = !app.config.loginFinished
        app.config.loginFinished = true
        wasFirstRun
    }
    LoginFinishScreen(firstRun = firstRun, onDone = { finishLogin(vm, app, activity, firstRun) })
}

private fun finishLogin(vm: LoginViewModel, app: App, activity: AppCompatActivity, firstRun: Boolean) {
    val firstProfileId = vm.firstProfileId
    if (firstProfileId == 0) { activity.finish(); return }
    app.profileLoad(firstProfileId) {
        if (firstRun) {
            activity.startActivity(Intent(activity, MainActivity::class.java, "profileId" to firstProfileId, "fragmentId" to NavTarget.HOME))
        } else {
            activity.setResult(Activity.RESULT_OK, Intent(null, "profileId" to firstProfileId, "fragmentId" to NavTarget.HOME))
        }
        activity.finish()
    }
}
