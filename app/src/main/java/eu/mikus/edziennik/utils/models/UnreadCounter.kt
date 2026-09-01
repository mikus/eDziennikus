package eu.mikus.edziennik.utils.models

import eu.mikus.edziennik.data.db.enums.MetadataType
import eu.mikus.edziennik.ext.asMetadataType

class UnreadCounter {
    var profileId: Int = 0
    var count: Int = 0
    lateinit var thingType: MetadataType

    var drawerItemId: Int? = null
    var type: Int
        get() = thingType.id
        set(value) { thingType = value.asMetadataType() }
}
