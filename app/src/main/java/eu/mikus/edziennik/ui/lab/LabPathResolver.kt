/*
 * Copyright (c) Mikolaj Olszewski 2026-9-15.
 */

package eu.mikus.edziennik.ui.lab

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import eu.mikus.edziennik.config.BaseConfig

/**
 * Walks a [LabPath] down the six roots and says what it points at.
 *
 * Replaces `LabProfileFragment.kt:66-87`, whose `when (el)` matched the root by its *display string*
 * and then re-dispatched on the parent's runtime type - which could not tell the two config roots
 * apart, because both are the same `HashMap<String, String?>` (`BaseConfig.kt:27`).
 *
 * Returns a [Result] rather than throwing: the caller is a ViewModel with a [LabEffect] to emit, not
 * a `try` block that the write path escapes anyway.
 */
object LabPathResolver {

    fun resolve(roots: Map<LabRoot, Any?>, path: LabPath): Result<Resolved> = runCatching {
        val root = roots[path.root]
            ?: throw LabEditError("\"${path.root.label}\" is not available on this profile")

        if (path.segments.isEmpty())
            return@runCatching Resolved(path.root, LabTarget.Unsupported("a root is not editable"))

        var parent: Any = root
        var current: Any? = root
        var name = ""
        for (segment in path.segments) {
            parent = current ?: throw LabEditError("cannot descend into a null value at \"$segment\"")
            name = segment
            current = descend(parent, segment)
        }
        Resolved(path.root, classify(parent, name, current))
    }

    private fun descend(parent: Any, segment: String): Any? = when (parent) {
        is JsonObject -> parent.get(segment) ?: throw LabEditError("no \"$segment\" in this object")
        is JsonArray -> parent.get(
            segment.toIntOrNull() ?: throw LabEditError("\"$segment\" is not an array index"),
        )
        is BaseConfig -> parent.values[segment]
        else -> {
            val field = parent::class.java.getDeclaredField(segment)
            field.isAccessible = true
            field.get(parent)
        }
    }

    private fun classify(parent: Any, name: String, current: Any?): LabTarget = when (parent) {
        // Reachable, not dead: LabJsonAdapter.kt:64 indexes arrays into clickable rows. Dropping this
        // arm sends array parents into the reflective branch and getDeclaredField("0").
        is JsonArray -> LabTarget.Unsupported("editing array elements is not supported")
        is JsonObject ->
            if (current is JsonPrimitive) LabTarget.JsonLeaf(parent, name, current)
            else LabTarget.Unsupported("only primitive leaves can be edited")
        is BaseConfig -> LabTarget.ConfigEntry(parent, name)
        else -> LabTarget.Field(parent, name, current)
    }
}
