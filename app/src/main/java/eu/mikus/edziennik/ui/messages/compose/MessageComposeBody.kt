/*
 * Copyright (c) Mikolaj Olszewski 2026-7-31.
 */
package eu.mikus.edziennik.ui.messages.compose

/** Which subject transform a compose-payload implies (reply/forward prefix vs none). */
enum class SubjectMode { NONE, REPLY, FORWARD }

fun subjectMode(payloadType: String?): SubjectMode = when (payloadType) {
    "reply" -> SubjectMode.REPLY
    "forward" -> SubjectMode.FORWARD
    else -> SubjectMode.NONE
}

/** The greeting to prepend for a reply/forward, gated by the per-profile flags (null = none). */
fun greetingFor(payloadType: String?, greetingText: String, onReply: Boolean, onForward: Boolean): String? =
    when (payloadType) {
        "reply" -> if (onReply) greetingText else null
        "forward" -> if (onForward) greetingText else null
        else -> null
    }
