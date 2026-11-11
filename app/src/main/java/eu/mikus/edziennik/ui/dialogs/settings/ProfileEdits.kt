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

/**
 * The fields the user changed in [ProfileConfigDialog], each recorded with its value as it changes,
 * and whether the profile was removed meanwhile. [edit] is what the dialog saves on dismiss.
 */
internal class ProfileConfigChanges {
    private var name: String? = null
    private var syncEnabled: Boolean? = null
    private var imageChanged = false
    private var image: String? = null
    private var removed = false

    fun name(value: String) { name = value }

    fun syncEnabled(value: Boolean) { syncEnabled = value }

    /** A null [value] clears the image: it is a value, not "unchanged". */
    fun image(value: String?) { imageChanged = true; image = value }

    fun removed() { removed = true }

    /** The edit that writes every recorded field and no other; `null` when there is nothing to save. */
    fun edit(): ((Profile) -> Unit)? =
        if (removed || (name == null && syncEnabled == null && !imageChanged)) null
        else profileConfigEdit(name = name, syncEnabled = syncEnabled, setImage = imageChanged, image = image)
}
