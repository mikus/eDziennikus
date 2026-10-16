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
 * (`Profile.isParent`), and the default message signature reads it
 * (`Profile.accountOwnerName`). So an unreadable `GroupId` must not become null: that would turn a
 * stored parent name into "student" and sign every message with the child's name. A `GroupId` that
 * cannot be read - missing, JSON null, unparsable, or no Account object at all - keeps [stored].
 *
 * Throwing instead would not help: Me is endpoint 1001, the head of the chain, so it would throw
 * first in every sync, use up the once-per-code circuit breaker and turn any later parser failure
 * into a whole-task abort.
 *
 * The parent domain stays `== 5`; first login uses `5..6` and treats an unreadable `GroupId`
 * differently, see [librusFirstLoginAccountName].
 */
internal fun librusMeAccountName(account: JsonObject?, stored: String?): String? =
    when (account?.getInt("GroupId")) {
        null -> stored
        5 -> accountOwnerFullName(account)
        else -> null
    }

/**
 * The first-login counterpart of [librusMeAccountName]: resolves `Profile.accountName` for a new
 * Synergia/JST profile from the Me response's Account object.
 *
 * There is no stored value to keep here, so an unreadable `GroupId` - missing, JSON null or
 * unparsable - has to be resolved one way or the other, and the two silent defaults do not fail
 * alike. Reading it as a student would leave `accountName` null, and the default message signature
 * (`Profile.accountOwnerName`) would then become the child's name: wrong for a parent's messages.
 * Reading it as the owner stores the Account's own `FirstName`/`LastName`, which keeps the signature
 * and the sender names right either way; only the label derived from `Profile.isParent` can be wrong,
 * and only for a student. So an unreadable `GroupId` takes the owner's own name.
 *
 * That rests on a premise nobody has verified: that a student account's Account names are the
 * student's own. The guess is transient while Me can read `GroupId`: the first sync after login
 * runs [librusMeAccountName] and replaces it once `GroupId` is readable there.
 *
 * A missing Account object still gives null, as before, and so does an Account whose `FirstName` or
 * `LastName` cannot be read: `buildFullName(null, null)` would store "Null Null" and a single
 * missing name "Anna Null", and [librusMeAccountName] would then keep it.
 */
internal fun librusFirstLoginAccountName(account: JsonObject?): String? {
    account ?: return null
    return when (account.getInt("GroupId")) {
        null ->
            if (account.getString("FirstName") != null && account.getString("LastName") != null) {
                accountOwnerFullName(account)
            } else {
                null
            }
        in 5..6 -> accountOwnerFullName(account)
        else -> null
    }
}

private fun accountOwnerFullName(account: JsonObject?): String =
    buildFullName(account.getString("FirstName"), account.getString("LastName"))
