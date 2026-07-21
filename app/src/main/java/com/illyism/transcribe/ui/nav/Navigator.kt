package com.illyism.transcribe.ui.nav

import androidx.navigation3.runtime.NavKey

/** Updates [NavigationState] for forward/back and tab switches. */
class Navigator(val state: NavigationState) {
    fun navigate(route: NavKey) {
        if (route in state.backStacks.keys) {
            state.topLevelRoute = route
        } else {
            state.backStacks[state.topLevelRoute]?.add(route)
        }
    }

    fun ensureTopLevel(route: NavKey) {
        if (route in state.backStacks.keys) {
            state.topLevelRoute = route
        }
    }

    fun goBack() {
        val currentStack = state.backStacks[state.topLevelRoute]
            ?: error("Stack for ${state.topLevelRoute} not found")
        val currentRoute = currentStack.last()

        // At tab root → exit through the start (Home) stack.
        if (currentRoute == state.topLevelRoute) {
            state.topLevelRoute = state.startRoute
        } else {
            currentStack.removeLastOrNull()
        }
    }

    fun clearToRoot() {
        val stack = state.backStacks[state.topLevelRoute] ?: return
        val root = state.topLevelRoute
        stack.clear()
        stack.add(root)
    }

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

    /** Home-tab flow: Selected (skip if already mid-job / on a transcript). */
    fun openSelected() {
        ensureTopLevel(AppKey.Home)
        val top = currentKey()
        if (top !is AppKey.Selected &&
            top !is AppKey.Processing &&
            top !is AppKey.TranscriptDetail
        ) {
            navigate(AppKey.Selected)
        }
    }

    /** Home-tab flow: Processing (replace Selected when present). */
    fun openProcessing() {
        ensureTopLevel(AppKey.Home)
        when (currentKey()) {
            is AppKey.Processing -> Unit
            is AppKey.Selected -> replaceTop(AppKey.Processing)
            else -> navigate(AppKey.Processing)
        }
    }

    /** Finished job → Files tab detail so the library chrome stays visible. */
    fun openFinishedTranscript(id: String) {
        openHistoryDetail(id)
    }

    /**
     * Open a transcript from the History list.
     * On wide list-detail layouts, replaces an existing detail instead of stacking.
     */
    fun openHistoryDetail(transcriptId: String) {
        ensureTopLevel(AppKey.History)
        val stack = state.backStacks[AppKey.History] ?: return
        val detail = AppKey.TranscriptDetail(transcriptId)
        // Drop skill/settings overlays until History or an existing detail is on top.
        while (stack.size > 1 &&
            stack.last() !is AppKey.History &&
            stack.last() !is AppKey.TranscriptDetail
        ) {
            stack.removeLastOrNull()
        }
        when (stack.lastOrNull()) {
            is AppKey.TranscriptDetail -> stack[stack.lastIndex] = detail
            else -> stack.add(detail)
        }
    }

    /**
     * Deep link: History → TranscriptDetail, optionally → SkillResults.
     * Back returns through a sensible stack (results → detail → History).
     */
    fun openFromDeepLink(transcriptId: String, skillId: String? = null) {
        openHistoryDetail(transcriptId)
        if (skillId != null) {
            navigate(AppKey.SkillResults(transcriptId, skillId))
        }
    }
}
