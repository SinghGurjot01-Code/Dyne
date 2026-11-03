package com.dyne.island

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dyne.events.ChargeState
import com.dyne.events.IslandEvent
import com.dyne.settings.DyneSettings
import com.dyne.ui.DyneIcons
import com.dyne.utils.formatElapsed
import com.dyne.utils.formatMs

val Green = Color(0xFF30D158)
val Orange = Color(0xFFFF9F0A)
val Red = Color(0xFFFF453A)
val Blue = Color(0xFF0A84FF)
val Yellow = Color(0xFFFFD60A)
val Dim = Color(0xFF9A9AA2)

/** Left of the camera hole / camera hole / right of the camera hole. */
@Composable
fun CompactContent(event: IslandEvent?, charging: Boolean, s: DyneSettings, g: com.dyne.overlay.IslandGeometry) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(s.compactExtra.dp).fillMaxHeight().padding(start = 12.dp), Alignment.CenterStart) {
            CompactLeft(event, charging)
        }
        Spacer(Modifier.width(g.cutoutW.dp))
        Box(Modifier.width(s.compactExtra.dp).fillMaxHeight().padding(end = 12.dp), Alignment.CenterEnd) {
            CompactRight(event, charging, s.animation)
        }
    }
}

@Composable
private fun Glyph(icon: ImageVector, tint: Color) =
    Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))

@Composable
private fun CompactLeft(e: IslandEvent?, charging: Boolean) {
    when (e) {
        null -> if (charging) Glyph(DyneIcons.Bolt, Green)
        is IslandEvent.Call -> Glyph(DyneIcons.Call, Green)
        is IslandEvent.Timer -> Glyph(DyneIcons.Timer, Orange)
        is IslandEvent.TimerDone -> Glyph(DyneIcons.Timer, Orange)
        is IslandEvent.Music -> {
            val bmp = e.art
            if (bmp != null) {
                val ib = remember(bmp) { bmp.asImageBitmap() }
                Image(ib, null, Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)), contentScale = ContentScale.Crop)
            } else Glyph(DyneIcons.Music, Color.White)
        }
        IslandEvent.Torch -> Glyph(DyneIcons.Torch, Yellow)
        is IslandEvent.Charging -> Glyph(DyneIcons.Bolt, Green)
        is IslandEvent.Notification -> {
            val icon = rememberAppIcon(e.pkg)
            if (icon != null) Image(icon, null, Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)))
            else Glyph(DyneIcons.Bell, Color.White)
        }
        is IslandEvent.Bluetooth -> Glyph(DyneIcons.Bluetooth, Blue)
        is IslandEvent.Volume -> Glyph(DyneIcons.Volume, Color.White)
    }
}

@Composable
private fun CompactRight(e: IslandEvent?, charging: Boolean, anim: Int) {
    when (e) {
        null -> {}
        is IslandEvent.Call -> {
            if (e.ringing) Text("Ringing", color = Green, fontSize = 10.sp, maxLines = 1)
            else {
                val now by rememberElapsed(true, 1000)
                Text(formatElapsed(now - e.startedAt), color = Green, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
        is IslandEvent.Timer -> {
            val now by rememberElapsed(e.running, 1000)
            val rem = if (e.running) e.endElapsed - now else e.remainingMs
            Text(formatMs(rem), color = if (e.running) Orange else Dim, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        is IslandEvent.TimerDone -> Text("Done", color = Orange, fontSize = 11.sp, maxLines = 1)
        is IslandEvent.Music -> EqBars(e.playing, anim)
        IslandEvent.Torch -> Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(Yellow))
        is IslandEvent.Charging -> Text(
            if (e.state == ChargeState.DISCONNECTED) "${e.percent}%" else "${e.percent}%",
            color = if (e.state == ChargeState.DISCONNECTED) Color.White else Green, fontSize = 11.sp, maxLines = 1
        )
        is IslandEvent.Notification -> Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(Red))
        is IslandEvent.Bluetooth -> Text(
            when (e.kind) {
                com.dyne.events.BtKind.CONNECTED -> "On"
                com.dyne.events.BtKind.DISCONNECTED -> "Off"
                com.dyne.events.BtKind.ON -> "On"
                com.dyne.events.BtKind.OFF -> "Off"
            }, color = Blue, fontSize = 10.sp, maxLines = 1
        )
        is IslandEvent.Volume -> Text("${e.level * 100 / e.max.coerceAtLeast(1)}", color = Color.White, fontSize = 11.sp, maxLines = 1)
    }
}

/** Three bars; animated only while playing and only with full animations. */
@Composable
private fun EqBars(playing: Boolean, anim: Int) {
    val animate = playing && anim == 2
    val t = rememberInfiniteTransition(label = "eq")
    val a = if (animate) t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "a").value else 0.5f
    val b = if (animate) t.animateFloat(1f, 0.35f, infiniteRepeatable(tween(560, easing = LinearEasing), RepeatMode.Reverse), label = "b").value else 0.8f
    val c = if (animate) t.animateFloat(0.4f, 0.9f, infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse), label = "c").value else 0.4f
    Row(Modifier.height(16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(a, b, c).forEach { f ->
            Box(Modifier.width(3.dp).height((14 * f).dp).clip(RoundedCornerShape(2.dp)).background(if (playing) Color.White else Dim))
        }
    }
}
