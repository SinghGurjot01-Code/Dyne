package com.dyne.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.dyne.DyneApplication
import com.dyne.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private data class Tab(val label: String, val icon: ImageVector)

@Composable
fun SettingsApp(app: DyneApplication, requestPermissions: (Array<String>) -> Unit) {
    val tabs = remember {
        listOf(
            Tab("General", Icons.Default.Settings), Tab("Look", Icons.Default.Create),
            Tab("Features", Icons.Default.Menu), Tab("Access", Icons.Default.Lock), Tab("About", Icons.Default.Info)
        )
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val s by app.settings.flow.collectAsState()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(t.icon, null) }, label = { Text(t.label, fontSize = 11.sp, maxLines = 1) }
                    )
                }
            }
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).statusBarsPadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (tab) {
                0 -> GeneralScreen(app, s)
                1 -> AppearanceScreen(app, s)
                2 -> FeaturesScreen(app, s, requestPermissions)
                3 -> PermissionsScreen(app, requestPermissions)
                else -> AboutScreen()
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---------- shared rows ----------
@Composable
private fun Header(text: String) = Text(text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))

@Composable
private fun Section(content: @Composable () -> Unit) = Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    shape = RoundedCornerShape(20.dp)
) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() } }

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            if (sub != null) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun SliderRow(title: String, value: Int, range: IntRange, unit: String, onChange: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(title, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text("$value$unit", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value.toFloat(), onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat()
        )
    }
}

// ---------- General ----------
@Composable
private fun GeneralScreen(app: DyneApplication, s: DyneSettings) {
    Header("Dyne")
    Section {
        SwitchRow("Enable Dyne", "Master switch. Off stops the overlay service.", s.enabled) { v -> app.settings.update { it.copy(enabled = v) } }
        SwitchRow("Island overlay", "Show the island over other apps", s.overlayEnabled) { v -> app.settings.update { it.copy(overlayEnabled = v) } }
        SwitchRow("Start on boot", "Start the overlay after the phone restarts", s.startOnBoot) { v -> app.settings.update { it.copy(startOnBoot = v) } }
    }
    Section {
        Text("Animation intensity", fontSize = 15.sp)
        val labels = listOf("Off", "Reduced", "Full")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { i, l ->
                if (s.animation == i) Button({ app.settings.update { it.copy(animation = i) } }) { Text(l) }
                else OutlinedButton({ app.settings.update { it.copy(animation = i) } }) { Text(l) }
            }
        }
        SliderRow("Island timeout", s.timeoutSec, 2..15, " s") { v -> app.settings.update { it.copy(timeoutSec = v) } }
    }
    Section {
        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
            Text(if (app.permissions.hasOverlay()) "Overlay permission granted" else "Grant 'Display over other apps' in the Access tab")
        }
    }
}

// ---------- Appearance ----------
@Composable
private fun AppearanceScreen(app: DyneApplication, s: DyneSettings) {
    Header("Appearance")
    Text("Changes apply to the island live.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Section {
        Text("Position", fontWeight = FontWeight.Medium)
        Text(
            "Dyne reads the camera cutout from Android and centres on it. Use these only if it is slightly off.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SliderRow("Horizontal", s.offsetX, -60..60, " dp") { v -> app.settings.update { it.copy(offsetX = v) } }
        SliderRow("Vertical", s.offsetY, -30..30, " dp") { v -> app.settings.update { it.copy(offsetY = v) } }
        TextButton({ app.settings.update { it.copy(offsetX = 0, offsetY = 0) } }) { Text("Reset position") }
    }
    Section {
        Text("Compact island", fontWeight = FontWeight.Medium)
        SliderRow("Width beyond camera (each side)", s.compactExtra, 28..80, " dp") { v -> app.settings.update { it.copy(compactExtra = v) } }
        SliderRow("Height beyond camera", s.compactExtraH, 0..24, " dp") { v -> app.settings.update { it.copy(compactExtraH = v) } }
    }
    Section {
        Text("Expanded island", fontWeight = FontWeight.Medium)
        SliderRow("Width", s.expandedWidth, 260..420, " dp") { v -> app.settings.update { it.copy(expandedWidth = v) } }
        SliderRow("Height", s.expandedHeight, 130..260, " dp") { v -> app.settings.update { it.copy(expandedHeight = v) } }
        SliderRow("Corner radius", s.cornerRadius, 12..56, " dp") { v -> app.settings.update { it.copy(cornerRadius = v) } }
    }
    TextButton({
        app.settings.update { it.copy(
            expandedWidth = 350, expandedHeight = 170, compactExtra = 44, compactExtraH = 6, cornerRadius = 36
        ) }
    }) { Text("Reset sizes") }
}

// ---------- Features ----------
@Composable
private fun FeaturesScreen(app: DyneApplication, s: DyneSettings, request: (Array<String>) -> Unit) {
    val p = app.permissions
    Header("Features")
    Section {
        Feature.values().forEach { f ->
            val missing = when (f) {
                Feature.MUSIC, Feature.NOTIFICATIONS -> if (!p.hasNotificationAccess()) "Needs notification access (Access tab)" else null
                Feature.CALLS -> if (!p.hasPhoneState()) "Needs phone permission" else null
                Feature.BLUETOOTH -> if (!p.hasBluetoothConnect()) "Device names need Bluetooth permission" else null
                Feature.TORCH -> if (!app.torch.available) "No flashlight on this device" else null
                else -> null
            }
            SwitchRow(f.label, missing ?: f.blurb, f in s.features) { on ->
                app.settings.update { it.copy(features = if (on) it.features + f else it.features - f) }
                if (on) when (f) { // request a permission only when its feature is switched on
                    Feature.CALLS -> if (!p.hasPhoneState() || !p.hasAnswerCalls()) request(p.phonePermissions())
                    Feature.BLUETOOTH -> if (!p.hasBluetoothConnect()) request(p.bluetoothPermissions())
                    else -> {}
                }
            }
        }
    }
    if (Feature.NOTIFICATIONS in s.features) NotificationApps(app, s)
}

private data class AppRow(val pkg: String, val label: String)

@Composable
private fun NotificationApps(app: DyneApplication, s: DyneSettings) {
    val ctx = LocalContext.current
    val apps by produceState(emptyList<AppRow>()) {
        value = withContext(Dispatchers.IO) { launcherApps(ctx) }
    }
    Section {
        Text("Apps allowed in the island", fontWeight = FontWeight.Medium)
        Text("Notification content is only shown on this device and never stored or sent anywhere.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (apps.isEmpty()) Text("Loading…", fontSize = 13.sp)
        apps.forEach { a ->
            val allowed = a.pkg !in s.blockedPackages
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(a.label, Modifier.weight(1f), maxLines = 1, fontSize = 15.sp)
                Switch(allowed, { on ->
                    app.settings.update { it.copy(blockedPackages = if (on) it.blockedPackages - a.pkg else it.blockedPackages + a.pkg) }
                })
            }
        }
    }
}

private fun launcherApps(ctx: Context): List<AppRow> {
    val pm = ctx.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, PackageManager.MATCH_ALL)
        .map { AppRow(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.pkg != ctx.packageName }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

// ---------- Permissions ----------
@Composable
private fun PermissionsScreen(app: DyneApplication, request: (Array<String>) -> Unit) {
    val ctx = LocalContext.current
    val tick by app.permissionRefresh.collectAsState()
    val p = app.permissions
    fun open(i: Intent) { runCatching { ctx.startActivity(i) } }
    Header("Permissions")
    Text("Dyne works entirely on this device and has no internet permission.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    key(tick) {
        PermCard("Display over other apps", "Required. Lets Dyne draw the island above other apps.", p.hasOverlay(), "Open settings") { open(p.overlaySettingsIntent()) }
        PermCard("Notification access", "Required for notifications and for detecting music sessions (Android only shares media sessions with apps that have this). Notification text stays on the device.", p.hasNotificationAccess(), "Open settings") { open(p.notificationListenerIntent()) }
        PermCard("Phone state", "Needed to show incoming and ongoing calls. Answer/end buttons additionally use the 'answer calls' permission.", p.hasPhoneState() && p.hasAnswerCalls(), "Grant") { request(p.phonePermissions()) }
        PermCard("Bluetooth (nearby devices)", "Needed on Android 12+ to read device names and connection events.", p.hasBluetoothConnect(), "Grant") { request(p.bluetoothPermissions()) }
        PermCard("Post notifications", "Android 13+ hides the small 'Dyne is running' notification without this. The island still works.", p.hasPostNotifications(), "Grant") { request(p.notificationPermissions()) }
        PermCard("Camera / flashlight", "No permission is needed. The torch uses the flashlight API only, never the camera.", true, "") {}
        PermCard("Unrestricted battery", "Optional. Helps Android keep Dyne running on aggressive battery managers.", p.ignoresBatteryOptimizations(), "Open settings") { open(p.batterySettingsIntent()) }
    }
    Section {
        Text("Notification access greyed out?", fontWeight = FontWeight.Medium)
        Text(
            "For apps installed outside the Play Store, Android 13+ blocks some permissions until you allow them: " +
                "open App info for Dyne, tap the three-dot menu, choose 'Allow restricted settings', then enable notification access.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton({ open(p.appDetailsIntent()) }) { Text("Open App info") }
    }
}

@Composable
private fun PermCard(title: String, why: String, granted: Boolean, action: String, onClick: () -> Unit) {
    Section {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(
                if (granted) "Granted" else "Not granted", fontSize = 12.sp,
                color = if (granted) com.dyne.island.Green else com.dyne.island.Orange
            )
        }
        Text(why, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!granted && action.isNotEmpty()) Button(onClick) { Text(action) }
    }
}

// ---------- About ----------
@Composable
private fun AboutScreen() {
    val ctx = LocalContext.current
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "1.0.0" }
    Header("About")
    Section {
        Text("Dyne", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text("Version $version", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Developer: ${ctx.getString(R.string.developer)}")
    }
    Section {
        Text("Privacy", fontWeight = FontWeight.Medium)
        Text(
            "Dyne runs entirely on your device. It has no internet permission, no analytics, no ads and no accounts. " +
                "Notification, media and call information is read only to draw the island and is never stored or transmitted. " +
                "Sensitive permissions are only used by the feature that needs them.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
