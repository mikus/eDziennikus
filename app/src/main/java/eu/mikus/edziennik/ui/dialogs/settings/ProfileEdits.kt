/*
 * Copyright (c) Mikolaj Olszewski 2026-10-30.
 */
package eu.mikus.edziennik.ui.dialogs.settings

import eu.mikus.edziennik.data.db.entity.Profile

/** What [StudentNumberDialog] saves on OK: the student number, and nothing else. */
internal fun studentNumberEdit(number: Int): (Profile) -> Unit = { it.studentNumber = number }

/**
 * What [ProfileConfigDialog] saves: only the fields the user changed. [name] and [syncEnabled] are
 * written when they are non-null. [image] is written only when [setImage] is true, because a null
 * image is a value, not "unchanged".
 */
internal fun profileConfigEdit(
    name: String? = null,
    syncEnabled: Boolean? = null,
    setImage: Boolean = false,
    image: String? = null,
): (Profile) -> Unit = { profile ->
    name?.let { profile.name = it }
    syncEnabled?.let { profile.syncEnabled = it }
    if (setImage)
        profile.image = image
}
