package com.dyne.integrations.volume

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.ContextCompat
import com.dyne.events.EventType
import com.dyne.events.IslandEvent
import com.dyne.events.IslandEventManager
import com.dyne.integrations.Integration

/**
 * Listens for volume changes. There is no public volume-change API; the VOLUME_CHANGED_ACTION broadcast is the
 * widely used (unofficial) mechanism. The island only observes; Android's own volume panel is untouched.
 */
class VolumeMonitor(private val ctx: Context, private val events: IslandEventManager) : Integration {
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val last = HashMap<Int, Int>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val stream = i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
            if (stream != AudioManager.STREAM_MUSIC && stream != AudioManager.STREAM_RING) return
            val level = audio.getStreamVolume(stream)
            if (last[stream] == level) return
            last[stream] = level
            val ev = IslandEvent.Volume(stream, level, audio.getStreamMaxVolume(stream))
            val alreadyShown = events.active.value.any { it.type == EventType.VOLUME }
            events.flash(ev, 2200, expand = !alreadyShown) // re-flash restarts the hide timer
        }
    }

    override fun start() {
        last.clear()
        ContextCompat.registerReceiver(
            ctx, receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"), ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun stop() {
        runCatching { ctx.unregisterReceiver(receiver) }
        events.clearFlash(EventType.VOLUME)
    }
}
