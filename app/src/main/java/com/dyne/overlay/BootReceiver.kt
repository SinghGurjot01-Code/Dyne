package com.dyne.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dyne.dyne

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.dyne
        runCatching { app.timer.restore() } // re-arm a running timer's alarm (alarms don't survive reboot)
        val s = app.settings.current
        if (s.enabled && s.startOnBoot && app.permissions.hasOverlay()) DyneOverlayService.start(context)
    }
}
