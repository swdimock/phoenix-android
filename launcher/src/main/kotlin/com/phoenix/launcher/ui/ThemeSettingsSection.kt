package com.phoenix.launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.phoenix.launcher.ui.theme.ThemeRepository
import com.phoenix.uitheme.AccentPreset
import com.phoenix.uitheme.PhoenixShapes
import com.phoenix.uitheme.PhoenixTheme
import kotlinx.coroutines.launch

/** "Appearance": one swatch per [AccentPreset]. The selection is the preset currently applied by the theme. */
@Composable
fun ThemeSettingsSection() {
    val context = LocalContext.current
    val repository = remember(context) { ThemeRepository(context) }
    val scope = rememberCoroutineScope()
    val selected = PhoenixTheme.palette.preset
    Section("Appearance") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccentPreset.entries.forEach { preset ->
                Swatch(preset, preset == selected, { scope.launch { repository.setPreset(preset) } }, Modifier.weight(1f))
            }
        }
        Hint("Accent colour for the launcher and the climate bar.")
    }
}

@Composable
private fun Swatch(preset: AccentPreset, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val palette = PhoenixTheme.palette
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = PhoenixShapes.Medium,
        color = palette.surface,
        border = if (selected) BorderStroke(3.dp, palette.text) else BorderStroke(1.dp, palette.outline),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(Brush.verticalGradient(listOf(preset.fillTop, preset.fillBottom)), PhoenixShapes.Small),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = preset.onAccent, modifier = Modifier.size(40.dp))
            }
            Text(
                preset.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) palette.text else palette.textMuted,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
