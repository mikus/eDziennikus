/*
 * Copyright (c) Mikolaj Olszewski 2026-8-7.
 */
package eu.mikus.edziennik.ui.base.nav

import android.os.Bundle
import eu.mikus.edziennik.ui.base.enums.NavTarget
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.jupiter.api.Test

/**
 * Pins the back-stack semantics that MainActivity.navigateImpl had inline before Phase 25. Every
 * case here is CURRENT behaviour, including the two quirks pinned directly: the
 * firstOrNull/lastIndexOf asymmetry, and the pop-to-root truncation firing only for popTo == HOME.
 *
 * The third preserved quirk - RELOAD not reassigning navArguments - is deliberately NOT pinned here.
 * Under the four-field NavDecision that guard lives at the MainActivity call site (the
 * `if (decision.transition != NavTransition.RELOAD)`), which has no unit test. Do not delete that
 * `if` on the strength of a green suite.
 *
 * Pure Jupiter - no Robolectric. NavTarget loads on plain JVM and Bundle is only ever moved around,
 * never constructed or read, so mockk() identity tokens are enough.
 */
class NavStackPolicyTest {

    private fun bundle() = mockk<Bundle>()

    /** HOME has no popTo, so it never triggers the truncation - the neutral target for stack tests. */
    private val root = NavTarget.HOME

    /** NOTES and SETTINGS declare no popTo, so a chain of them exercises POP without truncation. */
    private val a = NavTarget.NOTES
    private val b = NavTarget.SETTINGS

    // ---- transitions ----

    @Test
    fun `same target reloads, leaving the stack and target alone`() {
        val currentArgs = bundle()
        val stack = listOf<Pair<NavTarget, Bundle?>>(root to null)

        val d = decideNavigation(
            current = a,
            currentArguments = currentArgs,
            stack = stack,
            target = a,
            requestedArguments = bundle(),
        )

        assertEquals(NavTransition.RELOAD, d.transition)
        assertEquals(stack, d.stack)
        assertEquals(a, d.target)
    }

    @Test
    fun `a target absent from the stack is pushed, carrying the current entry`() {
        val currentArgs = bundle()

        val d = decideNavigation(
            current = a,
            currentArguments = currentArgs,
            stack = listOf(root to null),
            target = b,
            requestedArguments = null,
        )

        assertEquals(NavTransition.PUSH, d.transition)
        assertEquals(listOf<Pair<NavTarget, Bundle?>>(root to null, a to currentArgs), d.stack)
        assertEquals(b, d.target)
    }

    @Test
    fun `a target already in the stack pops everything above it`() {
        val d = decideNavigation(
            current = b,
            currentArguments = null,
            stack = listOf(root to null, a to null),
            target = a,
            requestedArguments = null,
        )

        assertEquals(NavTransition.POP, d.transition)
        assertEquals(listOf<Pair<NavTarget, Bundle?>>(root to null), d.stack)
        assertEquals(a, d.target)
    }

    @Test
    fun `a target present twice pops to its LAST occurrence`() {
        val d = decideNavigation(
            current = b,
            currentArguments = null,
            stack = listOf(a to null, root to null, a to null),
            target = a,
            requestedArguments = null,
        )

        assertEquals(NavTransition.POP, d.transition)
        assertEquals(listOf<Pair<NavTarget, Bundle?>>(a to null, root to null), d.stack)
    }

    // ---- pop-to-root truncation ----

    @Test
    fun `a popTo HOME target truncates the stack to one entry`() {
        val d = decideNavigation(
            current = a,
            currentArguments = null,
            stack = listOf(root to null, b to null),
            target = NavTarget.ATTENDANCE,
            requestedArguments = null,
        )

        assertEquals(NavTransition.PUSH, d.transition)
        assertEquals(1, d.stack.size)
        assertEquals(root, d.stack[0].first)
    }

    @Test
    fun `a popTo MESSAGES target does NOT truncate`() {
        // MESSAGE declares popTo = MESSAGES, and the truncation checks == HOME specifically.
        // Simplifying that check to popTo != null would break exactly this case.
        val d = decideNavigation(
            current = a,
            currentArguments = null,
            stack = listOf(root to null, b to null),
            target = NavTarget.MESSAGE,
            requestedArguments = null,
        )

        assertEquals(3, d.stack.size)
        assertEquals(listOf(root, b, a), d.stack.map { it.first })
    }

    @Test
    fun `truncation on an empty stack is a no-op`() {
        // ATTENDANCE declares popTo = HOME, so this reaches the truncation condition - though the
        // `size > 1` half then short-circuits, so the body never runs. Note this case cannot detect
        // the truncation being deleted or relaxed to `popTo != null`: empty-in, empty-out either
        // way. It pins only "an empty stack survives a popTo == HOME target without throwing";
        // tests 5 and 6 are what pin the truncation itself.
        val d = decideNavigation(
            current = NavTarget.ATTENDANCE,
            currentArguments = null,
            stack = emptyList(),
            target = NavTarget.ATTENDANCE,
            requestedArguments = null,
        )

        assertEquals(NavTransition.RELOAD, d.transition)
        assertEquals(emptyList(), d.stack)
    }

    // ---- arguments resolution ----

    @Test
    fun `requested arguments win`() {
        val requested = bundle()
        val stacked = bundle()

        val d = decideNavigation(
            current = a,
            currentArguments = null,
            stack = listOf(b to stacked),
            target = b,
            requestedArguments = requested,
        )

        assertSame(requested, d.arguments)
    }

    @Test
    fun `with no request the FIRST matching stack entry supplies the arguments`() {
        // firstOrNull, deliberately - while the pop decision uses lastIndexOf
        val first = bundle()
        val last = bundle()

        val d = decideNavigation(
            current = a,
            currentArguments = null,
            stack = listOf(b to first, root to null, b to last),
            target = b,
            requestedArguments = null,
        )

        assertSame(first, d.arguments)
    }

    @Test
    fun `arguments are resolved BEFORE the pop removes the entry holding them`() {
        // b appears exactly once, at the index the POP truncates away - take(1) drops it. Resolving
        // AFTER the pop would therefore yield null; resolving before yields x. This is the fixture
        // that makes the ordering inside the policy observable, so the claim is not vacuous.
        val x = bundle()

        val d = decideNavigation(
            current = a,
            currentArguments = null,
            stack = listOf(root to null, b to x),
            target = b,
            requestedArguments = null,
        )

        assertEquals(NavTransition.POP, d.transition)
        assertEquals(listOf<Pair<NavTarget, Bundle?>>(root to null), d.stack)
        assertSame(x, d.arguments)
    }

    @Test
    fun `with no request and no stack entry the arguments are null`() {
        val d = decideNavigation(
            current = a,
            currentArguments = bundle(),
            stack = listOf(root to null),
            target = b,
            requestedArguments = null,
        )

        assertNull(d.arguments)
    }

    // ---- purity ----

    @Test
    fun `the input list is neither mutated nor returned`() {
        // A returned instance - or a subList view - would be wiped by the caller's
        // navBackStack.clear() before addAll(decision.stack). Uses NOTES/SETTINGS (no popTo) so the
        // truncation cannot mask the aliasing by allocating a fresh list anyway.
        val original = mutableListOf<Pair<NavTarget, Bundle?>>(root to null, a to null)
        val snapshot = original.toList()

        val decisions = listOf(
            decideNavigation(a, null, original, a, null),         // RELOAD
            decideNavigation(a, null, original, b, null),         // PUSH (SETTINGS has no popTo)
            decideNavigation(b, null, original, a, null),         // POP
        )
        val sizes = decisions.map { it.stack.size }

        decisions.forEach { d ->
            assertNotSame(original, d.stack)
            assertEquals(snapshot, original)
        }

        // assertNotSame alone cannot catch a subList VIEW - that is a distinct object which also
        // leaves its backing list unmutated, yet the caller's navBackStack.clear() would empty it
        // and wipe the decision. Mutating the input afterwards is the check that actually bites:
        // if take() were ever "optimised" to subList(), these sizes would collapse to 0.
        original.clear()
        assertEquals(sizes, decisions.map { it.stack.size })
    }
}
