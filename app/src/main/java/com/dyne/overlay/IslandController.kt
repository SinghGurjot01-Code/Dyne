package com.dyne.overlay

import com.dyne.events.EventType
import com.dyne.events.ExpandRequest
import com.dyne.events.IslandEvent
import com.dyne.events.IslandEventManager
import com.dyne.settings.DyneSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** UI state machine: compact <-> expanded, pinned event, auto-collapse. */
class IslandController(
    private val scope: CoroutineScope,
    private val events: IslandEventManager,
    private val settings: StateFlow<DyneSettings>
) {
    private val _expanded = MutableStateFlow(false)
    val expanded = _expanded.asStateFlow()
    private val pinned = MutableStateFlow<EventType?>(null)
    private var collapseJob: Job? = null
    private var sticky = false

    val display: StateFlow<IslandEvent?> = combine(events.display, events.active, pinned) { top, active, pin ->
        if (pin != null) active.firstOrNull { it.type == pin } ?: top else top
    }.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch { events.expandRequests.collect { onExpandRequest(it) } }
        scope.launch {
            display.collect { e ->
                if (sticky && e?.type != EventType.CALL_INCOMING) { sticky = false; scheduleCollapse(1200) }
            }
        }
    }

    private fun onExpandRequest(r: ExpandRequest) {
        if (!settings.value.overlayEnabled) return
        pinned.value = null
        _expanded.value = true
        if (r.sticky) { sticky = true; collapseJob?.cancel() }
        else if (!sticky) scheduleCollapse(settings.value.timeoutSec * 1000L)
    }

    fun expandManual() {
        pinned.value = null
        _expanded.value = true
        if (!sticky) scheduleCollapse(maxOf(settings.value.timeoutSec * 2, 6) * 1000L)
    }

    /** Any interaction with the expanded island postpones auto-collapse. */
    fun interact() {
        if (_expanded.value && !sticky) scheduleCollapse(maxOf(settings.value.timeoutSec * 2, 6) * 1000L)
    }

    fun collapse() {
        collapseJob?.cancel(); sticky = false
        _expanded.value = false
        pinned.value = null
    }

    /** Swipe up: drop a temporary event and collapse. */
    fun dismiss() {
        display.value?.type?.let {
            if (it == EventType.NOTIFICATION || it == EventType.CHARGING || it == EventType.BLUETOOTH ||
                it == EventType.VOLUME || it == EventType.TIMER_DONE
            ) events.clearFlash(it)
        }
        collapse()
    }

    /** Swipe sideways: cycle through simultaneously active events. */
    fun cycle(dir: Int) {
        val list = events.active.value
        if (list.size <= 1) return
        val idx = list.indexOfFirst { it.type == display.value?.type }.coerceAtLeast(0)
        pinned.value = list[(idx + dir + list.size) % list.size].type
        interact()
    }

    private fun scheduleCollapse(ms: Long) {
        collapseJob?.cancel()
        collapseJob = scope.launch { delay(ms); collapse() }
    }
}
