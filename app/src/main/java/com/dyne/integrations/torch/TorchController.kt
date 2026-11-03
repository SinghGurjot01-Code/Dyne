package com.dyne.integrations.torch

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.dyne.events.EventType
import com.dyne.events.IslandEvent
import com.dyne.events.IslandEventManager
import com.dyne.integrations.Integration
import kotlinx.coroutines.flow.MutableStateFlow

/** CameraManager.setTorchMode needs no camera permission. */
class TorchController(private val ctx: Context, private val events: IslandEventManager) : Integration {
    private val cm = ctx.getSystemService(CameraManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    val isOn = MutableStateFlow(false)

    val cameraId: String? = runCatching {
        cm.cameraIdList.firstOrNull { id ->
            val c = cm.getCameraCharacteristics(id)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    }.getOrNull()

    val available: Boolean get() = cameraId != null

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id != cameraId) return
            isOn.value = enabled
            if (enabled) events.setPersistent(IslandEvent.Torch) else events.clear(EventType.TORCH)
        }

        override fun onTorchModeUnavailable(id: String) {
            if (id != cameraId) return
            isOn.value = false
            events.clear(EventType.TORCH) // camera in use by another app
        }
    }

    override fun start() {
        if (cameraId != null) runCatching { cm.registerTorchCallback(callback, handler) }
    }

    override fun stop() {
        runCatching { cm.unregisterTorchCallback(callback) }
        events.clear(EventType.TORCH)
    }

    fun toggle() {
        val id = cameraId
        if (id == null) { toast("No flashlight available"); return }
        try {
            cm.setTorchMode(id, !isOn.value)
        } catch (e: CameraAccessException) {
            toast("Torch unavailable (camera in use)")
        } catch (e: IllegalArgumentException) {
            toast("Torch unavailable")
        }
    }

    private fun toast(msg: String) = handler.post { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
}
