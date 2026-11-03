package com.dyne.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.SystemClock
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dyne.DyneApplication
import com.dyne.island.IslandRoot
import com.dyne.island.heightFraction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class TouchRootLayout(ctx: Context) : FrameLayout(ctx) {
    var onOutside: (() -> Unit)? = null
    var isCompact: (() -> Boolean)? = null
    var onCompactTap: (() -> Unit)? = null

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_OUTSIDE) {
            onOutside?.invoke()
            return true
        }
        // The compact island has no child controls. Handle its tap at the window root so
        // expansion does not depend on Compose pointer hit-testing in an overlay window.
        if (isCompact?.invoke() == true) {
            if (ev.action == MotionEvent.ACTION_UP) onCompactTap?.invoke()
            return true
        }
        return super.dispatchTouchEvent(ev)
    }
}

/**
 * Owns the single overlay window. The window is kept only as large as the island needs: it grows immediately
 * before an expand animation and shrinks after a collapse animation settles, so touches outside the island
 * always reach the app underneath, and there is no size/position jump.
 */
class OverlayWindowManager(
    private val context: Context,
    private val owner: SavedStateRegistryOwner,
    private val app: DyneApplication,
    private val controller: IslandController,
    private val scope: CoroutineScope
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val dm = context.getSystemService(DisplayManager::class.java)
    private var root: TouchRootLayout? = null
    private var lp: WindowManager.LayoutParams? = null
    private var raw: RawGeo? = null
    private var curW = 0f
    private var curH = 0f
    private var resizeJob: Job? = null
    private var sizingJob: Job? = null
    private val geometry = MutableStateFlow(IslandGeometry(24f, 24f))

    var screenOn = true
    val isShowing get() = root != null

    /** Show/hide according to state; call whenever any input changes. */
    fun sync() {
        readGeometry()
        val s = app.settings.current
        val should = screenOn && (raw?.portrait == true) && s.enabled && s.overlayEnabled && app.permissions.hasOverlay()
        if (should && root == null) add() else if (!should && root != null) remove()
        else if (should) reposition()
    }

    fun onConfigurationChanged() = sync()

    private fun add() {
        val rawGeo = raw ?: return
        val view = TouchRootLayout(context).apply {
            onOutside = { controller.collapse() }
            isCompact = { !controller.expanded.value }
            onCompactTap = { controller.expandManual() }
            setViewTreeLifecycleOwner(owner as LifecycleOwner)
            setViewTreeSavedStateRegistryOwner(owner)
            addView(ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    val s = app.settings.flow.collectAsStateCompat()
                    val g = geometry.collectAsStateCompat()
                    IslandRoot(controller, app, s, g)
                }
            })
        }
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "DyneIsland"
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) fitInsetsTypes = 0
        }
        lp = p
        root = view
        val (w, h) = targetDp()
        curW = w; curH = h
        layout(p, rawGeo, w, h)
        runCatching { wm.addView(view, p) }.onFailure { root = null; lp = null; return }
        startSizing()
    }

    fun remove() {
        sizingJob?.cancel(); resizeJob?.cancel()
        root?.let { runCatching { wm.removeViewImmediate(it) } }
        root = null; lp = null
    }

    private fun reposition() {
        val p = lp ?: return
        val r = raw ?: return
        layout(p, r, curW, curH)
        root?.let { runCatching { wm.updateViewLayout(it, p) } }
    }

    /** Target window size in dp for the current state (island + small margin for spring overshoot). */
    private fun targetDp(): Pair<Float, Float> {
        val s = app.settings.current
        val g = geometry.value
        val expanded = controller.expanded.value
        val ev = controller.display.value
        val w = if (expanded) s.expandedWidth.toFloat() else g.cutoutW + s.compactExtra * 2
        val h = if (expanded) s.expandedHeight * heightFraction(ev?.type) else g.cutoutH + s.compactExtraH
        return (w + PAD * 2) to (h + PAD)
    }

    private fun startSizing() {
        sizingJob?.cancel()
        sizingJob = scope.launch {
            combine(controller.expanded, controller.display, app.settings.flow, geometry) { expanded, _, settings, _ ->
                Triple(expanded, settings.previewUntilElapsed, settings.animation)
            }.collect { (expanded, previewUntil, animation) ->
                val (w, h) = targetDp()
                resizeJob?.cancel()
                val adjustingAppearance = previewUntil > SystemClock.elapsedRealtime()
                if (expanded || adjustingAppearance || (w >= curW && h >= curH) || animation == 0) {
                    applySize(w, h)
                } else {
                    resizeJob = scope.launch { delay(520); applySize(w, h) } // let the collapse animation settle
                }
            }
        }
    }

    private fun applySize(w: Float, h: Float) {
        curW = w; curH = h
        val p = lp ?: return
        val r = raw ?: return
        layout(p, r, w, h)
        root?.let { runCatching { wm.updateViewLayout(it, p) } }
    }

    private fun layout(p: WindowManager.LayoutParams, r: RawGeo, wDp: Float, hDp: Float) {
        val d = r.density
        val s = app.settings.current
        val wPx = (wDp * d).toInt()
        val hPx = (hDp * d).toInt()
        p.width = wPx; p.height = hPx
        val x = (r.cutoutCenterX - wPx / 2f + s.offsetX * d).toInt().coerceIn(0, (r.screenW - wPx).coerceAtLeast(0))
        // Island top aligns with the cutout top, grown by half of the extra compact height.
        val top = r.cutoutTop - (s.compactExtraH * d / 2f)
        p.x = x
        p.y = (top + s.offsetY * d).toInt().coerceAtLeast(-(2 * d).toInt())
    }

    private fun readGeometry() {
        val d = context.resources.displayMetrics.density
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
        val bounds: Rect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) wm.maximumWindowMetrics.bounds
        else legacyBounds(display)
        val sw = bounds.width(); val sh = bounds.height()
        val portrait = sh > sw

        val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display.cutout
        else root?.rootWindowInsets?.displayCutout
        val rect = cutout?.boundingRects
            ?.filter { it.top < sh / 8 && it.bottom < sh / 6 && it.width() > 0 }
            ?.minByOrNull { it.top }

        raw = if (rect != null) RawGeo(sw, sh, rect.exactCenterX(), rect.top, rect.width(), rect.height(), d, portrait, true)
        else RawGeo(sw, sh, sw / 2f, (8 * d).toInt(), (24 * d).toInt(), (24 * d).toInt(), d, portrait, false) // fallback
        val r = raw!!
        geometry.value = IslandGeometry(r.cutoutW / d, r.cutoutH / d)
    }

    @Suppress("DEPRECATION")
    private fun legacyBounds(display: Display): Rect {
        val pt = android.graphics.Point()
        display.getRealSize(pt)
        return Rect(0, 0, pt.x, pt.y)
    }

    companion object { private const val PAD = 10f }
}
