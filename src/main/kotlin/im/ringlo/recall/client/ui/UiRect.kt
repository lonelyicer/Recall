package im.ringlo.recall.client.ui

import kotlin.math.max
import kotlin.math.min

data class UiRect(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float
        get() = left + width

    val bottom: Float
        get() = top + height

    val centerX: Float
        get() = left + width / 2f

    val centerY: Float
        get() = top + height / 2f

    fun contains(x: Float, y: Float): Boolean = x in left..<right && y >= top && y < bottom

    fun clampInside(container: UiRect, padding: Float): UiRect =
        UiRect(
            left =
                (centerX - width / 2f).coerceIn(
                    container.left + padding,
                    max(container.left + padding, container.right - width - padding),
                ),
            top =
                (centerY - height / 2f).coerceIn(
                    container.top + padding,
                    max(container.top + padding, container.bottom - height - padding),
                ),
            width = min(width, container.width - padding * 2f),
            height = min(height, container.height - padding * 2f),
        )

    companion object {
        fun lerp(from: UiRect, to: UiRect, progress: Float): UiRect {
            val t = progress.coerceIn(0f, 1f)
            return UiRect(
                from.left + (to.left - from.left) * t,
                from.top + (to.top - from.top) * t,
                from.width + (to.width - from.width) * t,
                from.height + (to.height - from.height) * t,
            )
        }
    }
}
