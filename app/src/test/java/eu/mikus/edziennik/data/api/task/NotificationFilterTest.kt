/*
 * Copyright (c) Mikolaj Olszewski 2026-9-27.
 */

package eu.mikus.edziennik.data.api.task

import eu.mikus.edziennik.data.db.entity.Notification
import eu.mikus.edziennik.data.db.enums.NotificationType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class NotificationFilterTest {

    private fun notification(profileId: Int?, type: NotificationType, id: Long) = Notification(
        id = id,
        title = "t$id",
        text = "x$id",
        type = type,
        profileId = profileId,
        profileName = profileId?.let { "p$it" },
    )

    /** Profile 1 suppresses GRADE; profile 2 suppresses nothing. The divergence is the whole point. */
    private val diverging: (Int) -> Set<NotificationType> = { profileId ->
        when (profileId) {
            1 -> setOf(NotificationType.GRADE)
            else -> emptySet()
        }
    }

    private fun ids(list: List<Notification>) = list.map { it.id }

    @Test
    fun `a type the notification's own profile filters is dropped`() {
        val list = mutableListOf(notification(1, NotificationType.GRADE, 10))
        retainUnfilteredNotifications(list, diverging)
        assertEquals(emptyList(), ids(list))
    }

    @Test
    fun `a type the notification's own profile does not filter survives`() {
        val list = mutableListOf(notification(1, NotificationType.MESSAGE, 11))
        retainUnfilteredNotifications(list, diverging)
        assertEquals(listOf(11L), ids(list))
    }

    @Test
    fun `one profile's filter does not drop another profile's notification of that type`() {
        val list = mutableListOf(
            notification(1, NotificationType.GRADE, 20),   // profile 1 filters GRADE -> dropped
            notification(2, NotificationType.GRADE, 21),   // profile 2 does not      -> survives
        )
        retainUnfilteredNotifications(list, diverging)
        assertEquals(listOf(21L), ids(list))
    }

    @Test
    fun `a notification with no profile survives every filter`() {
        val list = mutableListOf(notification(null, NotificationType.GRADE, 30))
        retainUnfilteredNotifications(list) { setOf(NotificationType.GRADE) }
        assertEquals(listOf(30L), ids(list))
    }

    @Test
    fun `survivors keep their original relative order across interleaved profiles`() {
        val list = mutableListOf(
            notification(2, NotificationType.MESSAGE, 40),
            notification(1, NotificationType.MESSAGE, 41),
            notification(2, NotificationType.GRADE, 42),
            notification(1, NotificationType.GRADE, 43),   // only this one is filtered
            notification(2, NotificationType.EVENT, 44),
        )
        retainUnfilteredNotifications(list, diverging)
        assertEquals(listOf(40L, 41L, 42L, 44L), ids(list))
    }

    @Test
    fun `an empty filter drops nothing`() {
        val list = mutableListOf(
            notification(1, NotificationType.GRADE, 50),
            notification(2, NotificationType.MESSAGE, 51),
        )
        retainUnfilteredNotifications(list) { emptySet() }
        assertEquals(listOf(50L, 51L), ids(list))
    }
}
