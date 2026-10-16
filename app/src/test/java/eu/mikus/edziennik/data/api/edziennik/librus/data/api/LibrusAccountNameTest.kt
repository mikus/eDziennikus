/*
 * Copyright (c) Mikolaj Olszewski 2026-10-16.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class LibrusAccountNameTest {

    private fun account(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

    private val PARENT = account("""{"GroupId":5,"FirstName":"anna","LastName":"NOWAK"}""")

    @Test fun `a parent GroupId replaces whatever was stored`() {
        assertEquals("Anna Nowak", librusMeAccountName(PARENT, stored = "Jan Kowalski"))
        assertEquals("Anna Nowak", librusMeAccountName(PARENT, stored = null))
    }

    @Test fun `a readable student GroupId clears a stored name`() {
        val student = account("""{"GroupId":8,"FirstName":"anna","LastName":"NOWAK"}""")
        assertNull(librusMeAccountName(student, stored = "Anna Nowak"))
    }

    /** First login reads 5..6 as a parent; this endpoint has always read only 5. */
    @Test fun `GroupId 6 reads as a student here, unlike first login`() {
        val six = account("""{"GroupId":6,"FirstName":"anna","LastName":"NOWAK"}""")
        assertNull(librusMeAccountName(six, stored = "Anna Nowak"))
    }

    @Test fun `a quoted parent GroupId still coerces`() {
        val quoted = account("""{"GroupId":"5","FirstName":"anna","LastName":"NOWAK"}""")
        assertEquals("Anna Nowak", librusMeAccountName(quoted, stored = null))
    }

    @Test fun `a missing GroupId keeps the stored name`() {
        val noGroup = account("""{"FirstName":"anna","LastName":"NOWAK"}""")
        assertEquals("Jan Kowalski", librusMeAccountName(noGroup, stored = "Jan Kowalski"))
        assertNull(librusMeAccountName(noGroup, stored = null))
    }

    @Test fun `a JSON null GroupId keeps the stored name`() {
        val jsonNull = account("""{"GroupId":null,"FirstName":"anna","LastName":"NOWAK"}""")
        assertEquals("Jan Kowalski", librusMeAccountName(jsonNull, stored = "Jan Kowalski"))
        assertNull(librusMeAccountName(jsonNull, stored = null))
    }

    @Test fun `an unreadable string GroupId keeps the stored name`() {
        val unreadable = account("""{"GroupId":"abc","FirstName":"anna","LastName":"NOWAK"}""")
        assertEquals("Jan Kowalski", librusMeAccountName(unreadable, stored = "Jan Kowalski"))
        assertNull(librusMeAccountName(unreadable, stored = null))
    }

    @Test fun `a boolean GroupId keeps the stored name`() {
        val boolean = account("""{"GroupId":true,"FirstName":"anna","LastName":"NOWAK"}""")
        assertEquals("Jan Kowalski", librusMeAccountName(boolean, stored = "Jan Kowalski"))
        assertNull(librusMeAccountName(boolean, stored = null))
    }

    @Test fun `a missing Account object keeps the stored name`() {
        assertEquals("Jan Kowalski", librusMeAccountName(null, stored = "Jan Kowalski"))
        assertNull(librusMeAccountName(null, stored = null))
    }
}
