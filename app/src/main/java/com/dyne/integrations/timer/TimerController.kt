package com.dyne.integrations.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import com.dyne.MainActivity
import com.dyne.events.EventType
import com.dyne.events.IslandEvent
import com.dyne.events.IslandEventManager
import com.dyne.integrations.Integration
import kotlin.math.abs

/**
 * Timer state is stored as an absolute end time on both clocks:
 *  - elapsedRealtime (monotonic, immune to wall-clock changes) while the device stays up
 *  - wall clock, used to recompute after a reboot (elapsedRealtime resets)
 * Completion is delivered by AlarmManager.setAlarmClock, so it fires even if the process was killed.
 */
class TimerController(private val ctx: Context, private val events: IslandEventManager) : Integration {
    private val prefs = ctx.getSharedPreferences("dyne_timer", Context.MODE_PRIVATE)
    private var totalMs = 0L
    private var remainingMs = 0L
    private var running = false
    private var endElapsed = 0L
    private var endWall = 0L
    private var shown = false

    override fun start() { shown = true; restore() }
    override fun stop() { shown = false; events.clear(EventType.TIMER); events.clearFlash(EventType.TIMER_DONE) }

    fun start(durationMs: Long) {
        if (durationMs <= 0) return
        totalMs = durationMs; remainingMs = durationMs
        run(durationMs)
    }

    fun pause() {
        if (!running) return
        remainingMs = (endElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0)
        running = false
        cancelAlarm(); persist(); publish()
    }

    fun resume() {
        if (running || remainingMs <= 0) return
        run(remainingMs)
    }

    fun reset() {
        running = false; remainingMs = 0; totalMs = 0
        cancelAlarm(); persist(); events.clear(EventType.TIMER)
    }

    private fun run(ms: Long) {
        endElapsed = SystemClock.elapsedRealtime() + ms
        endWall = System.currentTimeMillis() + ms
        running = true
        scheduleAlarm(); persist(); publish()
    }

    /** Called by the alarm, or by restore() when the end time already passed. */
    fun onFinished() {
        if (!running) return
        val total = totalMs
        running = false; remainingMs = 0; totalMs = 0
        cancelAlarm(); persist()
        events.clear(EventType.TIMER)
        if (shown) {
            events.flash(IslandEvent.TimerDone(total), 15_000)
            vibrate()
        }
    }

    /** Load saved state; re-arm the alarm (alarms are cleared on reboot). Safe to call repeatedly. */
    private fun load() {
        totalMs = prefs.getLong("total", 0)
        remainingMs = prefs.getLong("remaining", 0)
        running = prefs.getBoolean("running", false)
        endElapsed = prefs.getLong("endElapsed", 0)
        endWall = prefs.getLong("endWall", 0)
    }

    /** Alarm fired: the process may have been killed, so reload state from disk first. */
    fun onAlarm() { load(); onFinished() }

    fun restore() {
        load()
        if (running) {
            val bootWallNow = System.currentTimeMillis() - SystemClock.elapsedRealtime()
            val rebooted = abs(bootWallNow - prefs.getLong("bootWall", bootWallNow)) > 3000
            if (rebooted) endElapsed = SystemClock.elapsedRealtime() + (endWall - System.currentTimeMillis())
            if (endElapsed - SystemClock.elapsedRealtime() <= 0) { onFinished(); return }
            scheduleAlarm(); persist()
        }
        publish()
    }

    private fun publish() {
        if (!shown) return
        if (!running && remainingMs <= 0) { events.clear(EventType.TIMER); return }
        events.setPersistent(IslandEvent.Timer(totalMs, running, remainingMs, endElapsed))
    }

    private fun persist() {
        prefs.edit().putLong("total", totalMs).putLong("remaining", remainingMs).putBoolean("running", running)
            .putLong("endElapsed", endElapsed).putLong("endWall", endWall)
            .putLong("bootWall", System.currentTimeMillis() - SystemClock.elapsedRealtime()).apply()
    }

    private fun alarmIntent() = PendingIntent.getBroadcast(
        ctx, 1, Intent(ctx, TimerAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun scheduleAlarm() {
        val show = PendingIntent.getActivity(ctx, 2, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        runCatching {
            ctx.getSystemService(AlarmManager::class.java)
                .setAlarmClock(AlarmManager.AlarmClockInfo(endWall, show), alarmIntent())
        }
    }

    private fun cancelAlarm() {
        runCatching { ctx.getSystemService(AlarmManager::class.java).cancel(alarmIntent()) }
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        runCatching {
            ctx.getSystemService(Vibrator::class.java)
                .vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300, 150, 300), -1))
        }
    }
}
