/*
 * Copyright (c) Kuba Szczodrzyński 2019-10-6.
 */

package eu.mikus.edziennik.data.api

import kotlin.text.RegexOption.IGNORE_CASE

object Regexes {
    val STYLE_CSS_COLOR by lazy {
        """color: (\w+);?""".toRegex()
    }

    val NOT_DIGITS by lazy {
        """[^0-9]""".toRegex()
    }

    val HTML_BR by lazy {
        """<br\s?/?>""".toRegex()
    }

    val MESSAGE_META by lazy {
        """^\[META:([A-z0-9-&=]+)]""".toRegex()
    }

    val HTML_INPUT_HIDDEN by lazy {
        """<input .*?type="hidden".+?>""".toRegex()
    }
    val HTML_INPUT_NAME by lazy {
        """name="(.+?)"""".toRegex()
    }
    val HTML_INPUT_VALUE by lazy {
        """value="(.+?)"""".toRegex()
    }
    val HTML_CSRF_TOKEN by lazy {
        """name="csrf-token" content="([A-z0-9=+/\-_]+?)"""".toRegex()
    }
    val HTML_FORM_ACTION by lazy {
        """<form .*?action="(.+?)"""".toRegex()
    }
    val HTML_RECAPTCHA_KEY by lazy {
        """data-sitekey="(.+?)"""".toRegex()
    }


    val LIBRUS_ATTACHMENT_KEY by lazy {
        """singleUseKey=([0-9A-z_]+)""".toRegex()
    }
    val LIBRUS_MESSAGE_ID by lazy {
        """/wiadomosci/[0-9]+/[0-9]+/([0-9]+?)/""".toRegex()
    }


    val LINKIFY_DATE_YMD by lazy {
        """(1\d{3}|20\d{2})[\-./](1[0-2]|0?\d)[\-./]([1-2]\d|3[0-1]|0?\d)""".toRegex()
    }
    val LINKIFY_DATE_DMY by lazy {
        """(?<![\d\-./])([1-2]\d|3[0-1]|0?\d)[\-./](1[0-2]|0?\d)(?:[\-./](1\d{3}|2?0?\d{2}))?(?![\d\-/])""".toRegex()
    }
    val LINKIFY_DATE_ABSOLUTE by lazy {
        """([1-3][0-9]|[1-9])\s(sty|lut|mar|kwi|maj|cze|lip|sie|wrz|paź|lis|gru).*?\s(1[0-9]{3}|20[0-9]{2})?""".toRegex(IGNORE_CASE)
    }
    val LINKIFY_DATE_RELATIVE by lazy {
        """za\s([0-9]+)?\s?(dni|dzień|tydzień|tygodnie)""".toRegex(IGNORE_CASE)
    }
}
