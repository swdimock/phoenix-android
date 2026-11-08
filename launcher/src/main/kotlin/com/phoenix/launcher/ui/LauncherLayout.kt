package com.phoenix.launcher.ui

import androidx.compose.ui.unit.dp

/** Screen-layout constants shared by all launcher panes. */
object LauncherLayout {
    /** Bottom strip owned by the external HVAC bar overlay (`com.phoenix.hvacbar`). Nothing of ours goes here. */
    val HvacBarInset = 227.dp

    /** Minimum height for primary touch targets. */
    val TouchTarget = 96.dp
}
