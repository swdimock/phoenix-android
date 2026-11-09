package com.phoenix.uitheme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Russo One (SIL OFL 1.1, see assets/licenses/RussoOne-OFL.txt) for wordmarks, headings and readouts. */
val DisplayFont = FontFamily(Font(R.font.russo_one_regular, FontWeight.Normal))

/** Body text stays on the system sans for legibility. */
val BodyFont = FontFamily.Default

object PhoenixTypography {
    private val Base = Typography()

    /** Larger-than-default type for glanceable use while driving. Headings and labels use [DisplayFont]. */
    val Car = Typography(
        displaySmall = Base.displaySmall.copy(fontFamily = DisplayFont),
        headlineMedium = Base.headlineMedium.copy(fontFamily = DisplayFont, fontSize = 34.sp, lineHeight = 44.sp, letterSpacing = 1.sp),
        titleLarge = Base.titleLarge.copy(fontFamily = DisplayFont, fontSize = 28.sp, lineHeight = 38.sp, letterSpacing = 1.sp),
        titleMedium = Base.titleMedium.copy(fontFamily = BodyFont, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = Base.bodyLarge.copy(fontFamily = BodyFont, fontSize = 22.sp, lineHeight = 30.sp),
        bodyMedium = Base.bodyMedium.copy(fontFamily = BodyFont, fontSize = 18.sp, lineHeight = 26.sp),
        labelLarge = Base.labelLarge.copy(fontFamily = DisplayFont, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 1.sp),
    )
}
