/*
 * Copyright (c) Mikolaj Olszewski 2026-10-16.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import com.google.gson.JsonObject
import eu.mikus.edziennik.ext.buildFullName
import eu.mikus.edziennik.ext.getInt
import eu.mikus.edziennik.ext.getString

/**
 * Resolves `Profile.accountName` from the Me response's Account object.
 *
 * `accountName` is both the owner's name and, through its nullness, the account kind
 * (`Profile.isParent`; spec §6 S10), and the default message signature reads it
 * (`Profile.accountOwnerName`). So an unreadable `GroupId` must not become null: that would turn a
 * stored parent name into "student" and sign every message with the child's name. A `GroupId` that
 * cannot be read - missing, JSON null, unparsable, or no Account object at all - keeps [stored].
 *
 * Throwing instead would not help: Me is endpoint 1001, the head of the chain, so it would throw
 * first in every sync, use up the once-per-code circuit breaker and turn any later parser failure
 * into a whole-task abort.
 *
 * The parent domain stays `== 5`; first login uses `5..6` (`LibrusFirstLogin.kt:100`).
 */
internal fun librusMeAccountName(account: JsonObject?, stored: String?): String? =
    when (account?.getInt("GroupId")) {
        null -> stored
        5 -> buildFullName(account.getString("FirstName"), account.getString("LastName"))
        else -> null
    }
