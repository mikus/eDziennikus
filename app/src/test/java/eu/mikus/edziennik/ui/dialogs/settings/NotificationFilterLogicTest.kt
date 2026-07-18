/*
 * Copyright (c) Mikolaj Olszewski 2026-7-18.
 */
package eu.mikus.edziennik.ui.dialogs.settings

import eu.mikus.edziennik.data.db.enums.NotificationType
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class NotificationFilterLogicTest {

    private val eligible = NotificationType.values().filter { it.enabledByDefault != null }

    @Test
    fun `disabled is eligible minus enabled`() {
        val enabled = eligible.toSet() - NotificationType.TEACHER_ABSENCE
        val disabled = notificationFilterDisabled(eligible, enabled)
        assertEquals(setOf(NotificationType.TEACHER_ABSENCE), disabled)
    }

    @Test
    fun `all enabled yields empty disabled`() {
        assertEquals(emptySet(), notificationFilterDisabled(eligible, eligible.toSet()))
    }

    @Test
    fun `warn when a default-on type is disabled`() {
        val disabled = setOf(NotificationType.GRADE) // enabledByDefault == true
        assertTrue(shouldWarnDisabling(disabled))
    }

    @Test
    fun `no warn when only a default-off type is disabled`() {
        val disabled = setOf(NotificationType.TEACHER_ABSENCE) // enabledByDefault == false
        assertFalse(shouldWarnDisabling(disabled))
    }
}
