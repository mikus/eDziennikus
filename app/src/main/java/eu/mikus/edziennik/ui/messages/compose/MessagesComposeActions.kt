/*
 * Copyright (c) Mikolaj Olszewski 2026-8-7.
 */

package eu.mikus.edziennik.ui.messages.compose

import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ui.base.ScreenAction

/**
 * The compose-message screen's menu rows. Pure, so the discard row's position is an assertion
 * instead of the `addItemAt(2, …)` coupling it replaces.
 */
internal fun messagesComposeActions(
    hasDraft: Boolean,
    onSend: () -> Unit,
    onSaveDraft: () -> Unit,
    onDiscard: () -> Unit,
    onConfig: () -> Unit,
): List<ScreenAction> = buildList {
    add(ScreenAction(
        R.string.messages_compose_send_long,
        CommunityMaterial.Icon3.cmd_send_outline,
        onClick = onSend,
    ))
    add(ScreenAction(
        R.string.messages_compose_save_draft,
        CommunityMaterial.Icon.cmd_content_save_edit_outline,
        onClick = onSaveDraft,
    ))
    if (hasDraft)
        add(ScreenAction(
            R.string.messages_compose_discard_draft,
            CommunityMaterial.Icon3.cmd_text_box_remove_outline,
            onClick = onDiscard,
        ))
    add(ScreenAction(
        R.string.menu_messages_config,
        CommunityMaterial.Icon.cmd_cog_outline,
        separatorBefore = true,
        onClick = onConfig,
    ))
}
