package com.dyne.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.dyne.MainActivity
import com.dyne.R
import com.dyne.dyne
import com.dyne.integrations.Integration
import com.dyne.settings.Feature
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DyneOverlayService : LifecycleService(), SavedStateRegistryOwner {

    private val ssrc = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = ssrc.savedStateRegistry

    private var overlay: OverlayWindowManager? = null
    private var controller: IslandController? = null
    private val running = mutableSetOf<Feature>()
    private var attached = false

    private class Entry(val feature: Feature, val integration: Integration, val ready: () -> Boolean)
    private lateinit var entries: List<Entry>

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> { controller?.collapse(); overlay?.screenOn = false }
                Intent.ACTION_SCREEN_ON -> overlay?.screenOn = true
            }
            overlay?.sync()
        }
    }

    override fun onCreate() {
        ssrc.performAttach()
        ssrc.performRestore(null)
        super.onCreate()
        val app = dyne
        val p = app.permissions
        entries = listOf(
            Entry(Feature.MUSIC, app.music) { p.hasNotificationAccess() },
            Entry(Feature.NOTIFICATIONS, app.notifications) { p.hasNotificationAccess() },
            Entry(Feature.CHARGING, app.charging) { true },
            Entry(Feature.CALLS, app.calls) { p.hasPhoneState() },
            Entry(Feature.TIMER, app.timer) { true },
            Entry(Feature.TORCH, app.torch) { true },
            Entry(Feature.VOLUME, app.volume) { true },
            Entry(Feature.BLUETOOTH, app.bluetooth) { true }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startAsForeground() // must happen promptly after startForegroundService()
        if (!dyne.permissions.hasOverlay() || !dyne.settings.current.enabled) {
            stopSelf(); return START_NOT_STICKY
        }
        if (!attached) attach()
        overlay?.sync()
        return START_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("dyne_service", getString(R.string.service_channel), NotificationManager.IMPORTANCE_MIN)
        )
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "dyne_service")
            .setSmallIcon(R.drawable.ic_stat_dyne)
            .setContentTitle(getString(R.string.service_text))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this, 1, n,
            when {
                Build.VERSION.SDK_INT >= 34 -> android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                Build.VERSION.SDK_INT >= 29 -> android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
                else -> 0
            }
        )
    }

    private fun attach() {
        attached = true
        val app = dyne
        val ctl = IslandController(lifecycleScope, app.events, app.settings.flow)
        controller = ctl
        overlay = OverlayWindowManager(this, this, app, ctl, lifecycleScope)
        ContextCompat.registerReceiver(
            this, screenReceiver,
            IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        lifecycleScope.launch {
            combine(app.settings.flow, app.permissionRefresh) { s, _ -> s }.collect { s ->
                if (!s.enabled) { stopSelf(); return@collect }
                reconcile()
                overlay?.sync()
            }
        }
    }

    private fun reconcile() {
        val s = dyne.settings.current
        for (e in entries) {
            val should = e.feature in s.features && e.ready()
            val isRunning = e.feature in running
            if (should && !isRunning) {
                runCatching { e.integration.start() }.onSuccess { running += e.feature }
            } else if (!should && isRunning) {
                runCatching { e.integration.stop() }; running -= e.feature
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.onConfigurationChanged()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        entries.forEach { if (it.feature in running) runCatching { it.integration.stop() } }
        running.clear()
        overlay?.remove()
        super.onDestroy()
    }

    companion object {
        fun start(ctx: Context) {
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, DyneOverlayService::class.java)) }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, DyneOverlayService::class.java))
        }
    }
}
