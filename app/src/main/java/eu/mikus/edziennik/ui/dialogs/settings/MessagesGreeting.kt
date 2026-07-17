/*
 * Copyright (c) Mikolaj Olszewski 2026-7-16.
 */
package eu.mikus.edziennik.ui.dialogs.settings

/** Ports MessagesConfigDialog.saveConfig: empty → null; else the trimmed text prefixed with "\n\n". */
fun messagesGreetingSave(input: String): String? =
    input.trim().let { if (it.isEmpty()) null else "\n\n$it" }
