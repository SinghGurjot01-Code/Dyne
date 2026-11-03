package com.dyne.integrations.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.dyne.events.BtKind
import com.dyne.events.EventType
import com.dyne.events.IslandEvent
import com.dyne.events.IslandEventManager
import com.dyne.integrations.Integration
import com.dyne.permissions.PermissionManager

/**
 * Adapter on/off works without extra permission. Connection events and device names need BLUETOOTH_CONNECT
 * on Android 12+. Only connections that happen while Dyne is running are reported.
 */
class BluetoothMonitor(
    private val ctx: Context,
    private val events: IslandEventManager,
    private val perms: PermissionManager
) : Integration {
    private val adapter: BluetoothAdapter? = ctx.getSystemService(BluetoothManager::class.java)?.adapter
    private val lastSeen = HashMap<String, Pair<Boolean, Long>>()

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> when (i.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                    BluetoothAdapter.STATE_ON -> events.flash(IslandEvent.Bluetooth(BtKind.ON, null), 2500)
                    BluetoothAdapter.STATE_OFF -> events.flash(IslandEvent.Bluetooth(BtKind.OFF, null), 2500)
                }
                BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val dev = IntentCompat.getParcelableExtra(i, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
                    val connected = i.action == BluetoothDevice.ACTION_ACL_CONNECTED
                    val now = SystemClock.elapsedRealtime()
                    val prev = lastSeen[dev.address]
                    if (prev != null && prev.first == connected && now - prev.second < 1500) return // debounce
                    lastSeen[dev.address] = connected to now
                    val name = if (perms.hasBluetoothConnect()) runCatching { dev.name }.getOrNull() else null
                    events.flash(
                        IslandEvent.Bluetooth(if (connected) BtKind.CONNECTED else BtKind.DISCONNECTED, name), 3500
                    )
                }
            }
        }
    }

    override fun start() {
        if (adapter == null) return // no Bluetooth hardware
        val f = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (perms.hasBluetoothConnect()) {
            f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        ContextCompat.registerReceiver(ctx, receiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun stop() {
        runCatching { ctx.unregisterReceiver(receiver) }
        lastSeen.clear()
        events.clearFlash(EventType.BLUETOOTH)
    }
}
