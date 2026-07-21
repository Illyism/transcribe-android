package com.illyism.transcribe.ui.nav

import androidx.navigation3.runtime.NavKey

/**
 * Handles navigation events (forward and back) by updating the navigation state.
 */
class Navigator(val state: NavigationState) {
    fun navigate(route: NavKey) {
        if (route in state.backStacks.keys) {
            // Top-level tab — switch to it (preserving its stack).
            state.topLevelRoute = route
        } else {
            state.backStacks[state.topLevelRoute]?.add(route)
        }
    }

    fun goBack() {
        val currentStack = state.backStacks[state.topLevelRoute]
            ?: error("Stack for ${state.topLevelRoute} not found")
        val currentRoute = currentStack.last()

        // If we're at the base of the current tab, go back to the start (Home) stack.
        if (currentRoute == state.topLevelRoute) {
            state.topLevelRoute = state.startRoute
        } else {
            currentStack.removeLastOrNull()
        }
    }

    /** Clear the current tab stack down to its root key. */
    fun clearToRoot() {
        val stack = state.backStacks[state.topLevelRoute] ?: return
        val root = state.topLevelRoute
        stack.clear()
        stack.add(root)
    }

    /** Replace the top of the current stack, or push if only the root is present. */
    fun replaceTop(route: NavKey) {
        val stack = state.backStacks[state.topLevelRoute] ?: return
        if (stack.size <= 1) {
            stack.add(route)
        } else {
            stack[stack.lastIndex] = route
        }
    }

    /** Pop until [predicate] matches the top (keeps the match). */
    fun popTo(predicate: (NavKey) -> Boolean): Boolean {
        val stack = state.backStacks[state.topLevelRoute] ?: return false
        val idx = stack.indexOfLast(predicate)
        if (idx < 0) return false
        while (stack.lastIndex > idx) {
            stack.removeLastOrNull()
        }
        return true
    }

    fun currentKey(): NavKey? =
        state.backStacks[state.topLevelRoute]?.lastOrNull()
}
