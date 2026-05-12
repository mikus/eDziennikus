package eu.mikus.edziennik.data.api.events

import eu.mikus.edziennik.data.db.entity.LoginStore
import eu.mikus.edziennik.data.db.entity.Profile

data class FirstLoginFinishedEvent(val profileList: List<Profile>, val loginStore: LoginStore)
