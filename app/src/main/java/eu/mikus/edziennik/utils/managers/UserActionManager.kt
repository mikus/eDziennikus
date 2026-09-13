/*
 * Copyright (c) Kuba Szczodrzyński 2020-2-15.
 */

package eu.mikus.edziennik.utils.managers

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.api.edziennik.EdziennikTask
import eu.mikus.edziennik.data.api.events.UserActionRequiredEvent
import eu.mikus.edziennik.data.api.models.PendingUserAction
import eu.mikus.edziennik.data.api.models.pendingFor
import eu.mikus.edziennik.ext.*
import eu.mikus.edziennik.ui.captcha.RecaptchaPromptDialog
import eu.mikus.edziennik.ui.login.recaptcha.RecaptchaActivity
import eu.mikus.edziennik.ui.login.recaptcha.RecaptchaResult
import eu.mikus.edziennik.utils.Utils.d

class UserActionManager(val app: App) {
    companion object {
        private const val TAG = "UserActionManager"
    }

    fun sendToUser(event: UserActionRequiredEvent) {
        if (EventBus.getDefault().hasSubscriberForEvent(UserActionRequiredEvent::class.java)) {
            EventBus.getDefault().post(event)
            return
        }

        // One slot, last writer wins: a later park displaces an earlier profile's, whose tap then
        // does nothing until that profile's next sync. `pendingFor` makes that a no-op rather than
        // the wrong profile's captcha, which is the trade. The displaced notification is left in the
        // tray and is inert - `notify` below uses a fresh id each time, so it is not replaced.
        //
        // Bail rather than assign null: an event this cannot represent must not clear a good slot,
        // and a notification that cannot act on a tap is worse than no notification. Unreachable
        // today - the one producer always supplies all three - but adding a second action type would
        // break the exhaustive `when` in `execute` without touching `toPending`, which would then
        // fail silently here.
        val pending = event.toPending() ?: run {
            d(TAG, "Cannot park a user action for profile ${event.profileId} (${event.type})")
            return
        }
        app.config.pendingUserAction = pending

        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val text = app.getString(event.errorText, event.profileId)
        val intent = Intent(
            app,
            MainActivity::class.java,
            "action" to "userActionRequired",
            // A selector, not data: it picks which parked action to run, and guessing it achieves
            // only what the app itself queued for that profile. Reusing the existing `profileId`
            // extra rather than inventing a second one keeps `handleIntent`'s profile switch
            // working, so the tap still opens on the profile the action belongs to.
            "profileId" to event.profileId,
        )
        val pendingIntent = PendingIntent.getActivity(
            app,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_ONE_SHOT or pendingIntentFlag(),
        )

        val notification =
            NotificationCompat.Builder(app, app.notificationChannelsManager.userAttention.key)
                .setContentTitle(app.getString(R.string.notification_user_action_required_title))
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_error_outline)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setColor(0xff2196f3.toInt())
                .setLights(0xff2196f3.toInt(), 2000, 2000)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    class UserActionCallback(
        val onSuccess: ((data: Bundle) -> Unit)? = null,
        val onFailure: (() -> Unit)? = null,
        val onCancel: (() -> Unit)? = null,
    )

    fun execute(
        activity: AppCompatActivity,
        event: UserActionRequiredEvent,
        callback: UserActionCallback,
    ) {
        d(TAG, "Running user action (${event.type}) with params: ${event.params}")
        val isSuccessful = when (event.type) {
            UserActionRequiredEvent.Type.RECAPTCHA -> executeRecaptcha(activity, event, callback)
        }
        if (!isSuccessful)
            callback.onFailure?.invoke()
    }

    /** The captcha vocabulary belongs here, not to the stored record. */
    private fun UserActionRequiredEvent.toPending(): PendingUserAction? = PendingUserAction(
        profileId = profileId,
        type = type,
        siteKey = params.getString("siteKey") ?: return null,
        referer = params.getString("referer") ?: return null,
        userAgent = params.getString("userAgent") ?: return null,
    )

    private fun PendingUserAction.toEvent() = UserActionRequiredEvent(
        profileId = profileId,
        type = type,
        params = Bundle(
            "siteKey" to siteKey,
            "referer" to referer,
            "userAgent" to userAgent,
        ),
        // Only `sendToUser` renders this, and it has already done so by the time we read back.
        errorText = 0,
    )

    /**
     * Runs the action [sendToUser] parked for [profileId], if any. `false` means nothing was pending
     * for it - which is what a forged intent gets, and is why the notification needs no
     * authentication.
     */
    fun executePending(activity: AppCompatActivity, profileId: Int?): Boolean {
        val pending = pendingFor(app.config.pendingUserAction, profileId) ?: return false
        app.config.pendingUserAction = null
        execute(activity, pending.toEvent(), UserActionCallback())
        return true
    }

    private fun executeRecaptcha(
        activity: AppCompatActivity,
        event: UserActionRequiredEvent,
        callback: UserActionCallback,
    ): Boolean {
        val siteKey = event.params.getString("siteKey") ?: return false
        val referer = event.params.getString("referer") ?: return false
        RecaptchaPromptDialog(
            activity = activity,
            siteKey = siteKey,
            referer = referer,
            onSuccess = { code ->
                finishAction(activity, event, callback, Bundle(
                    "recaptchaCode" to code,
                    "recaptchaTime" to System.currentTimeMillis(),
                ))
            },
            onCancel = callback.onCancel,
            onServerError = {
                executeRecaptchaActivity(activity, event, callback)
            },
        ).show()
        return true
    }

    private fun executeRecaptchaActivity(
        activity: AppCompatActivity,
        event: UserActionRequiredEvent,
        callback: UserActionCallback,
    ): Boolean {
        event.params.getString("siteKey") ?: return false
        event.params.getString("referer") ?: return false

        var listener: Any? = null
        listener = object {
            @Subscribe(threadMode = ThreadMode.MAIN)
            fun onRecaptchaResult(result: RecaptchaResult) {
                EventBus.getDefault().unregister(listener)
                when {
                    result.isError -> callback.onFailure?.invoke()
                    result.code != null -> {
                        finishAction(activity, event, callback, Bundle(
                            "recaptchaCode" to result.code,
                            "recaptchaTime" to System.currentTimeMillis(),
                        ))
                    }
                    else -> callback.onCancel?.invoke()
                }
            }
        }
        EventBus.getDefault().register(listener)

        val intent = Intent(activity, RecaptchaActivity::class.java).putExtras(event.params)
        activity.startActivity(intent)
        return true
    }

    private fun finishAction(
        activity: AppCompatActivity,
        event: UserActionRequiredEvent,
        callback: UserActionCallback,
        data: Bundle,
    ) {
        if (callback.onSuccess != null)
            callback.onSuccess.invoke(data)
        else if (event.profileId != null)
            EdziennikTask.syncProfile(
                profileId = event.profileId,
                arguments = data.toJsonObject(),
            ).enqueue(activity)
        else
            callback.onFailure?.invoke()
    }
}
