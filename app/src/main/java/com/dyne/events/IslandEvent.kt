package com.dyne.events

import android.app.PendingIntent
import android.graphics.Bitmap
import android.media.session.MediaController

/** Higher priority wins. Calls always win; see IslandEventManager for transient-event behaviour. */
enum class EventType(val priority: Int) {
    CALL_INCOMING(100), CALL_ACTIVE(90), TIMER_DONE(85), TIMER(80), MUSIC(70),
    TORCH(60), CHARGING(50), NOTIFICATION(40), BLUETOOTH(30), VOLUME(20)
}

enum class ChargeState { CONNECTED, DISCONNECTED, FULL }
enum class BtKind { CONNECTED, DISCONNECTED, ON, OFF }

sealed interface IslandEvent {
    val type: EventType

    data class Call(val ringing: Boolean, val startedAt: Long) : IslandEvent {
        override val type: EventType get() = if (ringing) EventType.CALL_INCOMING else EventType.CALL_ACTIVE
    }

    data class Music(
        val title: String, val artist: String, val art: Bitmap?, val playing: Boolean,
        val positionMs: Long, val durationMs: Long, val updatedAt: Long, val speed: Float,
        val canNext: Boolean, val canPrev: Boolean, val controller: MediaController
    ) : IslandEvent {
        override val type: EventType get() = EventType.MUSIC
    }

    data class Notification(
        val key: String, val pkg: String, val appName: String,
        val title: String, val text: String, val postedAt: Long, val intent: PendingIntent?
    ) : IslandEvent {
        override val type: EventType get() = EventType.NOTIFICATION
    }

    data class Charging(val percent: Int, val state: ChargeState, val source: String?) : IslandEvent {
        override val type: EventType get() = EventType.CHARGING
    }

    data class Timer(val totalMs: Long, val running: Boolean, val remainingMs: Long, val endElapsed: Long) : IslandEvent {
        override val type: EventType get() = EventType.TIMER
    }

    data class TimerDone(val totalMs: Long) : IslandEvent {
        override val type: EventType get() = EventType.TIMER_DONE
    }

    object Torch : IslandEvent {
        override val type: EventType get() = EventType.TORCH
    }

    data class Volume(val stream: Int, val level: Int, val max: Int) : IslandEvent {
        override val type: EventType get() = EventType.VOLUME
    }

    data class Bluetooth(val kind: BtKind, val name: String?) : IslandEvent {
        override val type: EventType get() = EventType.BLUETOOTH
    }
}
