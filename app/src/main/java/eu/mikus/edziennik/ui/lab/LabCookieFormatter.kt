/*
 * Copyright (c) Mikolaj Olszewski 2026-9-15.
 */

package eu.mikus.edziennik.ui.lab

import okhttp3.Cookie
import pl.szczodrzynski.fslogin.decode

/**
 * `LabPageFragment.kt@53a07964:177-203`, lifted out of the `Spannable` concatenation so it can be asserted.
 *
 * The persistent flag rides along because the readout underlined persistent cookie names
 * (`asUnderlineSpannable`, `ext/TextExtensions.kt@53a07964:163`). Compose takes no `Spannable`, so the screen
 * re-renders that cue as `SpanStyle(textDecoration = TextDecoration.Underline)`; the extension itself
 * then has no caller left and commit 4 removes it.
 */
object LabCookieFormatter {

    private const val ValueChars = 40

    fun format(cookies: List<Cookie>): List<CookieGroup> =
        cookies.sortedBy { it.domain() }
            .groupBy { it.domain() }
            .map { (domain, group) ->
                CookieGroup(
                    domain = domain,
                    lines = group.sortedBy { it.name() }.map {
                        CookieLine(it.name(), it.value().decode().take(ValueChars), it.persistent())
                    },
                )
            }
}
