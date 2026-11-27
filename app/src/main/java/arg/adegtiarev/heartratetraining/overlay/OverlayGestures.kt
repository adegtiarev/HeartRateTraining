package arg.adegtiarev.heartratetraining.overlay

import android.graphics.Rect
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.hypot

/** Screen coordinates keep the gesture stable while the window itself moves under the fingers. */
internal class OverlayGestures(private val view: PulseWindowText) {
    private val slop = ViewConfiguration.get(view.context).scaledTouchSlop
    private var firstId = -1
    private var secondId = -1
    private var startX = 0f
    private var startY = 0f
    private var startSpan = 0f
    private var startScale = 1f
    private var pivotX = 0f
    private var pivotY = 0f
    private val initialBounds = Rect()
    private val location = IntArray(2)
    private var changed = false

    /** Returns true for a completed tap. */
    fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                changed = false
                anchor(event)
            }
            MotionEvent.ACTION_POINTER_DOWN -> anchor(event)
            MotionEvent.ACTION_MOVE -> move(event)
            MotionEvent.ACTION_POINTER_UP -> {
                move(event)
                anchor(event, event.actionIndex)
            }
            MotionEvent.ACTION_UP -> {
                move(event)
                val tap = !changed
                finish()
                return tap
            }
            MotionEvent.ACTION_CANCEL -> finish()
        }
        return false
    }

    fun finish() {
        if (changed) view.savePlacement()
        changed = false
        firstId = -1
        secondId = -1
    }

    private fun anchor(event: MotionEvent, exclude: Int = -1) {
        val first = if (exclude == 0) 1 else 0
        val second = (first + 1 until event.pointerCount).firstOrNull { it != exclude } ?: first
        firstId = event.getPointerId(first)
        secondId = event.getPointerId(second)
        startX = (event.getRawX(first) + event.getRawX(second)) / 2
        startY = (event.getRawY(first) + event.getRawY(second)) / 2
        startSpan = span(event, first, second)
        startScale = view.placement.scale
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            view.getLocationOnScreen(location)
            initialBounds.set(location[0], location[1], location[0] + view.width, location[1] + view.height)
        } else {
            // A finger can lift before WindowManager has drawn the last resize. Rebase on that requested frame.
            view.requestedBoundsOnScreen(initialBounds)
        }
        pivotX = ((startX - initialBounds.left) / initialBounds.width().coerceAtLeast(1)).coerceIn(0f, 1f)
        pivotY = ((startY - initialBounds.top) / initialBounds.height().coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    private fun move(event: MotionEvent) {
        val first = event.findPointerIndex(firstId)
        val second = event.findPointerIndex(secondId)
        if (first < 0 || second < 0) return
        val x = (event.getRawX(first) + event.getRawX(second)) / 2
        val y = (event.getRawY(first) + event.getRawY(second)) / 2
        val span = span(event, first, second)
        if (!changed && hypot(x - startX, y - startY) < slop && kotlin.math.abs(span - startSpan) < slop) return
        changed = true
        if (startSpan > 0f) view.resize((startScale * span / startSpan).coerceIn(OverlayPlacement.MIN_SCALE, OverlayPlacement.MAX_SCALE))
        view.moveOnScreen(x - pivotX * view.params.width, y - pivotY * view.params.height)
    }

    private fun span(event: MotionEvent, first: Int, second: Int) =
        hypot(event.getRawX(second) - event.getRawX(first), event.getRawY(second) - event.getRawY(first))
}
