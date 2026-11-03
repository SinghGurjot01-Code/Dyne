package com.dyne

import android.app.Application
import android.content.Context
import com.dyne.events.IslandEventManager
import com.dyne.integrations.bluetooth.BluetoothMonitor
import com.dyne.integrations.calls.CallMonitor
import com.dyne.integrations.charging.ChargingMonitor
import com.dyne.integrations.music.MusicMonitor
import com.dyne.integrations.notifications.NotificationMonitor
import com.dyne.integrations.timer.TimerController
import com.dyne.integrations.torch.TorchController
import com.dyne.integrations.volume.VolumeMonitor
import com.dyne.permissions.PermissionManager
import com.dyne.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

class DyneApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** Bumped whenever a permission/listener state may have changed. */
    val permissionRefresh = MutableStateFlow(0)

    lateinit var settings: SettingsRepository
    lateinit var permissions: PermissionManager
    lateinit var events: IslandEventManager
    lateinit var music: MusicMonitor
    lateinit var notifications: NotificationMonitor
    lateinit var charging: ChargingMonitor
    lateinit var calls: CallMonitor
    lateinit var timer: TimerController
    lateinit var torch: TorchController
    lateinit var volume: VolumeMonitor
    lateinit var bluetooth: BluetoothMonitor

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        permissions = PermissionManager(this)
        events = IslandEventManager(appScope)
        music = MusicMonitor(this, events)
        notifications = NotificationMonitor(this, events, settings)
        charging = ChargingMonitor(this, events)
        calls = CallMonitor(this, events)
        timer = TimerController(this, events)
        torch = TorchController(this, events)
        volume = VolumeMonitor(this, events)
        bluetooth = BluetoothMonitor(this, events, permissions)
    }
}

val Context.dyne: DyneApplication get() = applicationContext as DyneApplication
