/*
 * Copyright (c) Kuba Szczodrzyński 2021-10-4.
 */

package eu.mikus.edziennik.ui.messages.compose

import android.content.Context
import com.hootsuite.nachos.NachoTextView
import com.hootsuite.nachos.chip.ChipSpan
import com.hootsuite.nachos.tokenizer.SpanChipTokenizer
import eu.mikus.edziennik.data.db.entity.Teacher

class MessagesComposeChipTokenizer(
    context: Context,
    nacho: NachoTextView,
    teacherList: List<Teacher>,
) : SpanChipTokenizer<ChipSpan>(
    context,
    MessagesComposeChipCreator(
        context = context,
        nacho = nacho,
        teacherList = teacherList
    ),
    ChipSpan::class.java
)
