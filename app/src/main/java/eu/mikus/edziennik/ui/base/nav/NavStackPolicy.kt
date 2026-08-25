/*
 * Copyright (c) Mikolaj Olszewski 2026-8-7.
 */
package eu.mikus.edziennik.ui.base.nav

import android.os.Bundle
import eu.mikus.edziennik.ui.base.enums.NavTarget

/** How the stack moved, which is also what picks the transaction's animation pair. */
enum class NavTransition { RELOAD, POP, PUSH }

/**
 * The whole navigation decision, computed before anything is mutated.
 *
 * [stack] never aliases the caller's mutable back stack and is never a subList view of it - the
 * caller clears its own list before copying this one into it, and would otherwise wipe the
 * decision. (The one instance this can return unchanged is the shared `EmptyList` singleton, when
 * an empty list is passed in; that is not a MutableList, so it can never be the caller's.)
 *
 * [arguments] is the resolved-or-null argument bundle for the target: null means "the caller
 * supplies an empty Bundle". Constructing one is an Android call, so it stays at the call site.
 */
data class NavDecision(
    val transition: NavTransition,
    val stack: List<Pair<NavTarget, Bundle?>>,
    val target: NavTarget,
    val arguments: Bundle?,
)

/**
 * The back-stack arithmetic that used to live inline in MainActivity.navigateImpl.
 *
 * Three quirks are preserved deliberately:
 * - RELOAD leaves the current target alone; the caller must also leave navArguments alone.
 * - [arguments] resolves through `firstOrNull` (the OLDEST matching entry) while the pop decision
 *   uses `lastIndexOf` (the NEWEST), and resolution reads the stack before any popping.
 * - The pop-to-root truncation fires only for `popTo == HOME`, so NavTarget.MESSAGE
 *   (popTo = MESSAGES) does not truncate, even though popBackStack honours any non-null popTo.
 */
fun decideNavigation(
    current: NavTarget,
    currentArguments: Bundle?,
    stack: List<Pair<NavTarget, Bundle?>>,
    target: NavTarget,
    requestedArguments: Bundle?,
): NavDecision {
    // resolved against the stack as it is BEFORE any popping, oldest match first
    val arguments = requestedArguments ?: stack.firstOrNull { it.first == target }?.second

    val transition: NavTransition
    var newStack: List<Pair<NavTarget, Bundle?>>
    val newTarget: NavTarget

    if (target == current) {
        transition = NavTransition.RELOAD
        newStack = stack.toList()
        newTarget = current
    } else {
        val index = stack.indexOfLast { it.first == target }
        if (index == -1) {
            transition = NavTransition.PUSH
            newStack = stack + (current to currentArguments)
        } else {
            // popCount was size - index, removed from the end, which leaves exactly index entries
            transition = NavTransition.POP
            newStack = stack.take(index)
        }
        newTarget = target
    }

    // applies to all three transitions, and is a no-op on a stack of 0 or 1
    if (target.popTo == NavTarget.HOME && newStack.size > 1)
        newStack = newStack.take(1)

    return NavDecision(
        transition = transition,
        stack = newStack,
        target = newTarget,
        arguments = arguments,
    )
}
