package com.dyne.permissions

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dyne.integrations.notifications.DyneNotificationListener

class PermissionManager(private val ctx: Context) {

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun hasOverlay() = Settings.canDrawOverlays(ctx)

    fun hasNotificationAccess() =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    /** Required on Android 12+ only; older versions grant it implicitly. */
    fun hasBluetoothConnect() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || granted(Manifest.permission.BLUETOOTH_CONNECT)

    fun hasPhoneState() = granted(Manifest.permission.READ_PHONE_STATE)
    fun hasAnswerCalls() = granted(Manifest.permission.ANSWER_PHONE_CALLS)

    fun hasPostNotifications() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(Manifest.permission.POST_NOTIFICATIONS)

    fun ignoresBatteryOptimizations() =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    fun overlaySettingsIntent() = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")
    ).newTask()

    fun notificationListenerIntent(): Intent {
        val i = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(ctx, DyneNotificationListener::class.java).flattenToString()
            )
        } else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        return i.newTask()
    }

    fun appDetailsIntent() = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")
    ).newTask()

    fun batterySettingsIntent() =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).newTask()

    fun bluetoothPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyArray()

    fun phonePermissions(): Array<String> =
        arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.ANSWER_PHONE_CALLS)

    fun notificationPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()

    private fun Intent.newTask() = addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
