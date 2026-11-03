package com.dyne.settings

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class Feature(val label: String, val blurb: String) {
    MUSIC("Music", "Artwork, title and playback controls from active media sessions"),
    NOTIFICATIONS("Notifications", "Incoming notifications from apps you allow"),
    CHARGING("Charging", "Plug in, unplug and fully charged events"),
    CALLS("Calls", "Incoming and ongoing call indicator"),
    TIMER("Timer", "Countdown timer that keeps running in the background"),
    TORCH("Torch", "Flashlight state and toggle"),
    VOLUME("Volume", "Volume level indicator"),
    BLUETOOTH("Bluetooth", "Bluetooth state and device connections")
}

data class DyneSettings(
    val enabled: Boolean = true,
    val startOnBoot: Boolean = true,
    val overlayEnabled: Boolean = true,
    /** 0 = off, 1 = reduced, 2 = full spring animations */
    val animation: Int = 2,
    val timeoutSec: Int = 5,
    val expandedWidth: Int = 350,
    val expandedHeight: Int = 170,
    /** Extra pill width on each side of the camera cutout (dp) */
    val compactExtra: Int = 20,
    /** Extra pill height over the cutout height (dp) */
    val compactExtraH: Int = 2,
    val cornerRadius: Int = 36,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val features: Set<Feature> = Feature.values().toSet(),
    val blockedPackages: Set<String> = emptySet(),
    /** Ephemeral high-contrast preview while appearance sliders are being adjusted. */
    val previewUntilElapsed: Long = 0L
)

class SettingsRepository(context: Context) {
    private val p = context.getSharedPreferences("dyne_settings", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(load())
    val flow: StateFlow<DyneSettings> = _flow
    val current: DyneSettings get() = _flow.value

    @Synchronized
    fun update(transform: (DyneSettings) -> DyneSettings) {
        val old = _flow.value
        val requested = transform(old)
        if (requested == old) return
        val appearanceChanged =
            requested.expandedWidth != old.expandedWidth ||
            requested.expandedHeight != old.expandedHeight ||
            requested.compactExtra != old.compactExtra ||
            requested.compactExtraH != old.compactExtraH ||
            requested.cornerRadius != old.cornerRadius ||
            requested.offsetX != old.offsetX ||
            requested.offsetY != old.offsetY
        val n = requested.copy(
            previewUntilElapsed = if (appearanceChanged) SystemClock.elapsedRealtime() + 1100L
            else old.previewUntilElapsed
        )
        save(n)
        _flow.value = n
    }

    private fun load(): DyneSettings {
        val d = DyneSettings()
        val feats = p.getStringSet("features", null)
            ?.mapNotNull { runCatching { Feature.valueOf(it) }.getOrNull() }?.toSet() ?: d.features
        return DyneSettings(
            enabled = p.getBoolean("enabled", d.enabled),
            startOnBoot = p.getBoolean("startOnBoot", d.startOnBoot),
            overlayEnabled = p.getBoolean("overlayEnabled", d.overlayEnabled),
            animation = p.getInt("animation", d.animation),
            timeoutSec = p.getInt("timeoutSec", d.timeoutSec),
            expandedWidth = p.getInt("expandedWidth", d.expandedWidth),
            expandedHeight = p.getInt("expandedHeight", d.expandedHeight),
            compactExtra = p.getInt("compactExtra", d.compactExtra).coerceIn(4, 64),
            compactExtraH = p.getInt("compactExtraH", d.compactExtraH).coerceIn(0, 18),
            cornerRadius = p.getInt("cornerRadius", d.cornerRadius),
            offsetX = p.getInt("offsetX", d.offsetX),
            offsetY = p.getInt("offsetY", d.offsetY),
            features = feats,
            blockedPackages = p.getStringSet("blocked", emptySet())?.toSet() ?: emptySet()
        )
    }

    private fun save(s: DyneSettings) {
        p.edit()
            .putBoolean("enabled", s.enabled).putBoolean("startOnBoot", s.startOnBoot)
            .putBoolean("overlayEnabled", s.overlayEnabled).putInt("animation", s.animation)
            .putInt("timeoutSec", s.timeoutSec).putInt("expandedWidth", s.expandedWidth)
            .putInt("expandedHeight", s.expandedHeight).putInt("compactExtra", s.compactExtra)
            .putInt("compactExtraH", s.compactExtraH).putInt("cornerRadius", s.cornerRadius)
            .putInt("offsetX", s.offsetX).putInt("offsetY", s.offsetY)
            .putStringSet("features", s.features.map { it.name }.toSet())
            .putStringSet("blocked", s.blockedPackages)
            .apply()
    }
}
