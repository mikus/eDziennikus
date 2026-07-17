/*
 * Copyright (c) Mikolaj Olszewski 2026-7-16.
 */
package eu.mikus.edziennik.ui.dialogs.settings

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ui.dialogs.base.ComposeDialog

class MessagesConfigDialog(
    activity: AppCompatActivity,
    private val reloadOnDismiss: Boolean = true,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : ComposeDialog(activity, onShowListener, onDismissListener) {

    override val TAG = "MessagesConfigDialog"
    override fun getTitleRes() = R.string.menu_messages_config
    override fun getPositiveButtonText() = R.string.ok

    @Composable
    override fun Content() = MessagesConfigContent(activity.applicationContext as App)

    override fun onDismiss() {
        if (reloadOnDismiss && activity is MainActivity) activity.reloadTarget()
    }
}

@Composable
private fun MessagesConfigContent(app: App) {
    val cfg = app.profile.config.ui
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SectionHeader(R.string.messages_config_compose)
        var onCompose by remember { mutableStateOf(cfg.messagesGreetingOnCompose) }
        CheckboxRow(R.string.messages_config_greeting_on_compose, onCompose) { onCompose = it; cfg.messagesGreetingOnCompose = it }
        var onReply by remember { mutableStateOf(cfg.messagesGreetingOnReply) }
        CheckboxRow(R.string.messages_config_greeting_on_reply, onReply) { onReply = it; cfg.messagesGreetingOnReply = it }
        var onForward by remember { mutableStateOf(cfg.messagesGreetingOnForward) }
        CheckboxRow(R.string.messages_config_greeting_on_forward, onForward) { onForward = it; cfg.messagesGreetingOnForward = it }

        var greeting by remember {
            mutableStateOf(
                cfg.messagesGreetingText
                    ?: app.getString(R.string.messages_config_greeting_default, app.profile.accountOwnerName),
            )
        }
        OutlinedTextField(
            value = greeting,
            onValueChange = { greeting = it; cfg.messagesGreetingText = messagesGreetingSave(it) },
            label = { Text(stringResource(R.string.messages_config_greeting_text)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}
