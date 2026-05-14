/*
 * Copyright (c) Kuba Szczodrzyński 2022-10-17.
 */

package eu.mikus.edziennik.data.db.enums

enum class LoginMode(
    val loginType: LoginType,
    val id: Int,
) {
    LIBRUS_EMAIL(LoginType.LIBRUS, id = 200),
    LIBRUS_SYNERGIA(LoginType.LIBRUS, id = 201),
    LIBRUS_JST(LoginType.LIBRUS, id = 202),
    DEMO(LoginType.DEMO, id = 800),
}
