/*
 * Copyright (c) Kuba Szczodrzyński 2021-10-17.
 */

package eu.mikus.edziennik.ext

import android.util.LongSparseArray
import androidx.core.util.forEach
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.entity.Teacher
import eu.mikus.edziennik.data.db.entity.Team

fun List<Teacher>.byId(id: Long) = firstOrNull { it.id == id }
fun List<Teacher>.byNameFirstLast(nameFirstLast: String) = firstOrNull { it.name + " " + it.surname == nameFirstLast }
fun List<Teacher>.byNameLastFirst(nameLastFirst: String) = firstOrNull { it.surname + " " + it.name == nameLastFirst }
fun List<Teacher>.byNameFDotLast(nameFDotLast: String) = firstOrNull { it.name + "." + it.surname == nameFDotLast }
fun List<Teacher>.byNameFDotSpaceLast(nameFDotSpaceLast: String) = firstOrNull { it.name + ". " + it.surname == nameFDotSpaceLast }

fun List<Profile>.filterOutArchived() = this.filter { !it.archived }

fun List<Team>.getById(id: Long): Team? {
    return singleOrNull { it.id == id }
}

fun LongSparseArray<Team>.getById(id: Long): Team? {
    forEach { _, value ->
        if (value.id == id)
            return value
    }
    return null
}
