package com.phoenix.hvacbar.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.phoenix.fytipc.climate.AirMode

/**
 * Stroke-drawn climate glyphs in the ISO 2575 idiom (windshield defrost, heated seat, seated figure with
 * airflow arrows). Drawn white on a 48 x 48 viewport; [androidx.compose.material3.Icon] tints them.
 */
internal object HvacGlyphs {
    private const val VIEWPORT = 48f
    private const val STROKE = 3.6f

    /** Fan-shaped windshield with three rising wavy arrows. */
    val FrontDefrost: ImageVector by lazy {
        glyph("front_defrost") {
            stroke {
                moveTo(5f, 37f)
                lineTo(13f, 12f)
                quadTo(24f, 8.5f, 35f, 12f)
                lineTo(43f, 37f)
                quadTo(24f, 42f, 5f, 37f)
                close()
            }
            heatWaves(top = 16f, bottom = 34f)
        }
    }

    /** Seat in profile (backrest on the left) with two rising heat waves over the cushion. */
    val SeatHeat: ImageVector by lazy { glyph("seat_heat") { seatHeat() } }

    /** [SeatHeat] facing the other way, for the passenger side. */
    val SeatHeatMirrored: ImageVector by lazy {
        glyph("seat_heat_mirrored") {
            group(scaleX = -1f, translationX = VIEWPORT) { seatHeat() }
        }
    }

    private fun ImageVector.Builder.seatHeat() {
        // Backrest leaning back, cushion, front lip.
        stroke {
            moveTo(14f, 4f)
            lineTo(9f, 33f)
            lineTo(39f, 33f)
            lineTo(42f, 44f)
        }
        for (x in floatArrayOf(22f, 32f)) {
            stroke {
                moveTo(x, 28f)
                quadTo(x - 5f, 23.5f, x, 19f)
                quadTo(x + 5f, 14.5f, x, 10f)
            }
            stroke {
                moveTo(x - 4f, 12f)
                lineTo(x, 8f)
                lineTo(x + 4f, 12f)
            }
        }
    }

    /** Seated figure with arrows for the given airflow outlets, or null for modes without a picture. */
    fun airflow(mode: AirMode): ImageVector? = when (mode) {
        AirMode.FACE -> Face
        AirMode.FACE_FEET -> FaceFeet
        AirMode.FEET -> Feet
        AirMode.FEET_DEFROST -> FeetDefrost
        AirMode.DEFROST -> Defrost
        AirMode.AUTO, AirMode.UNKNOWN -> null
    }

    private val Face by lazy { airflowGlyph("air_face", face = true, feet = false, windshield = false) }
    private val FaceFeet by lazy { airflowGlyph("air_face_feet", face = true, feet = true, windshield = false) }
    private val Feet by lazy { airflowGlyph("air_feet", face = false, feet = true, windshield = false) }
    private val FeetDefrost by lazy { airflowGlyph("air_feet_defrost", face = false, feet = true, windshield = true) }
    private val Defrost by lazy { airflowGlyph("air_defrost", face = false, feet = false, windshield = true) }

    private fun airflowGlyph(name: String, face: Boolean, feet: Boolean, windshield: Boolean): ImageVector = glyph(name) {
        // Head: filled disc, big enough to read as a person at 40 px.
        fill {
            moveTo(7.5f, 9.5f)
            arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 19.5f, y1 = 9.5f)
            arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 7.5f, y1 = 9.5f)
            close()
        }
        // Torso, thigh and shin, in profile facing right; heavier than the arrows so the figure leads.
        stroke(width = STROKE + 1.2f) {
            moveTo(13.5f, 17f)
            lineTo(12.5f, 31f)
            lineTo(27f, 31f)
            lineTo(27f, 43f)
        }
        if (face) arrow(fromX = 45f, toX = 27f, y = 9.5f)
        if (feet) arrow(fromX = 45f, toX = 34f, y = 43f)
        if (windshield) {
            // Rising arrow from the figure's chest toward the glass (up and forward).
            stroke(width = STROKE - 0.4f) {
                moveTo(25f, 26f)
                lineTo(39f, 9f)
            }
            stroke(width = STROKE - 0.4f) {
                moveTo(31.5f, 10f)
                lineTo(39f, 9f)
                lineTo(38.5f, 17f)
            }
        }
    }

    /** Horizontal arrow from [fromX] to [toX] at [y], chevron at [toX]. */
    private fun ImageVector.Builder.arrow(fromX: Float, toX: Float, y: Float) {
        stroke(width = STROKE - 0.4f) {
            moveTo(fromX, y)
            lineTo(toX, y)
        }
        val dir = if (toX < fromX) 1f else -1f
        stroke(width = STROKE - 0.4f) {
            moveTo(toX + 6f * dir, y - 5f)
            lineTo(toX, y)
            lineTo(toX + 6f * dir, y + 5f)
        }
    }

    /** Three S-shaped arrows rising from [bottom] to [top] at x = 15, 24, 33. */
    private fun ImageVector.Builder.heatWaves(top: Float, bottom: Float) {
        val mid = (top + bottom) / 2f
        for (x in floatArrayOf(15f, 24f, 33f)) {
            stroke {
                moveTo(x, bottom)
                quadTo(x - 5f, mid + 4.5f, x, mid)
                quadTo(x + 5f, mid - 4.5f, x, top + 1f)
            }
            stroke {
                moveTo(x - 4f, top + 4f)
                lineTo(x, top)
                lineTo(x + 4f, top + 4f)
            }
        }
    }

    private fun glyph(name: String, body: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = "hvac_$name",
            defaultWidth = VIEWPORT.dp,
            defaultHeight = VIEWPORT.dp,
            viewportWidth = VIEWPORT,
            viewportHeight = VIEWPORT,
        ).apply(body).build()

    private fun ImageVector.Builder.stroke(width: Float = STROKE, path: PathBuilder.() -> Unit) {
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = path,
        )
    }

    private fun ImageVector.Builder.fill(path: PathBuilder.() -> Unit) {
        path(fill = SolidColor(Color.White), pathFillType = PathFillType.NonZero, pathBuilder = path)
    }
}
