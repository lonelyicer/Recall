package im.ringlo.recall.client.ui.components.scene

import kotlin.math.hypot
import kotlin.math.roundToInt
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import org.joml.Vector2f

data class ScenePresentation(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val scale: Float,
    val clip: ScreenRectangle,
) {
    fun localX(x: Double) = (x - this.x) / scale

    fun localY(y: Double) = (y - this.y) / scale

    fun interpolate(target: ScenePresentation, progress: Float): ScenePresentation {
        fun mix(a: Float, b: Float) = a + (b - a) * progress
        fun mix(a: Int, b: Int) = mix(a.toFloat(), b.toFloat()).roundToInt()
        val scale = mix(scale, target.scale)
        return ScenePresentation(
            mix(x, target.x),
            mix(y, target.y),
            mix(width * this.scale, target.width * target.scale) / scale,
            mix(height * this.scale, target.height * target.scale) / scale,
            scale,
            ScreenRectangle(
                mix(clip.left(), target.clip.left()),
                mix(clip.top(), target.clip.top()),
                mix(clip.width(), target.clip.width()).coerceAtLeast(0),
                mix(clip.height(), target.clip.height()).coerceAtLeast(0),
            ),
        )
    }

    companion object {
        fun capture(gui: GuiGraphicsExtractor, bounds: ScreenRectangle): ScenePresentation {
            val origin =
                gui.pose()
                    .transformPosition(bounds.left().toFloat(), bounds.top().toFloat(), Vector2f())
            val scale = hypot(gui.pose().m00(), gui.pose().m01())
            val rect = bounds.transformMaxBounds(gui.pose())
            val clip =
                gui.peekScissorStack()?.intersection(rect)
                    ?: if (gui.peekScissorStack() == null) rect else ScreenRectangle.empty()
            return ScenePresentation(
                origin.x,
                origin.y,
                bounds.width().toFloat(),
                bounds.height().toFloat(),
                scale,
                clip,
            )
        }

        fun fullscreen(width: Int, height: Int): ScenePresentation {
            val availableWidth = (width - 24).coerceAtLeast(1).toFloat()
            val availableHeight = (height - 24).coerceAtLeast(1).toFloat()
            val targetWidth = minOf(availableWidth, availableHeight * 16f / 9f)
            val targetHeight = targetWidth * 9f / 16f
            val x = (width - targetWidth) / 2f
            val y = (height - targetHeight) / 2f
            return ScenePresentation(
                x,
                y,
                targetWidth,
                targetHeight,
                1f,
                ScreenRectangle(
                    x.roundToInt(),
                    y.roundToInt(),
                    targetWidth.roundToInt().coerceAtLeast(1),
                    targetHeight.roundToInt().coerceAtLeast(1),
                ),
            )
        }
    }
}
