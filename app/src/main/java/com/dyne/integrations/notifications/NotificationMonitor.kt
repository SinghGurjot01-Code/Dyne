package com.dyne.integrations.notifications

import android.app.KeyguardManager
import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import com.dyne.events.EventType
import com.dyne.events.IslandEvent
import com.dyne.events.IslandEventManager
import com.dyne.integrations.Integration
import com.dyne.settings.SettingsRepository

/** Filters notifications delivered by [DyneNotificationListener]. Nothing is stored or transmitted. */
class NotificationMonitor(
    private val ctx: Context,
    private val events: IslandEventManager,
    private val settings: SettingsRepository
) : Integration {
    @Volatile private var active = false
    private val seen = LinkedHashMap<String, Int>()
    private val ignoredCategories = setOf(
        Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_CALL, Notification.CATEGORY_PROGRESS,
        Notification.CATEGORY_SERVICE, Notification.CATEGORY_SYSTEM, Notification.CATEGORY_STATUS
    )

    override fun start() { active = true }
    override fun stop() {
        active = false; seen.clear(); events.clearFlash(EventType.NOTIFICATION)
    }

    fun onPosted(sbn: StatusBarNotification) {
        if (!active) return
        val pkg = sbn.packageName
        if (pkg == ctx.packageName || pkg == "android" || pkg == "com.android.systemui") return
        if (pkg in settings.current.blockedPackages) return
        val n = sbn.notification
        if (n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_FOREGROUND_SERVICE) != 0) return
        if (n.category in ignoredCategories) return

        var title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        var text = (n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: n.extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val appName = runCatching {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)

        // Same privacy behaviour as the lock screen.
        val locked = ctx.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        if (locked) {
            if (n.visibility == Notification.VISIBILITY_SECRET) return
            if (n.visibility == Notification.VISIBILITY_PRIVATE) { title = appName; text = "New notification" }
        }

        val hash = (title + "\u0000" + text).hashCode()
        if (seen[sbn.key] == hash) return // duplicate/update with identical content
        seen[sbn.key] = hash
        if (seen.size > 100) seen.remove(seen.keys.first())

        events.flash(
            IslandEvent.Notification(sbn.key, pkg, appName, title, text, sbn.postTime, n.contentIntent),
            settings.current.timeoutSec * 1000L
        )
    }

    fun onRemoved(sbn: StatusBarNotification) {
        seen.remove(sbn.key)
        val shown = events.active.value.firstOrNull { it is IslandEvent.Notification && it.key == sbn.key }
        if (shown != null) events.clearFlash(EventType.NOTIFICATION)
    }

    /** Dismiss from the shade too, where Android permits it. */
    fun dismiss(key: String) {
        runCatching { DyneNotificationListener.instance?.cancelNotification(key) }
        events.clearFlash(EventType.NOTIFICATION)
    }
}
