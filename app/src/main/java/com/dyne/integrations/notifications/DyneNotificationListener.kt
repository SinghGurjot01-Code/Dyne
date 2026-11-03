package com.dyne.integrations.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.dyne.dyne
import kotlinx.coroutines.flow.update

class DyneNotificationListener : NotificationListenerService() {
    companion object {
        @Volatile var instance: DyneNotificationListener? = null
    }

    override fun onListenerConnected() {
        instance = this
        dyne.permissionRefresh.update { it + 1 }
    }

    override fun onListenerDisconnected() {
        instance = null
        dyne.permissionRefresh.update { it + 1 }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        runCatching { dyne.notifications.onPosted(sbn) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        runCatching { dyne.notifications.onRemoved(sbn) }
    }
}
