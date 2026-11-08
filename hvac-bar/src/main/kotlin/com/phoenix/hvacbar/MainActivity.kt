package com.phoenix.hvacbar

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.phoenix.hvacbar.overlay.BarService

/**
 * Launcher entry point with no UI of its own: obtains the overlay permission if needed, starts
 * [BarService] and finishes.
 */
class MainActivity : ComponentActivity() {

    private val overlayPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this)) {
                startBarAndFinish()
            } else {
                Toast.makeText(this, R.string.overlay_permission_denied, Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when {
            Settings.canDrawOverlays(this) -> startBarAndFinish()
            savedInstanceState == null -> overlayPermission.launch(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
            )
        }
    }

    private fun startBarAndFinish() {
        BarService.start(this)
        finish()
    }
}
