/*
 * Copyright (c) Kuba Szczodrzyński 2021-10-23.
 */

package eu.mikus.edziennik.ui.notes

import android.view.Gravity
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Noteable
import eu.mikus.edziennik.ext.dp
import eu.mikus.edziennik.ext.onClick

fun MaterialButton.setupNotesButton(
    activity: AppCompatActivity,
    owner: Noteable,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) {
    if (!isVisible)
        return
    icon = IconicsDrawable(activity, CommunityMaterial.Icon3.cmd_playlist_edit)
    setText(R.string.notes_button)
    iconPadding = 8.dp
    iconSize = 24.dp

    updateLayoutParams<LinearLayout.LayoutParams> {
        gravity = Gravity.CENTER_HORIZONTAL
    }
    updatePadding(left = 12.dp)

    onClick {
        NoteListDialog(
            activity = activity,
            owner = owner,
            onShowListener = onShowListener,
            onDismissListener = onDismissListener,
        ).show()
    }
}
