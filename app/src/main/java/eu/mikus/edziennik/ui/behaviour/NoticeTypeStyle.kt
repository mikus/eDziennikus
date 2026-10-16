/*
 * Copyright (c) Mikolaj Olszewski 2026-10-16.
 */

package eu.mikus.edziennik.ui.behaviour

import androidx.annotation.ColorRes
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Notice
import eu.szkolny.font.SzkolnyFont

internal data class NoticeTypeStyle(val icon: IIcon, @ColorRes val color: Int)

/**
 * The icon and colour of a notice type, for both BehaviourScreen and NoticesAdapter.
 *
 * NEUTRAL is an explicit arm and `else` means "unknown", so a value this code does not know can
 * never silently draw the neutral blue icon.
 */
internal fun noticeTypeStyle(type: Int): NoticeTypeStyle = when (type) {
    Notice.TYPE_POSITIVE -> NoticeTypeStyle(CommunityMaterial.Icon3.cmd_plus_circle_outline, R.color.md_green_600)
    Notice.TYPE_NEGATIVE -> NoticeTypeStyle(CommunityMaterial.Icon.cmd_alert_decagram_outline, R.color.md_red_600)
    Notice.TYPE_NEUTRAL -> NoticeTypeStyle(SzkolnyFont.Icon.szf_message_processing_outline, R.color.md_blue_500)
    else -> NoticeTypeStyle(CommunityMaterial.Icon2.cmd_help_circle_outline, R.color.md_grey_500)
}
