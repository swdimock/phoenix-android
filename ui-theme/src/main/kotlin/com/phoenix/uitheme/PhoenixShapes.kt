package com.phoenix.uitheme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Angular, chamfered shapes: a large cut on the top-left and bottom-right, a small one on the others. */
object PhoenixShapes {
    fun chamfer(major: Dp, minor: Dp = major / 4): CutCornerShape =
        CutCornerShape(topStart = major, topEnd = minor, bottomEnd = major, bottomStart = minor)

    val Small = chamfer(8.dp)
    val Medium = chamfer(14.dp)
    val Large = chamfer(22.dp)

    /** Status chips and badges. */
    val Chip = CutCornerShape(6.dp)

    val Material = Shapes(
        extraSmall = CutCornerShape(4.dp),
        small = Small,
        medium = Medium,
        large = Large,
        extraLarge = Large,
    )
}
