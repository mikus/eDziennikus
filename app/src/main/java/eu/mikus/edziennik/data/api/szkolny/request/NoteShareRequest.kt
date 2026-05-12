/*
 * Copyright (c) Kuba Szczodrzyński 2021-10-26.
 */

package eu.mikus.edziennik.data.api.szkolny.request

import eu.mikus.edziennik.data.db.entity.Note

data class NoteShareRequest (
    val deviceId: String,
    val device: Device? = null,

    val action: String = "note",

    val userCode: String,
    val studentNameLong: String,

    val shareTeamCode: String? = null,
    val unshareTeamCode: String? = null,
    val requesterName: String? = null,

    val noteId: Long? = null,
    val note: Note? = null
)

