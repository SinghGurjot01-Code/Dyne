package com.dyne.events

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExpandRequest(val event: IslandEvent, val sticky: Boolean)

/**
 * Holds two kinds of events:
 *  - persistent (calls, timer, music, torch): live until their source clears them
 *  - flashes (timer done, charging, notification, bluetooth, volume): shown briefly, then expire
 *
 * Display rule: an incoming/ongoing call always wins. Otherwise the most recent unexpired flash is shown
 * (so a notification is visible even while music plays), then the persistent event with the highest priority.
 * Everything is driven by events — no polling.
 */
class IslandEventManager(private val scope: CoroutineScope) {
    private data class Flash(val event: IslandEvent, val seq: Long)

    private val persistent = MutableStateFlow<Map<EventType, IslandEvent>>(emptyMap())
    private val flashes = MutableStateFlow<Map<EventType, Flash>>(emptyMap())
    private val timers = HashMap<EventType, Job>()
    private var seq = 0L

    private val _expand = MutableSharedFlow<ExpandRequest>(
        extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val expandRequests: SharedFlow<ExpandRequest> = _expand

    /** All active events, highest priority first. */
    val active: StateFlow<List<IslandEvent>> = combine(persistent, flashes) { p, f ->
        (p.values + f.values.map { it.event }).sortedByDescending { it.type.priority }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** The event the island should show right now. */
    val display: StateFlow<IslandEvent?> = combine(persistent, flashes) { p, f ->
        p[EventType.CALL_INCOMING] ?: p[EventType.CALL_ACTIVE]
            ?: f.values.maxByOrNull { it.seq }?.event
            ?: p.values.maxByOrNull { it.type.priority }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    fun setPersistent(event: IslandEvent, expand: Boolean = false, sticky: Boolean = false) {
        persistent.update { it + (event.type to event) }
        if (expand) _expand.tryEmit(ExpandRequest(event, sticky))
    }

    fun clear(type: EventType) {
        persistent.update { it - type }
    }

    fun flash(event: IslandEvent, durationMs: Long, expand: Boolean = true) {
        val s = ++seq
        val type = event.type
        flashes.update { it + (type to Flash(event, s)) }
        timers.remove(type)?.cancel()
        timers[type] = scope.launch {
            delay(durationMs)
            flashes.update { m -> if (m[type]?.seq == s) m - type else m }
        }
        if (expand) _expand.tryEmit(ExpandRequest(event, false))
    }

    /** Update a flash's content without restarting its timer or re-expanding. */
    fun updateFlash(event: IslandEvent) {
        flashes.update { m -> m[event.type]?.let { m + (event.type to it.copy(event = event)) } ?: m }
    }

    fun clearFlash(type: EventType) {
        timers.remove(type)?.cancel()
        flashes.update { it - type }
    }
}
