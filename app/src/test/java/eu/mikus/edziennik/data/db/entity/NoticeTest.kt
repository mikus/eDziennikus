/*
 * Copyright (c) Mikolaj Olszewski 2026-10-28.
 */
package eu.mikus.edziennik.data.db.entity

import eu.mikus.edziennik.data.db.full.NoticeFull
import eu.mikus.edziennik.utils.models.Date
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/** The gate on [Notice.addedDateUnknown]. */
class NoticeTest {

    private val d = Date(2026, 3, 10)

    private fun notice(addedDate: Long) =
        Notice(1, 1L, Notice.TYPE_NEUTRAL, 1, "t", null, null, 7L, addedDate)

    @Test fun `the sentinel reads as an unknown added date`() {
        assertTrue(notice(Notice.ADDED_DATE_UNKNOWN).addedDateUnknown)
    }

    @Test fun `a real added date is known`() {
        assertFalse(notice(d.inMillis).addedDateUnknown)
    }

    @Test fun `a NoticeFull built without an added date is known`() {
        // Its default added date is "now", and the subclass answers the same question the same way.
        assertFalse(NoticeFull(1, 1L, Notice.TYPE_NEUTRAL, 1, "t", null, null, 7L).addedDateUnknown)
    }
}
