/*
 * Copyright (c) Kuba Szczodrzyński 2021-3-23.
 * Copyright (c) Mikolaj Olszewski 2026-7-18.
 */
package eu.mikus.edziennik.ui.dialogs.settings

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.materialdrawer.view.BezelImageView
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.ui.compose.IconicsIcon
import eu.mikus.edziennik.ui.dialogs.ProfileRemoveDialog
import eu.mikus.edziennik.ui.dialogs.base.ComposeDialog

class ProfileConfigDialog(
    val mainActivity: MainActivity,
    private val profile: Profile,
    private val onProfileSaved: ((profile: Profile) -> Unit)? = null,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : ComposeDialog(mainActivity, onShowListener, onDismissListener) {

    override val TAG = "ProfileConfigDialog"
    override fun getTitleRes(): Int? = null
    override fun getPositiveButtonText() = R.string.close

    private var profileChanged = false
    private var profileRemoved = false
    private var avatarKey by mutableIntStateOf(0)
    private var name by mutableStateOf(profile.name)
    private var syncEnabled by mutableStateOf(profile.syncEnabled)

    @Composable
    override fun Content() {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally), contentAlignment = Alignment.BottomEnd) {
                AndroidView(
                    factory = { ImageViewFactory(it) },
                    update = { avatarKey.let { _ -> profile.applyImageTo(it) } },
                    modifier = Modifier.size(120.dp),
                )
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)
                        .clickable { requestImage() },
                    contentAlignment = Alignment.Center,
                ) {
                    IconicsIcon(CommunityMaterial.Icon2.cmd_image_plus, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; profile.name = it; profileChanged = true },
                singleLine = true,
                label = { Text(stringResource(R.string.profile_config_name_hint)) },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            Text(
                profile.subname ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            CheckboxRow(R.string.profile_config_sync_enabled, syncEnabled) {
                syncEnabled = it; profile.syncEnabled = it; profileChanged = true
            }
            OutlinedButton(
                onClick = {
                    ProfileRemoveDialog(mainActivity, profile.id, profile.name) {
                        profileRemoved = true; dialog.dismiss()
                    }.show()
                },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.profile_config_logout), color = Color(0xFFF44336))
            }
        }
    }

    private fun ImageViewFactory(ctx: android.content.Context) =
        BezelImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }

    private fun requestImage() {
        mainActivity.requestHandler.requestProfileImage(profile) {
            val p = it as? Profile ?: return@requestProfileImage
            if (this.profile == p) { profileChanged = true; avatarKey++ }
        }
    }

    override fun onDismiss() {
        if (!profileRemoved && profileChanged) {
            app.profileSave(profile)
            onProfileSaved?.invoke(profile)
        }
    }
}
