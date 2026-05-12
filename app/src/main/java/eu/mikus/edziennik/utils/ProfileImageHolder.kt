/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-7.
 */

package eu.mikus.edziennik.utils

import android.widget.ImageView
import pl.szczodrzynski.navlib.ImageHolder

class ProfileImageHolder(url: String) : ImageHolder(url) {

    override fun applyTo(imageView: ImageView, tag: String?): Boolean {
        return try {
            super.applyTo(imageView, tag)
        } catch (_: Exception) { false }
    }
}
