package com.phoenix.launcher.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phoenix.uitheme.DisplayFont
import com.phoenix.uitheme.PhoenixShapes
import com.phoenix.uitheme.PhoenixTheme

/** The truck this launcher is built for. */
object VehicleIdentity {
    const val MAKER = "HENNESSEY"
    const val MODEL = "VELOCIRAPTOR 600"
    const val PACKAGE = "HPE600"

    /** Hennessey build number of this truck. */
    const val BUILD_NUMBER = 15

    val buildLabel: String get() = "No. $BUILD_NUMBER"
}

/**
 * Name of the optional Hennessey logo drawable. The repo ships no Hennessey artwork (trademark); drop your own
 * raster at `launcher/src/main/res/drawable-nodpi/hennessey_logo.png` (or `.webp`, light-on-transparent) and the
 * header and splash pick it up. Without it they fall back to a type-only wordmark.
 */
private const val LOGO_RESOURCE = "hennessey_logo"

/** The Hennessey logo if the build has one, else null. Looked up by name so a missing file is not a build error. */
@SuppressLint("DiscouragedApi")
@Composable
fun rememberHennesseyLogo(): Painter? {
    val context = LocalContext.current
    val id = remember(context) { context.resources.getIdentifier(LOGO_RESOURCE, "drawable", context.packageName) }
    return if (id != 0) painterResource(id) else null
}

/**
 * Header badge: the Hennessey logo (or HENNESSEY wordmark), an angled accent slash, and the VELOCIRAPTOR 600 line
 * with the HPE600 package and this truck's build number.
 */
@Composable
fun HennesseyBadge(modifier: Modifier = Modifier) {
    val palette = PhoenixTheme.palette
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        HennesseyMark(height = 52.dp, wordmarkSize = 36.sp)
        Spacer(Modifier.width(14.dp))
        Box(Modifier.width(18.dp).height(44.dp).background(palette.accent, Slash))
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(VehicleIdentity.MODEL, fontFamily = DisplayFont, fontSize = 17.sp, letterSpacing = 2.sp, color = palette.silver, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Badge(VehicleIdentity.PACKAGE, palette.badgeRed, palette.text)
                Badge(VehicleIdentity.buildLabel, palette.surfaceHigh, palette.silver)
            }
        }
    }
}

/** The logo image at [height], or the HENNESSEY wordmark at [wordmarkSize] when no logo is bundled. */
@Composable
fun HennesseyMark(height: Dp, wordmarkSize: TextUnit, modifier: Modifier = Modifier) {
    val logo = rememberHennesseyLogo()
    if (logo != null) {
        Image(
            painter = logo,
            contentDescription = VehicleIdentity.MAKER,
            contentScale = ContentScale.Fit,
            modifier = modifier.height(height).widthIn(max = height * 6),
        )
    } else {
        Text(
            VehicleIdentity.MAKER,
            fontFamily = DisplayFont,
            fontSize = wordmarkSize,
            letterSpacing = wordmarkSize * 0.14f,
            color = PhoenixTheme.palette.text,
            maxLines = 1,
            modifier = modifier,
        )
    }
}

@Composable
private fun Badge(text: String, background: Color, color: Color) {
    Text(
        text,
        fontFamily = DisplayFont,
        fontSize = 13.sp,
        letterSpacing = 2.sp,
        color = color,
        maxLines = 1,
        modifier = Modifier.background(background, PhoenixShapes.Chip).padding(horizontal = 8.dp, vertical = 1.dp),
    )
}

/** Parallelogram leaning forward. */
internal val Slash = GenericShape { size, _ ->
    val lean = size.width * 0.55f
    moveTo(lean, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width - lean, size.height)
    lineTo(0f, size.height)
    close()
}
