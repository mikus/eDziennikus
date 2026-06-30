/*
 * Copyright (c) Mikolaj Olszewski 2026-6-30.
 */

package eu.mikus.edziennik.ui.home

import eu.mikus.edziennik.R
import eu.mikus.edziennik.utils.models.Date
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class LuckyNumberMessageTest {

    private val today = Date(2026, 6, 1)
    private val tomorrow = Date(2026, 6, 2)

    @Test
    fun `null lucky number -- no info + sad emoji`() {
        val m = LuckyNumberMessage.build(luckyNumber = null, luckyDate = null, number = -1, today = today, studentNumber = 7)
        assertEquals(R.string.home_lucky_number_no_info, m.titleRes)
        assertEquals(R.drawable.emoji_sad, m.emojiRes)
    }

    @Test
    fun `number -1 -- no number + sad emoji`() {
        val m = LuckyNumberMessage.build(luckyNumber = -1, luckyDate = today, number = -1, today = today, studentNumber = 7)
        assertEquals(R.string.home_lucky_number_no_number, m.titleRes)
        assertEquals(R.drawable.emoji_sad, m.emojiRes)
    }

    @Test
    fun `yours today -- glasses emoji`() {
        val m = LuckyNumberMessage.build(luckyNumber = 7, luckyDate = today, number = 7, today = today, studentNumber = 7)
        assertEquals(R.string.home_lucky_number_yours_today, m.titleRes)
        assertEquals(R.drawable.emoji_glasses, m.emojiRes)
    }

    @Test
    fun `others tomorrow -- smiling emoji + number arg`() {
        val m = LuckyNumberMessage.build(luckyNumber = 13, luckyDate = tomorrow, number = 13, today = today, studentNumber = 7)
        assertEquals(R.string.home_lucky_number_tomorrow, m.titleRes)
        assertEquals(listOf<Any>(13), m.titleArgs)
        assertEquals(R.drawable.emoji_smiling, m.emojiRes)
    }

    @Test
    fun `others later -- date + number args`() {
        val later = Date(2026, 6, 9)
        val m = LuckyNumberMessage.build(luckyNumber = 13, luckyDate = later, number = 13, today = today, studentNumber = 7)
        assertEquals(R.string.home_lucky_number_later, m.titleRes)
        assertEquals(listOf<Any>(later.formattedString, 13), m.titleArgs)
    }

    @Test
    fun `subtext switches on student number set`() {
        val unset = LuckyNumberMessage.subText(profileName = "Jan", studentNumber = -1)
        val set = LuckyNumberMessage.subText(profileName = "Jan", studentNumber = 7)
        assertEquals(R.string.home_lucky_number_details_click_to_set, unset.first)
        assertEquals(R.string.home_lucky_number_details, set.first)
        assertEquals(listOf<Any>("Jan", 7), set.second)
    }
}
