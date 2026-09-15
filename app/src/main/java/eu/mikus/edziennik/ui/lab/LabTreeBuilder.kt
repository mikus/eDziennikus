/*
 * Copyright (c) Mikolaj Olszewski 2026-9-15.
 */

package eu.mikus.edziennik.ui.lab

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

/**
 * Turns `readLabJson`'s six-root map into a flat, display-ordered list of [LabNode].
 *
 * It takes `Map<LabRoot, JsonElement>`, not the one `JsonObject` the old `showJson()` built, so a
 * root's identity never travels between two functions as a display string - the same reason [LabPath]
 * is structured rather than a colon string.
 *
 * Eager on purpose: the whole tree is walked once and Gson does not escape this unit. Expansion is a
 * separate projection ([visible]) whose owner is the ViewModel, exactly as `AttendanceViewModel.kt:38,
 * :72-89` owns its expand set - not the lazy one-level insert `LabJsonAdapter.expandModel` did.
 */
object LabTreeBuilder {

    /** `JsonObjectViewHolder.kt@53a07964:44` - `.take(200)`. */
    private const val PreviewChars = 200

    fun build(roots: Map<LabRoot, JsonElement>): List<LabNode> = buildList {
        roots.forEach { (root, value) -> append(LabPath(root), root.label, value, depth = 1) }
    }

    /**
     * Keeps a node only while every one of its ancestors is expanded, and stamps `expanded` on the
     * containers that survive. Pure, so the rule is asserted rather than branched in a composable.
     */
    fun visible(nodes: List<LabNode>, expanded: Set<LabPath>): List<LabNode> = buildList {
        var cutoff = Int.MAX_VALUE
        for (node in nodes) {
            if (node.depth >= cutoff) continue
            cutoff = Int.MAX_VALUE
            val isOpen = node.path in expanded
            add(if (node is LabNode.Container) node.copy(expanded = isOpen) else node)
            if (node is LabNode.Container && !isOpen) cutoff = node.depth + 1
        }
    }

    private fun MutableList<LabNode>.append(path: LabPath, name: String, element: JsonElement, depth: Int) {
        when (element) {
            is JsonObject -> {
                add(
                    container(
                        path, name, depth, "Object", element, element.size(),
                        if (depth == 1) LabContainerStyle.Full else LabContainerStyle.Compact,
                    ),
                )
                element.entrySet().forEach { (key, value) -> append(path.child(key), key, value, depth + 1) }
            }
            is JsonArray -> {
                // ITEM_TYPE_ARRAY was chosen by kind, at any depth, and always carried preview+summary.
                add(container(path, name, depth, "Array", element, element.size(), LabContainerStyle.Full))
                element.forEachIndexed { index, value ->
                    val key = index.toString()
                    append(path.child(key), key, value, depth + 1)
                }
            }
            else -> add(LabNode.Leaf(path, name, depth, typeLabelOf(element), element.toString()))
        }
    }

    private fun container(
        path: LabPath,
        name: String,
        depth: Int,
        typeLabel: String,
        element: JsonElement,
        size: Int,
        style: LabContainerStyle,
    ): LabNode.Container {
        // Only a Full row renders these. Computing them for a Compact row would serialise its whole
        // subtree for something the screen discards - JsonSubObjectViewHolder.kt@53a07964:30-41 had neither.
        val full = style == LabContainerStyle.Full
        return LabNode.Container(
            path = path, name = name, depth = depth, typeLabel = typeLabel, style = style,
            preview = if (full) element.toString().take(PreviewChars) else null,
            summary = if (full) "$size elements" else null,
        )
    }

    /** `JsonElementViewHolder.kt@53a07964:34-43`, verbatim. */
    private fun typeLabelOf(element: JsonElement): String? = when {
        element is JsonPrimitive && element.isNumber -> "Number"
        element is JsonPrimitive && element.isString -> "String"
        element is JsonPrimitive && element.isBoolean -> "Boolean"
        element is JsonPrimitive -> "Primitive"
        element is JsonNull -> "null"
        else -> null
    }
}
