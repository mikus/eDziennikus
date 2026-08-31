/*
 * Copyright (c) Mikolaj Olszewski 2026-8-7.
 */

package eu.mikus.edziennik

import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.ui.base.ScreenAction
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetPrimaryItem
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetSeparatorItem

class ScreenChromeMappingTest {

    private val icon = CommunityMaterial.Icon.cmd_cog_outline

    private fun action(
        titleRes: Int,
        descriptionRes: Int? = null,
        separatorBefore: Boolean = false,
        onClick: () -> Unit = {},
    ) = ScreenAction(titleRes, icon, descriptionRes, separatorBefore, onClick)

    @Test
    fun `preserves row order`() {
        val items = listOf(action(1), action(2), action(3)).toBottomSheetItems {}
        assertEquals(listOf(1, 2, 3), items.map { (it as BottomSheetPrimaryItem).titleRes })
    }

    @Test
    fun `inserts the separator immediately before its flagged row`() {
        val items = listOf(action(1), action(2, separatorBefore = true)).toBottomSheetItems {}
        assertEquals(3, items.size)
        assertEquals(1, (items[0] as BottomSheetPrimaryItem).titleRes)
        assertTrue(items[1] is BottomSheetSeparatorItem, "separator must precede its row, not follow it")
        assertEquals(2, (items[2] as BottomSheetPrimaryItem).titleRes)
    }

    @Test
    fun `applies descriptionRes only when non-null`() {
        val items = listOf(action(1, descriptionRes = 99), action(2)).toBottomSheetItems {}
        assertEquals(99, (items[0] as BottomSheetPrimaryItem).descriptionRes)
        assertNull((items[1] as BottomSheetPrimaryItem).descriptionRes)
    }

    /**
     * Load-bearing: `isContextual` is the entire contract behind `navigateImpl`'s
     * `removeAllContextual()`. Inverted one way, a screen's rows survive onto the next screen;
     * the other way, the shell's own Sync row gets wiped. Nothing else in this phase checks it.
     */
    @Test
    fun `marks every produced item contextual, separators included`() {
        val items = listOf(action(1), action(2, separatorBefore = true), action(3))
            .toBottomSheetItems {}
        assertEquals(4, items.size)
        assertTrue(items.all { it.isContextual }, "every produced item must be contextual")
    }

    @Test
    fun `routes the click through the host callback with the originating action`() {
        val clicked = mutableListOf<Int>()
        val actions = listOf(action(1), action(2))
        val items = actions.toBottomSheetItems { clicked += it.titleRes }

        (items[1] as BottomSheetPrimaryItem).onClickListener!!.onClick(null)

        assertEquals(listOf(2), clicked, "the host callback must receive the action that was tapped")
    }

    /**
     * `BottomSheetPrimaryItem` carries BOTH `iconicsIcon: IIcon` and `icon: ImageHolder`, and
     * `ImageHolder` is in scope where the mapper lives. Writing the wrong one would drag the
     * MaterialDrawer-derived ImageHolder into the seam, which a later phase is meant to remove.
     */
    @Test
    fun `routes the icon to iconicsIcon, never the ImageHolder icon`() {
        val item = listOf(action(1)).toBottomSheetItems {}[0] as BottomSheetPrimaryItem
        assertEquals(icon, item.iconicsIcon)
        assertNull(item.icon)
    }

    @Test
    fun `maps an empty list to an empty list`() {
        assertEquals(emptyList(), emptyList<ScreenAction>().toBottomSheetItems {})
    }
}
