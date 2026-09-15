/*
 * Copyright (c) Mikolaj Olszewski 2026-9-15.
 */

package eu.mikus.edziennik.ui.base.enums

import eu.mikus.edziennik.data.db.converter.ConverterEnums
import eu.mikus.edziennik.ext.asNavTarget
import eu.mikus.edziennik.ext.asNavTargetOrNull
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * What a *retired* `NavTarget` id decodes to. Phase 40 removed `PROFILE_MANAGER` (203) and `DEBUG`
 * (102); either id can already sit in a user's Room rows and config values.
 *
 * Most of this pins behaviour that is already correct and closes no gap — the `OrNull` decoders were
 * always tolerant, and no decoder line changes in this phase. Say so rather than letting the file
 * read as hardening. The one row that is not pre-existing behaviour is
 * `a retired id thrown at the non-null decoder throws`, which makes the throw a decision instead of an
 * accident: `ext/EnumExtensions.kt:16` uses `first { }`, and `config/DelegateConfig.kt:147` reaches it
 * with no try/catch on every read of a `Set<NavTarget>` config field (`config/ConfigUI.kt:19`
 * `miniMenuButtons`).
 *
 * **One tolerant site is named here and deliberately not asserted:**
 * `config/utils/AppConfigMigrationV3.kt:26`, `.mapNotNull { it.toIntOrNull().asNavTargetOrNull() }`.
 * Its whole `init` runs the v3 migration against a live `Config` and a `SharedPreferences`, so there
 * is no seam to test through; and transcribing the expression into an assertion here would pin
 * `mapNotNull`, not that file — it would stay green if that file were deleted. `IntentPolicyTest` names
 * its uncoverable path the same way rather than manufacturing a test for it.
 */
class NavTargetIdsTest {

    @Test
    fun `the retired PROFILE_MANAGER id no longer resolves`() {
        assertNull(203.asNavTargetOrNull())
    }

    @Test
    fun `the retired DEBUG id no longer resolves`() {
        assertNull(102.asNavTargetOrNull())
    }

    @Test
    fun `a live id still resolves`() {
        // Anti-vacuity: a decoder that answered null for everything would satisfy the test above.
        assertEquals(NavTarget.PROFILE_MARK_AS_READ, 204.asNavTargetOrNull())
    }

    @Test
    fun `the Room converter falls back to HOME for a retired id`() {
        // data/db/converter/ConverterEnums.kt:59 - `?: NavTarget.HOME`, so a stored id is non-null.
        assertEquals(NavTarget.HOME, ConverterEnums().toNavTarget(203))
        assertEquals(NavTarget.HOME, ConverterEnums().toNavTarget(102))
        assertEquals(NavTarget.PROFILE_MARK_AS_READ, ConverterEnums().toNavTarget(204))
    }

    @Test
    fun `a retired id thrown at the non-null decoder throws`() {
        // ext/EnumExtensions.kt:16 - `first { }`. This is the only row here that is not pre-existing
        // tolerance: it pins the crash so a future reader sees it was chosen.
        assertFailsWith<NoSuchElementException> { 203.asNavTarget() }
        assertFailsWith<NoSuchElementException> { 102.asNavTarget() }
    }
}
