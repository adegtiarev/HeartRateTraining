package arg.adegtiarev.heartratetraining.overlay

import android.content.Context
import androidx.core.content.edit
import kotlin.math.roundToInt

/** Fractions of the distance the whole badge can travel inside the usable display. */
internal data class OverlayPlacement(val horizontal: Float = 1f, val vertical: Float = 0f, val scale: Float = 1f) {
    companion object {
        const val MIN_SCALE = 0.75f
        const val MAX_SCALE = 3f
    }
}

internal class OverlayPlacementStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("workout_overlay_placement", Context.MODE_PRIVATE)

    fun read(): OverlayPlacement {
        fun number(key: String, default: Float, min: Float, max: Float): Float =
            preferences.getFloat(key, default).takeIf { it.isFinite() }?.coerceIn(min, max) ?: default
        return OverlayPlacement(number("horizontal", 1f, 0f, 1f), number("vertical", 0f, 0f, 1f),
            number("scale", 1f, OverlayPlacement.MIN_SCALE, OverlayPlacement.MAX_SCALE))
    }

    fun save(value: OverlayPlacement) {
        preferences.edit {
            putFloat("horizontal", value.horizontal)
            putFloat("vertical", value.vertical)
            putFloat("scale", value.scale)
        }
    }
}

internal class OverlayTravel(displaySize: Int, windowSize: Int, private val margin: Int) {
    private val distance = (displaySize - windowSize - 2 * margin).coerceAtLeast(0)
    fun offset(fraction: Float) = margin + (distance * fraction).roundToInt()
    fun fraction(offset: Float, previous: Float) =
        if (distance == 0) previous else ((offset - margin) / distance).coerceIn(0f, 1f)
}
