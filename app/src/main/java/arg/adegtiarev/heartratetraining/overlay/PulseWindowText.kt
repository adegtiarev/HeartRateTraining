package arg.adegtiarev.heartratetraining.overlay

import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import arg.adegtiarev.heartratetraining.R
import arg.adegtiarev.heartratetraining.ui.contrastingText
import arg.adegtiarev.heartratetraining.ui.opaqueRgb
import arg.adegtiarev.heartratetraining.ui.pulseDescription
import arg.adegtiarev.heartratetraining.ui.pulseNumber
import arg.adegtiarev.heartratetraining.workout.WorkoutReading
import kotlin.math.ceil
import kotlin.math.roundToInt

internal class PulseWindowText(context: Context) : TextView(context) {
    private var reading = WorkoutReading(null, null)
    var onWindowFailure: (() -> Unit)? = null
    private val manager = context.getSystemService(WindowManager::class.java)
    private val store = OverlayPlacementStore(context)
    var placement = store.read()
        private set
    val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        // The saved horizontal fraction is measured from the physical left edge in every locale.
        @Suppress("RtlHardcoded")
        gravity = Gravity.TOP or Gravity.LEFT
        if (Build.VERSION.SDK_INT >= 34) setCanPlayMoveAnimation(false)
        if (Build.VERSION.SDK_INT >= 30) {
            fitInsetsTypes = WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            setFitInsetsIgnoringVisibility(true)
        }
    }
    private val gestures = OverlayGestures(this)
    private val surface = GradientDrawable()
    private val usable = Rect()
    private val lastBounds = Rect()
    private val digitBounds = Rect()
    private val margin get() = dp(8f)
    private val horizontal get() = OverlayTravel(usable.width(), params.width, margin)
    private val vertical get() = OverlayTravel(usable.height(), params.height, margin)

    init {
        id = R.id.overlay_pulse
        gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        includeFontPadding = false
        setSingleLine()
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        background = surface
        refreshConfiguration()
    }

    fun showReading(next: WorkoutReading) {
        if (next == reading) return
        reading = next
        render()
    }

    fun refreshConfiguration() {
        readUsableSize()
        params.title = context.applicationContext.getString(R.string.overlay_title)
        render()
    }

    private fun render() {
        val color = opaqueRgb(reading.zone?.rgb ?: 0)
        surface.setColor(color.toArgb())
        setTextColor(contrastingText(color).toArgb())
        // A display window can have the system locale even when the rider chose an app language.
        val labels = context.applicationContext.resources
        text = labels.pulseNumber(reading.bpm)
        contentDescription = labels.pulseDescription(reading.bpm, reading.zone)
        sizeToContent()
        place()
    }

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    @Suppress("DEPRECATION")
    private fun readUsableSize() {
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = manager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val bounds = metrics.bounds
            usable.set(bounds.left + insets.left, bounds.top + insets.top,
                bounds.right - insets.right, bounds.bottom - insets.bottom)
        } else {
            usable.setEmpty()
            if (isAttachedToWindow) getWindowVisibleDisplayFrame(usable)
            if (usable.isEmpty) {
                val size = Point()
                manager.defaultDisplay.getSize(size)
                usable.set(0, 0, size.x, size.y)
            }
        }
    }

    private fun sizeToContent() {
        val scale = placement.scale
        val maxWidth = (usable.width() - 2 * margin).coerceAtLeast(1)
        val maxHeight = (usable.height() - 2 * margin).coerceAtLeast(1)
        val padX = dp(16f * scale).coerceAtMost(maxWidth / 4)
        val padY = dp(4.5f * scale).coerceAtMost(maxHeight / 4)
        setPadding(padX, padY, padX, padY)
        val base = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 30f, resources.displayMetrics) * scale
        setTextSize(TypedValue.COMPLEX_UNIT_PX, base)
        // Reserve three digits so a dropout or a change from 99 to 100 leaves the badge in place.
        val textWidth = maxOf(paint.measureText("888"), paint.measureText(text.toString()))
        val textHeight = paint.descent() - paint.ascent()
        val fit = minOf(1f, (maxWidth - 2 * padX) / textWidth, (maxHeight - 2 * padY) / textHeight)
        if (fit < 1f) setTextSize(TypedValue.COMPLEX_UNIT_PX, base * fit)
        params.width = maxOf(dp(48f), dp(64 * scale), ceil(textWidth * fit).toInt() + 2 * padX).coerceAtMost(maxWidth)
        // At large font sizes the line itself supplies part of the visible space around the digits.
        paint.getTextBounds("8", 0, 1, digitBounds)
        val lineHeight = textHeight * fit
        val extraSpace = (lineHeight - digitBounds.height()) / 2
        val paddedHeight = ceil(lineHeight + extraSpace).toInt() + 2 * padY
        params.height = maxOf(dp(48f), dp(60f * scale), paddedHeight).coerceAtMost(maxHeight)
        surface.cornerRadius = dp(8 * scale).toFloat()
    }

    fun resize(scale: Float) {
        if (placement.scale == scale) return
        placement = placement.copy(scale = scale)
        sizeToContent()
    }

    fun moveOnScreen(left: Float, top: Float) {
        placement = placement.copy(horizontal = horizontal.fraction(left - usable.left, placement.horizontal),
            vertical = vertical.fraction(top - usable.top, placement.vertical))
        place()
    }

    fun requestedBoundsOnScreen(bounds: Rect) {
        bounds.set(lastBounds)
        bounds.offset(usable.left, usable.top)
    }

    private fun place() {
        params.x = horizontal.offset(placement.horizontal)
        params.y = vertical.offset(placement.vertical)
        val right = params.x + params.width
        val bottom = params.y + params.height
        if (lastBounds.left == params.x && lastBounds.top == params.y && lastBounds.right == right && lastBounds.bottom == bottom) return
        lastBounds.set(params.x, params.y, right, bottom)
        if (isAttachedToWindow) {
            try {
                manager.updateViewLayout(this, params)
            } catch (_: RuntimeException) {
                onWindowFailure?.invoke()
            }
        }
    }

    fun savePlacement() = store.save(placement)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gestures.onTouch(event)) performClick()
        return true
    }
    override fun performClick() = super.performClick()

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        // On API 29 the visible display frame becomes available only after attachment.
        post {
            if (isAttachedToWindow) {
                val oldBounds = Rect(usable)
                readUsableSize()
                if (oldBounds != usable) {
                    sizeToContent()
                    place()
                }
            }
        }
        return super.onApplyWindowInsets(insets)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshConfiguration()
    }

    override fun onDetachedFromWindow() {
        gestures.finish()
        super.onDetachedFromWindow()
    }
}
