/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-7.
 * Copyright (c) Mikolaj Olszewski 2026-9-1.
 */
package eu.mikus.edziennik.compat

import android.content.Context
import android.widget.ImageView
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.ext.colorFromName
import eu.mikus.edziennik.ext.getDrawable
import eu.mikus.edziennik.utils.models.UnreadCounter
import pl.szczodrzynski.navlib.ImageHolder
import pl.szczodrzynski.navlib.drawer.IDrawerProfile
import pl.szczodrzynski.navlib.drawer.IUnreadCounter

/**
 * Adapters presenting app-side models to the navlib shell, so the models themselves need not
 * implement navlib interfaces.
 *
 * All of the app's remaining navlib profile/counter coupling is meant to live here, so that
 * swapping the shell (N4) can remove it in one move.
 */

internal fun Profile.toDrawerProfile(): IDrawerProfile = object : IDrawerProfile {
    override val id: Int get() = this@toDrawerProfile.id
    override var name: String
        get() = this@toDrawerProfile.name
        set(value) { this@toDrawerProfile.name = value }
    override var subname: String?
        get() = this@toDrawerProfile.subname
        set(value) { this@toDrawerProfile.subname = value }
    override var image: String?
        get() = this@toDrawerProfile.image
        set(value) { this@toDrawerProfile.image = value }

    override fun getImageDrawable(context: Context) = this@toDrawerProfile.getDrawable(context)
    override fun getImageHolder(context: Context) = this@toDrawerProfile.getHolder()
    override fun applyImageTo(imageView: ImageView) {
        getImageHolder(imageView.context).applyTo(imageView)
    }
}

internal fun UnreadCounter.toDrawerCounter(): IUnreadCounter = object : IUnreadCounter {
    override var profileId: Int
        get() = this@toDrawerCounter.profileId
        set(value) { this@toDrawerCounter.profileId = value }
    override var count: Int
        get() = this@toDrawerCounter.count
        set(value) { this@toDrawerCounter.count = value }
    override var type: Int
        get() = this@toDrawerCounter.type
        set(value) { this@toDrawerCounter.type = value }
    override var drawerItemId: Int?
        get() = this@toDrawerCounter.drawerItemId
        set(value) { this@toDrawerCounter.drawerItemId = value }
}

private fun Profile.getHolder(): ImageHolder {
    if (archived) {
        return ImageHolder(R.drawable.profile_archived, colorFromName(name))
    }

    return if (!image.isNullOrEmpty()) {
        try {
            ProfileImageHolder(image ?: "")
        } catch (_: Exception) {
            ImageHolder(R.drawable.profile, colorFromName(name))
        }
    } else {
        ImageHolder(R.drawable.profile, colorFromName(name))
    }
}

/**
 * navlib's [ImageHolder.applyTo] loads a `.gif` avatar by constructing a `GifDrawable`, which
 * throws if the file is missing or is not a valid GIF; other formats go through
 * `ImageView.setImageURI`, which never throws. Swallowing that degrades a corrupt GIF avatar to
 * a blank image instead of crashing the drawer bind.
 */
private class ProfileImageHolder(url: String) : ImageHolder(url) {
    override fun applyTo(imageView: ImageView, tag: String?): Boolean {
        return try {
            super.applyTo(imageView, tag)
        } catch (_: Exception) { false }
    }
}
