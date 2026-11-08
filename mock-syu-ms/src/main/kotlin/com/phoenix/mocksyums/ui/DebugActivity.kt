package com.phoenix.mocksyums.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import app.ToolkitService
import com.phoenix.mocksyums.MockRuntime

/** Launcher entry: starts the mock service and shows the vehicle-side debug controls. */
class DebugActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startForegroundService(Intent(this, ToolkitService::class.java))
        val runtime = MockRuntime.get(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                DebugScreen(runtime)
            }
        }
    }
}
