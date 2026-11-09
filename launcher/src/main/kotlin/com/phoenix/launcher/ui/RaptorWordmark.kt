package com.phoenix.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phoenix.uitheme.DisplayFont
import com.phoenix.uitheme.PhoenixShapes
import com.phoenix.uitheme.PhoenixTheme

/** Text-only header wordmark: RAPTOR, an angled accent slash, and the VELOCIRAPTOR 600 / HPE600 badge. */
@Composable
fun RaptorWordmark(modifier: Modifier = Modifier) {
    val palette = PhoenixTheme.palette
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("RAPTOR", fontFamily = DisplayFont, fontSize = 40.sp, letterSpacing = 6.sp, color = palette.text, maxLines = 1)
        Spacer(Modifier.width(14.dp))
        Box(Modifier.width(18.dp).height(44.dp).background(palette.accent, Slash))
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("VELOCIRAPTOR 600", fontFamily = DisplayFont, fontSize = 17.sp, letterSpacing = 2.sp, color = palette.silver, maxLines = 1)
            Text(
                "HPE600",
                fontFamily = DisplayFont,
                fontSize = 13.sp,
                letterSpacing = 2.sp,
                color = palette.text,
                maxLines = 1,
                modifier = Modifier.background(palette.badgeRed, PhoenixShapes.Chip).padding(horizontal = 8.dp, vertical = 1.dp),
            )
        }
    }
}

/** Parallelogram leaning forward. */
private val Slash = GenericShape { size, _ ->
    val lean = size.width * 0.55f
    moveTo(lean, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width - lean, size.height)
    lineTo(0f, size.height)
    close()
}
