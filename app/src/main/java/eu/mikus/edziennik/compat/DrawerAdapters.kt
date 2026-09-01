/*
 * Copyright (c) Mikolaj Olszewski 2026-9-1.
 */
package eu.mikus.edziennik.compat

import eu.mikus.edziennik.utils.models.UnreadCounter
import pl.szczodrzynski.navlib.drawer.IUnreadCounter

/**
 * Adapters presenting app-side models to the navlib shell, so the models themselves need not
 * implement navlib interfaces.
 *
 * All of the app's remaining navlib profile/counter coupling is meant to live here, so that
 * swapping the shell (N4) can remove it in one move.
 */

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
