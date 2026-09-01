/*
 * Copyright (c) Mikolaj Olszewski 2026-9-1.
 */
package eu.mikus.edziennik.compat

import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.enums.LoginType
import eu.mikus.edziennik.data.db.enums.MetadataType
import eu.mikus.edziennik.utils.models.UnreadCounter
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.jupiter.api.Test

class DrawerAdaptersTest {

    private fun profile(
        id: Int = 1,
        name: String = "Jan Kowalski",
        subname: String? = "1A",
    ) = Profile(
        id = id,
        loginStoreId = 2,
        loginStoreType = LoginType.LIBRUS,
        name = name,
        subname = subname,
    )

    @Test
    fun `toDrawerProfile reads id name subname and image through`() {
        val adapted = profile(id = 5, name = "Anna Nowak", subname = "2B").toDrawerProfile()
        assertEquals(5, adapted.id)
        assertEquals("Anna Nowak", adapted.name)
        assertEquals("2B", adapted.subname)
        assertNull(adapted.image)
    }

    @Test
    fun `toDrawerProfile reads a null subname through`() {
        assertNull(profile(subname = null).toDrawerProfile().subname)
    }

    @Test
    fun `toDrawerProfile name writes through to the entity`() {
        val source = profile(name = "Jan Kowalski")
        val adapted = source.toDrawerProfile()

        adapted.name = "Jan Nowak"
        assertEquals("Jan Nowak", source.name)
        assertEquals("Jan Nowak", adapted.name)
    }

    @Test
    fun `toDrawerProfile subname writes through to the entity`() {
        val source = profile(subname = "1A")
        val adapted = source.toDrawerProfile()

        adapted.subname = "3C"
        assertEquals("3C", source.subname)
        assertEquals("3C", adapted.subname)
    }

    @Test
    fun `toDrawerProfile image writes through to the entity`() {
        val source = profile()
        val adapted = source.toDrawerProfile()

        adapted.image = "/data/user/0/eu.mikus.edziennik/files/profile.png"
        assertEquals("/data/user/0/eu.mikus.edziennik/files/profile.png", source.image)
        assertEquals("/data/user/0/eu.mikus.edziennik/files/profile.png", adapted.image)
    }

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
