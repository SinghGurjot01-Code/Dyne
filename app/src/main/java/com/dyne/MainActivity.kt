package com.dyne

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.dyne.overlay.DyneOverlayService
import com.dyne.settings.SettingsApp
import com.dyne.ui.theme.DyneTheme
import kotlinx.coroutines.flow.update

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DyneTheme {
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    dyne.permissionRefresh.update { it + 1 }
                }
                ServiceSync()
                SettingsApp(dyne) { perms -> if (perms.isNotEmpty()) launcher.launch(perms) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        dyne.permissionRefresh.update { it + 1 } // re-check after returning from system settings
    }
}

/** Starts/stops the overlay service from the settings state; the service itself reacts to later changes. */
@Composable
private fun ServiceSync() {
    val app = androidx.compose.ui.platform.LocalContext.current.dyne
    val s by app.settings.flow.collectAsState()
    val tick by app.permissionRefresh.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(s.enabled, s.overlayEnabled, tick) {
        if (s.enabled && app.permissions.hasOverlay()) DyneOverlayService.start(ctx)
        else if (!s.enabled) DyneOverlayService.stop(ctx)
    }
}
