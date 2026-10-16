/*
 * Copyright (c) Mikolaj Olszewski 2026-10-16.
 */

package eu.mikus.edziennik.ui.behaviour

import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Notice
import eu.szkolny.font.SzkolnyFont
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class NoticeTypeStyleTest {

    private val unknown = NoticeTypeStyle(CommunityMaterial.Icon2.cmd_help_circle_outline, R.color.md_grey_500)

    @Test fun `a positive notice keeps its green plus`() {
        assertEquals(
            NoticeTypeStyle(CommunityMaterial.Icon3.cmd_plus_circle_outline, R.color.md_green_600),
            noticeTypeStyle(Notice.TYPE_POSITIVE),
        )
    }

    @Test fun `a negative notice keeps its red alert`() {
        assertEquals(
            NoticeTypeStyle(CommunityMaterial.Icon.cmd_alert_decagram_outline, R.color.md_red_600),
            noticeTypeStyle(Notice.TYPE_NEGATIVE),
        )
    }

    @Test fun `a neutral notice keeps its blue message`() {
        assertEquals(
            NoticeTypeStyle(SzkolnyFont.Icon.szf_message_processing_outline, R.color.md_blue_500),
            noticeTypeStyle(Notice.TYPE_NEUTRAL),
        )
    }

    @Test fun `an unknown notice draws a grey help icon, not the neutral one`() {
        assertEquals(unknown, noticeTypeStyle(Notice.TYPE_UNKNOWN))
    }

    @Test fun `a type no version wrote draws the unknown style`() {
        assertEquals(unknown, noticeTypeStyle(7))
    }
}
