/*
 * Copyright (c) Mikolaj Olszewski 2026-9-1.
 */
package eu.mikus.edziennik.compat

import eu.mikus.edziennik.data.db.enums.MetadataType
import eu.mikus.edziennik.utils.models.UnreadCounter
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.jupiter.api.Test

class DrawerAdaptersTest {

    private fun counter(
        profileId: Int = 3,
        count: Int = 7,
        thingType: MetadataType = MetadataType.MESSAGE,
    ) = UnreadCounter().also {
        it.profileId = profileId
        it.count = count
        it.thingType = thingType
    }

    @Test
    fun `toDrawerCounter reads profileId and count through`() {
        val source = counter(profileId = 3, count = 7)
        val adapted = source.toDrawerCounter()
        assertEquals(3, adapted.profileId)
        assertEquals(7, adapted.count)

        adapted.profileId = 9
        assertEquals(9, source.profileId)
    }

    @Test
    fun `toDrawerCounter reads type through from thingType`() {
        assertEquals(8, counter(thingType = MetadataType.MESSAGE).toDrawerCounter().type)
        assertEquals(1, counter(thingType = MetadataType.GRADE).toDrawerCounter().type)
    }

    @Test
    fun `toDrawerCounter drawerItemId starts null and writes through`() {
        val source = counter()
        val adapted = source.toDrawerCounter()
        assertNull(adapted.drawerItemId)

        adapted.drawerItemId = 42
        assertEquals(42, source.drawerItemId)
        assertEquals(42, adapted.drawerItemId)
    }

    @Test
    fun `toDrawerCounter type setter round-trips into thingType`() {
        val source = counter(thingType = MetadataType.MESSAGE)
        val adapted = source.toDrawerCounter()

        adapted.type = MetadataType.GRADE.id
        assertSame(MetadataType.GRADE, source.thingType)
        assertEquals(MetadataType.GRADE.id, adapted.type)
    }

    @Test
    fun `toDrawerCounter count writes through`() {
        val source = counter(count = 7)
        val adapted = source.toDrawerCounter()

        adapted.count = 11
        assertEquals(11, source.count)
        assertEquals(11, adapted.count)
    }
}
