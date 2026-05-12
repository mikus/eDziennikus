package eu.mikus.edziennik.utils.models

import eu.mikus.edziennik.data.db.enums.MetadataType
import eu.mikus.edziennik.ext.asMetadataType
import pl.szczodrzynski.navlib.drawer.IUnreadCounter

class UnreadCounter : IUnreadCounter {
    override var profileId: Int = 0
    override var count: Int = 0
    lateinit var thingType: MetadataType

    override var drawerItemId: Int? = null
    override var type: Int
        get() = thingType.id
        set(value) { thingType = value.asMetadataType() }
}
