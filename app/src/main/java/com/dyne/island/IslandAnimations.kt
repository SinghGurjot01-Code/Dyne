package com.dyne.island

import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.layout.layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.dyne.events.EventType
import kotlinx.coroutines.delay

/** 0 = no animation, 1 = reduced (short tween, no overshoot), 2 = full spring. */
fun dpSpec(mode: Int): FiniteAnimationSpec<Dp> = when (mode) {
    0 -> snap()
    1 -> tween(200, easing = FastOutSlowInEasing)
    else -> spring(dampingRatio = 0.78f, stiffness = 420f, visibilityThreshold = Dp.VisibilityThreshold)
}

fun fadeMs(mode: Int, base: Int) = if (mode == 0) 0 else if (mode == 1) base / 2 else base

/** Fraction of the configured expanded height each content type uses. */
fun heightFraction(type: EventType?): Float = when (type) {
    EventType.MUSIC -> 1f
    EventType.CALL_INCOMING, EventType.CALL_ACTIVE, EventType.TIMER, EventType.NOTIFICATION -> 0.8f
    else -> 0.62f
}

/** Size is read in the layout phase, so animating it does not recompose the island. */
fun Modifier.islandSize(w: State<Dp>, h: State<Dp>): Modifier = layout { measurable, _ ->
    val pw = w.value.roundToPx()
    val ph = h.value.roundToPx()
    val p = measurable.measure(Constraints.fixed(pw, ph))
    layout(pw, ph) { p.place(0, 0) }
}

/** Ticks only while [active]; nothing runs when the island is idle. */
@Composable
fun rememberElapsed(active: Boolean, intervalMs: Long): State<Long> =
    produceState(SystemClock.elapsedRealtime(), active, intervalMs) {
        value = SystemClock.elapsedRealtime()
        while (active) {
            delay(intervalMs)
            value = SystemClock.elapsedRealtime()
        }
    }
