package com.dyne.island

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.dyne.DyneApplication
import com.dyne.overlay.IslandController
import com.dyne.overlay.IslandGeometry
import com.dyne.settings.DyneSettings
import com.dyne.ui.theme.DyneTheme
import kotlin.math.abs
import kotlin.math.min

@Composable
fun IslandRoot(controller: IslandController, app: DyneApplication, s: DyneSettings, g: IslandGeometry) {
    DyneTheme {
        val expanded by controller.expanded.collectAsState()
        val event by controller.display.collectAsState()
        val charging by app.charging.isCharging.collectAsState()

        val compactW = g.cutoutW + s.compactExtra * 2
        val compactH = g.cutoutH + s.compactExtraH
        val targetW = if (expanded) s.expandedWidth.toFloat() else compactW
        val targetH = if (expanded) s.expandedHeight * heightFraction(event?.type) else compactH
        val spec = dpSpec(s.animation)
        val w = animateDpAsState(targetW.dp, spec, label = "w")
        val h = animateDpAsState(targetH.dp, spec, label = "h")
        val radius = animateDpAsState(
            if (expanded) min(s.cornerRadius.toFloat(), targetH / 2f).dp else (compactH / 2f).dp, spec, label = "r"
        )

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier
                    .islandSize(w, h)
                    .graphicsLayer { shape = RoundedCornerShape(radius.value); clip = true }
                    .background(Color.Black)
                    .pointerInput(expanded) {
                        detectTapGestures { if (!expanded) controller.expandManual() else controller.interact() }
                    }
                    .pointerInput(Unit) {
                        var dx = 0f
                        var dy = 0f
                        detectDragGestures(
                            onDragStart = { dx = 0f; dy = 0f },
                            onDragEnd = {
                                when {
                                    dy < -48f -> controller.dismiss()
                                    abs(dx) > 72f -> controller.cycle(if (dx < 0) 1 else -1)
                                }
                            },
                            onDrag = { change, amount -> change.consume(); dx += amount.x; dy += amount.y }
                        )
                    }
            ) {
                AnimatedContent(
                    targetState = expanded to event?.type,
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                    transitionSpec = {
                        (fadeIn(tween(fadeMs(s.animation, 180), delayMillis = fadeMs(s.animation, 70))) togetherWith
                            fadeOut(tween(fadeMs(s.animation, 90))))
                            .using(SizeTransform(clip = false) { _, _ -> androidx.compose.animation.core.snap() })
                    },
                    label = "content"
                ) { (isExpanded, _) ->
                    if (!isExpanded) CompactContent(event, charging, s, g)
                    else ExpandedContent(event, controller, app, s, compactH.dp)
                }
            }
        }
    }
}
