package im.ringlo.recall.client.ui.components.map

import im.ringlo.recall.client.ui.UiRect
import kotlin.math.min
import net.minecraft.client.gui.GuiGraphicsExtractor

class MapViewport {
    var width = 0
        private set

    var height = 0
        private set

    var scale = 1f
        private set

    var offsetX = 0f
        private set

    var offsetY = 0f
        private set

    fun resize(width: Int, height: Int) {
        if (this.width == width && this.height == height) return
        if (this.width == 0 || this.height == 0) {
            this.width = width
            this.height = height
            fit()
            return
        }
        val centerWorldX = worldX(this.width / 2.0)
        val centerWorldY = worldY(this.height / 2.0)
        this.width = width
        this.height = height
        offsetX = width / 2f - centerWorldX * scale
        offsetY = height / 2f - centerWorldY * scale
    }

    fun fit() {
        scale = min((width - 32f) / 720f, (height - 32f) / 500f).coerceIn(0.60f, 1.15f)
        offsetX = (width - 720f * scale) / 2f
        offsetY = (height - 500f * scale) / 2f
    }

    fun worldX(x: Double): Float = ((x - offsetX) / scale).toFloat()

    fun worldY(y: Double): Float = ((y - offsetY) / scale).toFloat()

    fun pan(dx: Float, dy: Float) {
        offsetX += dx
        offsetY += dy
    }

    fun focus(rect: UiRect) {
        offsetX = width / 2f - rect.centerX * scale
        offsetY = height / 2f - rect.centerY * scale
    }

    fun zoom(x: Double, y: Double, wheel: Double) {
        val wx = worldX(x)
        val wy = worldY(y)
        scale = (scale + wheel.toFloat() * 0.08f).coerceIn(0.60f, 1.45f)
        offsetX = x.toFloat() - wx * scale
        offsetY = y.toFloat() - wy * scale
    }

    fun cardViewport(): UiRect =
        UiRect(worldX(0.0), worldY(0.0), width / scale, (height - 34).coerceAtLeast(0) / scale)

    fun screenRect(rect: UiRect): UiRect =
        UiRect(
            rect.left * scale + offsetX,
            rect.top * scale + offsetY,
            rect.width * scale,
            rect.height * scale,
        )

    fun push(gui: GuiGraphicsExtractor) {
        gui.pose().pushMatrix()
        gui.pose().translate(offsetX, offsetY)
        gui.pose().scale(scale)
    }

    fun pop(gui: GuiGraphicsExtractor) {
        gui.pose().popMatrix()
    }
}
