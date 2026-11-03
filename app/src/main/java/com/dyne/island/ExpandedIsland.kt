package com.dyne.island

import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.ImageBitmap
import com.dyne.DyneApplication
import com.dyne.events.BtKind
import com.dyne.events.ChargeState
import com.dyne.events.IslandEvent
import com.dyne.overlay.IslandController
import com.dyne.settings.DyneSettings
import com.dyne.settings.Feature
import com.dyne.ui.DyneIcons
import com.dyne.utils.formatElapsed
import com.dyne.utils.formatMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun rememberAppIcon(pkg: String): ImageBitmap? {
    val ctx = LocalContext.current
    val s = produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.Default) {
            runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }
    return s.value
}

@Composable
fun ExpandedContent(event: IslandEvent?, controller: IslandController, app: DyneApplication, s: DyneSettings, topPad: Dp) {
    Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = topPad, bottom = 12.dp)) {
        when (event) {
            null -> IdlePanel(app, s, controller)
            is IslandEvent.Music -> MusicPanel(event, controller)
            is IslandEvent.Call -> CallPanel(event, app)
            is IslandEvent.Timer -> TimerPanel(event, app)
            is IslandEvent.TimerDone -> TimerDonePanel(event, app, controller)
            is IslandEvent.Notification -> NotificationPanel(event, app, controller)
            is IslandEvent.Charging -> ChargingPanel(event)
            IslandEvent.Torch -> TorchPanel(app)
            is IslandEvent.Volume -> VolumePanel(event, controller)
            is IslandEvent.Bluetooth -> BluetoothPanel(event)
        }
    }
}

@Composable
private fun RoundBtn(icon: ImageVector, bg: Color, tint: Color = Color.White, size: Dp = 40.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f)) }
}

@Composable
private fun Title(text: String) =
    Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
private fun Sub(text: String) =
    Text(text, color = Dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
private fun Badge(icon: ImageVector, color: Color) =
    Box(Modifier.size(48.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
    }

// ---------------- Music ----------------
@Composable
private fun MusicPanel(e: IslandEvent.Music, controller: IslandController) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ArtImage(e.art)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Title(e.title); Sub(e.artist) }
    }
    Spacer(Modifier.height(8.dp))
    if (e.durationMs > 0) {
        val now by rememberElapsed(e.playing, 500)
        val pos = if (e.playing) e.positionMs + ((now - e.updatedAt) * e.speed).toLong() else e.positionMs
        val frac by animateFloatAsState((pos.toFloat() / e.durationMs).coerceIn(0f, 1f), tween(500, easing = LinearEasing), label = "p")
        LinearProgressIndicator(
            progress = { frac }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = Color.White, trackColor = Color(0xFF3A3A40), gapSize = 0.dp, drawStopIndicator = {}
        )
        Spacer(Modifier.height(4.dp))
    }
    val tc = e.controller.transportControls
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        RoundBtn(DyneIcons.SkipPrev, Color.Transparent, if (e.canPrev) Color.White else Color(0xFF55555A)) { runCatching { tc.skipToPrevious() }; controller.interact() }
        RoundBtn(if (e.playing) DyneIcons.Pause else DyneIcons.Play, Color.White, Color.Black, 44.dp) {
            runCatching { if (e.playing) tc.pause() else tc.play() }; controller.interact()
        }
        RoundBtn(DyneIcons.SkipNext, Color.Transparent, if (e.canNext) Color.White else Color(0xFF55555A)) { runCatching { tc.skipToNext() }; controller.interact() }
    }
}

@Composable
private fun ArtImage(bmp: Bitmap?) {
    Crossfade(bmp, animationSpec = tween(250), label = "art") { b ->
        val mod = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
        if (b != null) {
            val ib = remember(b) { b.asImageBitmap() }
            Image(ib, null, mod, contentScale = ContentScale.Crop)
        } else Box(mod.background(Color(0xFF26262C)), Alignment.Center) {
            Icon(DyneIcons.Music, null, tint = Dim, modifier = Modifier.size(26.dp)) // missing artwork
        }
    }
}

// ---------------- Call ----------------
@Composable
private fun CallPanel(e: IslandEvent.Call, app: DyneApplication) {
    val can = app.permissions.hasAnswerCalls()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Badge(DyneIcons.Call, Green)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Title(if (e.ringing) "Incoming call" else "On call")
            if (e.ringing) Sub("Ringing…") else {
                val now by rememberElapsed(true, 1000)
                Sub(formatElapsed(now - e.startedAt))
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    if (can) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterHorizontally)) {
            Box(Modifier.graphicsLayer { rotationZ = 135f }) {
                RoundBtn(DyneIcons.Call, Red, size = 42.dp) { app.calls.endOrDecline() }
            }
            if (e.ringing) RoundBtn(DyneIcons.Call, Green, size = 42.dp) { app.calls.accept() }
        }
    } else Sub("Allow call control in Dyne to answer or end calls here")
}

// ---------------- Timer ----------------
@Composable
private fun TimerPanel(e: IslandEvent.Timer, app: DyneApplication) {
    val now by rememberElapsed(e.running, 250)
    val rem = if (e.running) e.endElapsed - now else e.remainingMs
    Row(verticalAlignment = Alignment.CenterVertically) {
        Badge(DyneIcons.Timer, Orange)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(formatMs(rem), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light, maxLines = 1)
            Sub(if (e.running) "Timer running" else "Paused")
        }
        RoundBtn(if (e.running) DyneIcons.Pause else DyneIcons.Play, Color(0xFF26262C)) {
            if (e.running) app.timer.pause() else app.timer.resume()
        }
        Spacer(Modifier.width(10.dp))
        RoundBtn(DyneIcons.Close, Color(0xFF26262C)) { app.timer.reset() }
    }
    if (e.totalMs > 0) {
        Spacer(Modifier.height(8.dp))
        val frac by animateFloatAsState(if (e.totalMs > 0) (1f - rem.toFloat() / e.totalMs).coerceIn(0f, 1f) else 0f, tween(250), label = "tp")
        LinearProgressIndicator(
            progress = { frac }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = Orange, trackColor = Color(0xFF3A3A40), gapSize = 0.dp, drawStopIndicator = {}
        )
    }
}

@Composable
private fun TimerDonePanel(e: IslandEvent.TimerDone, app: DyneApplication, c: IslandController) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Badge(DyneIcons.Timer, Orange)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Title("Timer finished"); Sub(formatMs(e.totalMs)) }
        RoundBtn(DyneIcons.Close, Color(0xFF26262C)) { c.dismiss() }
    }
}

// ---------------- Notification ----------------
@Composable
private fun NotificationPanel(e: IslandEvent.Notification, app: DyneApplication, c: IslandController) {
    val ctx = LocalContext.current
    val icon = rememberAppIcon(e.pkg)
    Row(
        Modifier.fillMaxWidth().clickable {
            runCatching { e.intent?.send() }; app.events.clearFlash(e.type); c.collapse()
        },
        verticalAlignment = Alignment.Top
    ) {
        if (icon != null) Image(icon, null, Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)))
        else Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFF26262C)), Alignment.Center) {
            Icon(DyneIcons.Bell, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(e.appName, color = Dim, fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Text(DateFormat.getTimeFormat(ctx).format(Date(e.postedAt)), color = Dim, fontSize = 11.sp)
            }
            if (e.title.isNotBlank()) Title(e.title)
            if (e.text.isNotBlank()) Text(e.text, color = Color(0xFFD0D0D6), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        RoundBtn(DyneIcons.Close, Color(0xFF26262C), size = 30.dp) { app.notifications.dismiss(e.key) }
    }
}

// ---------------- Charging ----------------
@Composable
private fun ChargingPanel(e: IslandEvent.Charging) {
    val (label, color) = when (e.state) {
        ChargeState.CONNECTED -> "Charging" to Green
        ChargeState.FULL -> "Fully charged" to Green
        ChargeState.DISCONNECTED -> "Unplugged" to Color.White
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Badge(DyneIcons.Bolt, color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Title(label)
            Sub(listOfNotNull(e.source).joinToString(" · ").ifEmpty { "Battery" })
        }
        Text("${e.percent}%", color = color, fontSize = 26.sp, fontWeight = FontWeight.Light)
    }
    Spacer(Modifier.height(8.dp))
    val frac by animateFloatAsState(e.percent / 100f, tween(400), label = "b")
    LinearProgressIndicator(
        progress = { frac }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
        color = if (e.percent <= 15 && e.state != ChargeState.CONNECTED) Red else color,
        trackColor = Color(0xFF3A3A40), gapSize = 0.dp, drawStopIndicator = {}
    )
}

// ---------------- Torch ----------------
@Composable
private fun TorchPanel(app: DyneApplication) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Badge(DyneIcons.Torch, Yellow)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Title("Torch on"); Sub("Tap to turn off") }
        RoundBtn(DyneIcons.Close, Yellow, Color.Black, 44.dp) { app.torch.toggle() }
    }
}

// ---------------- Volume ----------------
@Composable
private fun VolumePanel(e: IslandEvent.Volume, c: IslandController) {
    val ctx = LocalContext.current
    val audio = remember { ctx.getSystemService(AudioManager::class.java) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(DyneIcons.Volume, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Slider(
            value = e.level.toFloat(),
            onValueChange = { runCatching { audio.setStreamVolume(e.stream, it.roundToInt(), 0) }; c.interact() },
            valueRange = 0f..e.max.toFloat().coerceAtLeast(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color(0xFF3A3A40)
            ),
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
        )
        Text("${e.level * 100 / e.max.coerceAtLeast(1)}", color = Color.White, fontSize = 14.sp, modifier = Modifier.width(28.dp))
    }
}

// ---------------- Bluetooth ----------------
@Composable
private fun BluetoothPanel(e: IslandEvent.Bluetooth) {
    val (title, sub) = when (e.kind) {
        BtKind.CONNECTED -> (e.name ?: "Bluetooth device") to "Connected"
        BtKind.DISCONNECTED -> (e.name ?: "Bluetooth device") to "Disconnected"
        BtKind.ON -> "Bluetooth" to "Turned on"
        BtKind.OFF -> "Bluetooth" to "Turned off"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Badge(DyneIcons.Bluetooth, Blue)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Title(title); Sub(sub) }
    }
}

// ---------------- Idle (tap on compact island with nothing active) ----------------
@Composable
private fun IdlePanel(app: DyneApplication, s: DyneSettings, c: IslandController) {
    val torchOn by app.torch.isOn.collectAsState()
    val ctx = LocalContext.current
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (Feature.TORCH in s.features && app.torch.available) {
            RoundBtn(DyneIcons.Torch, if (torchOn) Yellow else Color(0xFF26262C), if (torchOn) Color.Black else Color.White, 42.dp) {
                app.torch.toggle(); c.interact()
            }
        }
        if (Feature.VOLUME in s.features) {
            RoundBtn(DyneIcons.Volume, Color(0xFF26262C), Color.White, 42.dp) {
                runCatching {
                    ctx.getSystemService(AudioManager::class.java)
                        .adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                }
                c.interact()
            }
        }
        if (Feature.BLUETOOTH in s.features) {
            RoundBtn(DyneIcons.Bluetooth, Color(0xFF26262C), Blue, 42.dp) {
                // Android exposes Bluetooth controls through the system settings activity;
                // direct adapter toggling is restricted for ordinary apps on modern Android.
                runCatching {
                    ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                c.interact()
            }
        }
        if (Feature.TIMER in s.features) {
            listOf(1 to "1m", 5 to "5m", 10 to "10m").forEach { (m, label) ->
                Box(
                    Modifier.clip(RoundedCornerShape(22.dp)).background(Color(0xFF26262C))
                        .clickable { app.timer.start(m * 60_000L); c.collapse() }
                        .padding(horizontal = 11.dp, vertical = 11.dp)
                ) { Text(label, color = Color.White, fontSize = 12.sp) }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}
