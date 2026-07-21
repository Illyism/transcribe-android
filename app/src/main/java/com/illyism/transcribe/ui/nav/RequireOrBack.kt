package com.illyism.transcribe.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** Composes [content] when [value] is non-null; otherwise pops once. */
@Composable
fun <T : Any> RequireOrBack(
    value: T?,
    goBack: () -> Unit,
    content: @Composable (T) -> Unit
) {
    if (value == null) {
        LaunchedEffect(Unit) { goBack() }
    } else {
        content(value)
    }
}

/** Composes [content] when [ok]; otherwise pops once. */
@Composable
fun RequireOrBack(
    ok: Boolean,
    goBack: () -> Unit,
    content: @Composable () -> Unit
) {
    if (!ok) {
        LaunchedEffect(Unit) { goBack() }
    } else {
        content()
    }
}
