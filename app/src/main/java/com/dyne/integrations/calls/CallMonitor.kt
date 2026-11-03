package com.dyne.integrations.calls

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import com.dyne.events.EventType
import com.dyne.events.IslandEvent
import com.dyne.events.IslandEventManager
import com.dyne.integrations.Integration

/**
 * Call state via TelephonyCallback (API 31+) / PhoneStateListener (older). Requires READ_PHONE_STATE.
 * The caller's number/name is not available without READ_CALL_LOG-level access and is intentionally not shown.
 */
class CallMonitor(private val ctx: Context, private val events: IslandEventManager) : Integration {
    private val tm = ctx.getSystemService(TelephonyManager::class.java)
    private var activeStart = 0L
    private var lastState = TelephonyManager.CALL_STATE_IDLE
    private var legacy: Any? = null
    private var modern: Any? = null

    override fun start() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) = handle(state)
                }
                tm.registerTelephonyCallback(ctx.mainExecutor, cb)
                modern = cb
                handle(tm.callState)
            } else {
                @Suppress("DEPRECATION")
                val l = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) = handle(state)
                }
                @Suppress("DEPRECATION")
                tm.listen(l, PhoneStateListener.LISTEN_CALL_STATE)
                legacy = l
                @Suppress("DEPRECATION")
                handle(tm.callState)
            }
        } catch (_: SecurityException) { /* READ_PHONE_STATE missing */ }
    }

    override fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (modern as? TelephonyCallback)?.let { runCatching { tm.unregisterTelephonyCallback(it) } }
        } else {
            @Suppress("DEPRECATION")
            (legacy as? PhoneStateListener)?.let { runCatching { tm.listen(it, PhoneStateListener.LISTEN_NONE) } }
        }
        modern = null; legacy = null
        events.clear(EventType.CALL_INCOMING); events.clear(EventType.CALL_ACTIVE)
        lastState = TelephonyManager.CALL_STATE_IDLE
    }

    private fun handle(state: Int) {
        if (state == lastState) return
        lastState = state
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                events.clear(EventType.CALL_ACTIVE)
                events.setPersistent(IslandEvent.Call(true, SystemClock.elapsedRealtime()), expand = true, sticky = true)
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                events.clear(EventType.CALL_INCOMING)
                activeStart = SystemClock.elapsedRealtime()
                events.setPersistent(IslandEvent.Call(false, activeStart), expand = true)
            }
            else -> {
                events.clear(EventType.CALL_INCOMING); events.clear(EventType.CALL_ACTIVE)
            }
        }
    }

    /** Only works with ANSWER_PHONE_CALLS granted, and only on explicit user tap. */
    @SuppressLint("MissingPermission")
    fun accept() { runCatching { ctx.getSystemService(TelecomManager::class.java).acceptRingingCall() } }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun endOrDecline() { runCatching { ctx.getSystemService(TelecomManager::class.java).endCall() } }
}
