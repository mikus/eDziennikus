/*
 * Copyright (c) Mikolaj Olszewski 2026-8-7.
 */

package eu.mikus.edziennik.ui.base

import androidx.annotation.StringRes
import com.mikepenz.iconics.typeface.IIcon

/**
 * One row of the current screen's own menu. Today it becomes a contextual navlib bottom-sheet item;
 * after the shell swap, a row in the Compose sheet. The host closes the menu before running [onClick].
 *
 * Deliberately navlib-free: [IIcon] is already the app's icon vocabulary (`NavTarget.icon`,
 * `ui/compose/IconicsIcon.kt`), so these types survive the shell swap untouched.
 */
data class ScreenAction(
    @StringRes val titleRes: Int,
    val icon: IIcon,
    @StringRes val descriptionRes: Int? = null,
    val separatorBefore: Boolean = false,
    val onClick: () -> Unit,
)

/** The current screen's single primary action. Today: navlib's extended FAB. `null` = no action. */
data class ScreenFab(
    @StringRes val labelRes: Int,
    val icon: IIcon,
    val onClick: () -> Unit,
)
