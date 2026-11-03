package com.dyne.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun <T> StateFlow<T>.collectAsStateCompat(): T {
    val s: State<T> = collectAsState()
    return s.value
}
