package com.dyne.integrations.charging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.dyne.events.ChargeState
import com.dyne.events.EventType
import com.dyne.events.IslandEvent
import com.dyne.events.IslandEventManager
import com.dyne.integrations.Integration
import kotlinx.coroutines.flow.MutableStateFlow

/** Uses ACTION_BATTERY_CHANGED only. Charging speed/wattage is not exposed reliably, so it is not shown. */
class ChargingMonitor(private val ctx: Context, private val events: IslandEventManager) : Integration {
    val isCharging = MutableStateFlow(false)
    private var lastPlugged: Boolean? = null
    private var lastFull = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = handle(i)
    }

    override fun start() {
        lastPlugged = null; lastFull = false
        val sticky = ContextCompat.registerReceiver(
            ctx, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        sticky?.let { handle(it) }
    }

    override fun stop() {
        runCatching { ctx.unregisterReceiver(receiver) }
        isCharging.value = false
        events.clearFlash(EventType.CHARGING)
    }

    private fun handle(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        val pct = level * 100 / scale
        val plugType = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val plugged = plugType != 0
        val full = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_FULL
        val source = when (plugType) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> null
        }
        isCharging.value = plugged
        val prev = lastPlugged
        lastPlugged = plugged
        val timeout = 4000L
        when {
            prev == null -> lastFull = plugged && full // first reading: stay silent
            plugged && !prev -> {
                lastFull = full
                events.flash(IslandEvent.Charging(pct, ChargeState.CONNECTED, source), timeout)
            }
            !plugged && prev -> {
                lastFull = false
                events.flash(IslandEvent.Charging(pct, ChargeState.DISCONNECTED, null), 2500)
            }
            plugged && full && !lastFull -> {
                lastFull = true
                events.flash(IslandEvent.Charging(pct, ChargeState.FULL, source), 5000)
            }
            plugged -> events.updateFlash(
                IslandEvent.Charging(pct, if (full) ChargeState.FULL else ChargeState.CONNECTED, source)
            )
        }
    }
}
