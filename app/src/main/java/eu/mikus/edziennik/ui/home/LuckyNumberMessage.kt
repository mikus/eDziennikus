/*
 * Copyright (c) Mikolaj Olszewski 2026-6-30.
 */

package eu.mikus.edziennik.ui.home

import eu.mikus.edziennik.R
import eu.mikus.edziennik.utils.models.Date

/**
 * Pure port of HomeLuckyNumberCard's title/emoji/subtext selection. Android-free: returns resource
 * ids + format args; the Screen resolves them with stringResource. Pass the lucky number's own
 * value/date (null when there is no row) plus today + the profile's student number.
 */
object LuckyNumberMessage {

    data class Ui(
        val titleRes: Int,
        val titleArgs: List<Any>,
        val emojiRes: Int,
    )

    /** @param luckyNumber the row's number, or null when there is no lucky-number row at all. */
    fun build(luckyNumber: Int?, luckyDate: Date?, number: Int, today: Date, studentNumber: Int): Ui {
        val todayValue = today.value
        val tomorrowValue = Date.fromValue(today.value).stepForward(0, 0, 1).value
        val isYours = luckyNumber != null && luckyNumber == studentNumber

        val (titleRes, args) = when {
            luckyNumber == null -> R.string.home_lucky_number_no_info to emptyList()
            luckyNumber == -1 -> R.string.home_lucky_number_no_number to emptyList()
            isYours -> when (luckyDate?.value) {
                todayValue -> R.string.home_lucky_number_yours_today to emptyList()
                tomorrowValue -> R.string.home_lucky_number_yours_tomorrow to emptyList()
                else -> R.string.home_lucky_number_yours_later to listOf<Any>(luckyDate?.formattedString ?: "")
            }
            else -> when (luckyDate?.value) {
                todayValue -> R.string.home_lucky_number_today to listOf<Any>(number)
                tomorrowValue -> R.string.home_lucky_number_tomorrow to listOf<Any>(number)
                else -> R.string.home_lucky_number_later to listOf<Any>(luckyDate?.formattedString ?: "", number)
            }
        }

        val emojiRes = when {
            luckyNumber == null || luckyNumber == -1 -> R.drawable.emoji_sad
            isYours -> R.drawable.emoji_glasses
            else -> R.drawable.emoji_smiling
        }

        return Ui(titleRes, args, emojiRes)
    }

    fun subText(profileName: String, studentNumber: Int): Pair<Int, List<Any>> =
        if (studentNumber == -1) {
            R.string.home_lucky_number_details_click_to_set to listOf<Any>(profileName, studentNumber)
        } else {
            R.string.home_lucky_number_details to listOf<Any>(profileName, studentNumber)
        }
}
