package com.phoenix.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Android Auto pane: re-open button when the receiver is installed, otherwise setup guidance. */
@Composable
fun AaPane(
    installed: Boolean,
    receiverPackage: String,
    transport: String,
    onOpen: () -> Unit,
    onSwitchToRadio: () -> Unit,
) {
    PaneColumn {
        if (installed) {
            InfoCard(
                title = "Android Auto",
                body = "Transport: $transport\nReceiver: $receiverPackage",
            ) { BigButton("Open Android Auto", onOpen, Modifier.fillMaxWidth()) }
        } else {
            InfoCard(
                title = "Android Auto receiver not installed",
                body = "Install the receiver app ($receiverPackage), or set a different package in Settings.\n\n" +
                    "Then connect your phone with a USB cable, or pair it over Bluetooth for wireless Android Auto.",
            ) {
                BigButton("Switch to Radio", onSwitchToRadio, Modifier.fillMaxWidth())
            }
        }
    }
}

/** Pane for a mode served by a vendor app (radio, phone). */
@Composable
fun VendorAppPane(
    title: String,
    installed: Boolean,
    openLabel: String,
    onOpen: () -> Unit,
    missingNote: String,
) {
    PaneColumn {
        if (installed) {
            InfoCard(title = title, body = "$title runs in the head unit's own app.") {
                BigButton(openLabel, onOpen, Modifier.fillMaxWidth())
            }
        } else {
            InfoCard(title = "$title app not found", body = missingNote) {
                OutlinedButton(
                    onClick = onOpen,
                    modifier = Modifier.fillMaxWidth().heightIn(min = LauncherLayout.TouchTarget),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("Try again", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

@Composable
private fun PaneColumn(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) { content() }
}
