/*
 * Copyright (c) Mikolaj Olszewski 2026-9-15.
 */

package eu.mikus.edziennik.ui.lab

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import eu.mikus.edziennik.config.BaseConfig
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A `BaseConfig` with no database behind it. `entries = emptyList()` skips the DAO read in `init`. */
private class FakeConfig(profileId: Int?) :
    BaseConfig(db = mockk(relaxed = true), profileId = profileId, entries = emptyList())

/** A plain object with fields, standing in for `Profile` / `LoginStore` on the reflective arm. */
private class Owner {
    @JvmField var count: Int = 7
    @JvmField var label: String = "hi"
    @JvmField var nested: Owner? = null
}

class LabPathResolverTest {

    private val globalConfig = FakeConfig(null).apply { values["theme"] = "1" }
    private val profileConfig = FakeConfig(4).apply { values["theme"] = "2" }
    private val studentData = JsonObject().apply {
        addProperty("token", "abc")
        add("list", JsonArray().apply { add(1) })
        add("nul", JsonNull.INSTANCE)
    }
    private val owner = Owner()

    private fun roots() = mapOf(
        LabRoot.PROFILE to owner,
        LabRoot.PROFILE_STUDENT_DATA to studentData,
        LabRoot.LOGIN_STORE to null,
        LabRoot.LOGIN_STORE_DATA to JsonObject(),
        LabRoot.CONFIG_GLOBAL to globalConfig,
        LabRoot.CONFIG_PROFILE to profileConfig,
    )

    private fun resolve(root: LabRoot, vararg segments: String) =
        LabPathResolver.resolve(roots(), LabPath(root, segments.toList()))

    @Test
    fun `a config path carries the config it came from, not a shared map`() {
        // The whole point of design D4: both owners are HashMap<String, String?>, so only identity
        // can tell "Config" from "Config (profile)".
        val global = resolve(LabRoot.CONFIG_GLOBAL, "theme").getOrThrow()
        val profile = resolve(LabRoot.CONFIG_PROFILE, "theme").getOrThrow()

        assertEquals(LabRoot.CONFIG_GLOBAL, global.root)
        assertEquals(LabRoot.CONFIG_PROFILE, profile.root)
        assertSame(globalConfig, assertIs<LabTarget.ConfigEntry>(global.target).owner)
        assertSame(profileConfig, assertIs<LabTarget.ConfigEntry>(profile.target).owner)
        assertEquals(globalConfig.values::class.java, profileConfig.values::class.java)  // why identity is needed
    }

    @Test
    fun `a JSON primitive under an object resolves to a JsonLeaf carrying its parent`() {
        val target = assertIs<LabTarget.JsonLeaf>(resolve(LabRoot.PROFILE_STUDENT_DATA, "token").getOrThrow().target)
        assertSame(studentData, target.parent)
        assertEquals("token", target.name)
        assertEquals("abc", target.old.asString)
    }

    @Test
    fun `a JsonNull leaf is Unsupported, not a ClassCastException`() {
        // LabProfileFragment.kt@53a07964:110 did `objVal as JsonPrimitive` inside the uncovered listener.
        val target = assertIs<LabTarget.Unsupported>(resolve(LabRoot.PROFILE_STUDENT_DATA, "nul").getOrThrow().target)
        assertTrue(target.reason.isNotBlank())
    }

    @Test
    fun `an array element is Unsupported, and the arm is reachable`() {
        // LabJsonAdapter.kt@53a07964:64 indexes arrays into clickable rows, so this path really is reached.
        // Without the arm it falls to reflection and runs getDeclaredField("0") on a JsonArray.
        val target = assertIs<LabTarget.Unsupported>(
            resolve(LabRoot.PROFILE_STUDENT_DATA, "list", "0").getOrThrow().target,
        )
        assertTrue(target.reason.isNotBlank())
    }

    @Test
    fun `a reflected field resolves to its owner and current value`() {
        val target = assertIs<LabTarget.Field>(resolve(LabRoot.PROFILE, "count").getOrThrow().target)
        assertSame(owner, target.owner)
        assertEquals("count", target.name)
        assertEquals(7, target.old)
    }

    @Test
    fun `an absent root fails instead of resolving`() {
        assertTrue(resolve(LabRoot.LOGIN_STORE, "id").isFailure)
    }

    @Test
    fun `a missing field fails instead of throwing at the caller`() {
        assertTrue(resolve(LabRoot.PROFILE, "nope").isFailure)
        assertTrue(resolve(LabRoot.PROFILE_STUDENT_DATA, "nope").isFailure)
    }

    @Test
    fun `a null intermediate fails instead of descending into it`() {
        assertTrue(resolve(LabRoot.PROFILE, "nested", "count").isFailure)
    }

    @Test
    fun `a root itself is not editable`() {
        assertIs<LabTarget.Unsupported>(resolve(LabRoot.CONFIG_GLOBAL).getOrThrow().target)
    }
}
