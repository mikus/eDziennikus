/*
 * Copyright (c) Mikolaj Olszewski 2026-9-15.
 */

package eu.mikus.edziennik.ui.lab

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LabTreeBuilderTest {

    /** `Config: { a: "1", nested: { b: 2 }, arr: [7, 8], nul: null, flag: true }`. */
    private fun sample(): Map<LabRoot, JsonElement> = mapOf(
        LabRoot.CONFIG_GLOBAL to JsonObject().apply {
            addProperty("a", "1")
            add("nested", JsonObject().apply { addProperty("b", 2) })
            add("arr", JsonArray().apply { add(7); add(8) })
            add("nul", JsonNull.INSTANCE)
            addProperty("flag", true)
        },
    )

    @Test
    fun `each root in the map becomes one depth-1 row, in map order`() {
        val nodes = LabTreeBuilder.build(
            mapOf(LabRoot.PROFILE to JsonObject(), LabRoot.CONFIG_GLOBAL to JsonObject()),
        )
        assertEquals(listOf("Profile", "Config"), nodes.map { it.name })
        assertEquals(listOf(LabRoot.PROFILE, LabRoot.CONFIG_GLOBAL), nodes.map { it.path.root })
        assertTrue(nodes.all { it.depth == 1 && it.path.segments.isEmpty() })
    }

    @Test
    fun `a Full container's preview is the serialised subtree`() {
        val root = LabTreeBuilder.build(sample()).single { it.depth == 1 } as LabNode.Container
        // Asserted as content, not as non-null: `preview = if (full) "" else null` would satisfy a
        // bare assertNotNull, and the preview IS the serialised root - that is its whole job.
        assertEquals("""{"a":"1","nested":{"b":2},"arr":[7,8],"nul":null,"flag":true}""", root.preview)
        assertEquals("5 elements", root.summary)
    }

    @Test
    fun `a Compact container computes no preview or summary`() {
        val nested = LabTreeBuilder.build(sample()).single { it.name == "nested" } as LabNode.Container
        // LabScreen reads both only under `style == Full`, and JsonSubObjectViewHolder.kt@53a07964:30-41
        // rendered neither - so serialising this subtree would be work the row throws away.
        assertNull(nested.preview)
        assertNull(nested.summary)
    }

    @Test
    fun `nesting produces depth and a structured path, not a colon string`() {
        val b = LabTreeBuilder.build(sample()).single { it.name == "b" }
        assertEquals(3, b.depth)
        assertEquals(LabPath(LabRoot.CONFIG_GLOBAL, listOf("nested", "b")), b.path)
    }

    @Test
    fun `array children are addressed by index`() {
        val nodes = LabTreeBuilder.build(sample())
        val arr = nodes.single { it.name == "arr" }
        assertIs<LabNode.Container>(arr)
        assertEquals("Array", arr.typeLabel)
        assertEquals("2 elements", arr.summary)
        val first = nodes.single { it.path == LabPath(LabRoot.CONFIG_GLOBAL, listOf("arr", "0")) }
        assertIs<LabNode.Leaf>(first)
        assertEquals("7", first.displayText)
    }

    @Test
    fun `the container style is decided by depth, and an array is always Full`() {
        val nodes = LabTreeBuilder.build(sample())
        // LabJsonAdapter.kt@53a07964:98-100 - `if (item.level == 1)` picked the preview/summary rendering.
        assertEquals(LabContainerStyle.Full, (nodes.single { it.depth == 1 } as LabNode.Container).style)
        assertEquals(LabContainerStyle.Compact, (nodes.single { it.name == "nested" } as LabNode.Container).style)
        // ...but ITEM_TYPE_ARRAY was chosen by kind, at any depth.
        assertEquals(LabContainerStyle.Full, (nodes.single { it.name == "arr" } as LabNode.Container).style)
    }

    @Test
    fun `leaf type labels match the old view holder`() {
        val leaves = LabTreeBuilder.build(sample()).filterIsInstance<LabNode.Leaf>().associateBy { it.name }
        assertEquals("String", leaves.getValue("a").typeLabel)
        assertEquals("Number", leaves.getValue("b").typeLabel)
        assertEquals("Boolean", leaves.getValue("flag").typeLabel)
        assertEquals("null", leaves.getValue("nul").typeLabel)
    }

    @Test
    fun `display text is the quoted JSON rendering`() {
        val leaves = LabTreeBuilder.build(sample()).filterIsInstance<LabNode.Leaf>().associateBy { it.name }
        // JsonElementViewHolder.kt@53a07964:51 used jsonElement.toString(), which quotes strings. The dialog
        // prefill does not - that is LabValueCodec.format's job, and they are different strings.
        assertEquals("\"1\"", leaves.getValue("a").displayText)
        assertEquals("2", leaves.getValue("b").displayText)
        assertEquals("null", leaves.getValue("nul").displayText)
    }

    @Test
    fun `nothing below a collapsed container is visible`() {
        val nodes = LabTreeBuilder.build(sample())
        assertEquals(listOf("Config"), LabTreeBuilder.visible(nodes, emptySet()).map { it.name })
    }

    @Test
    fun `expanding a root reveals only its direct children`() {
        val nodes = LabTreeBuilder.build(sample())
        val visible = LabTreeBuilder.visible(nodes, setOf(LabPath(LabRoot.CONFIG_GLOBAL)))
        assertEquals(listOf("Config", "a", "nested", "arr", "nul", "flag"), visible.map { it.name })
        assertTrue((visible.first() as LabNode.Container).expanded)
        assertFalse((visible.single { it.name == "nested" } as LabNode.Container).expanded)
    }

    @Test
    fun `expanding a grandchild without its parent reveals nothing extra`() {
        val nodes = LabTreeBuilder.build(sample())
        val deep = LabPath(LabRoot.CONFIG_GLOBAL, listOf("nested"))
        assertEquals(listOf("Config"), LabTreeBuilder.visible(nodes, setOf(deep)).map { it.name })
    }

    @Test
    fun `expanding both levels reveals the grandchild`() {
        val nodes = LabTreeBuilder.build(sample())
        val expanded = setOf(
            LabPath(LabRoot.CONFIG_GLOBAL),
            LabPath(LabRoot.CONFIG_GLOBAL, listOf("nested")),
        )
        assertEquals(
            listOf("Config", "a", "nested", "b", "arr", "nul", "flag"),
            LabTreeBuilder.visible(nodes, expanded).map { it.name },
        )
    }

    @Test
    fun `an expanded sibling after a collapsed one still shows its children`() {
        // Pins the cutoff reset in visible(): without it the cutoff only ever lowers, so `arr` - which
        // follows the collapsed `nested` at the same depth - would silently lose its elements.
        val nodes = LabTreeBuilder.build(sample())
        val expanded = setOf(
            LabPath(LabRoot.CONFIG_GLOBAL),
            LabPath(LabRoot.CONFIG_GLOBAL, listOf("arr")),
        )
        assertEquals(
            listOf("Config", "a", "nested", "arr", "0", "1", "nul", "flag"),
            LabTreeBuilder.visible(nodes, expanded).map { it.name },
        )
    }

    @Test
    fun `a preview longer than the limit is clipped, not just non-empty`() {
        // JsonObjectViewHolder.kt@53a07964:44 was `.take(200)`; dropping it serialises a whole Profile
        // into one row. LabCookieFormatterTest pins the analogous 40-char clip.
        val big = JsonObject().apply { repeat(40) { addProperty("key$it", "value$it") } }
        val root = LabTreeBuilder.build(mapOf(LabRoot.PROFILE to big)).first() as LabNode.Container
        val preview = assertNotNull(root.preview)
        assertEquals(200, preview.length)
        assertTrue(preview.startsWith("{\"key0\":\"value0\""))
    }

    @Test
    fun `stableId separates segment lists that a naive join would collide`() {
        // A JSON key may legally contain "/", and LazyColumn throws on a duplicate key - so the id
        // has to be injective, not merely "usually different". Counting segments is not enough.
        assertNotEquals(
            LabPath(LabRoot.CONFIG_GLOBAL, listOf("a/b", "c")).stableId,
            LabPath(LabRoot.CONFIG_GLOBAL, listOf("a", "b/c")).stableId,
        )
        assertNotEquals(LabPath(LabRoot.PROFILE).stableId, LabPath(LabRoot.CONFIG_GLOBAL).stableId)
    }

    @Test
    fun `an empty root is a container with no children`() {
        val nodes = LabTreeBuilder.build(mapOf(LabRoot.PROFILE to JsonObject()))
        assertEquals(1, nodes.size)
        assertEquals("0 elements", (nodes.single() as LabNode.Container).summary)
    }
}
