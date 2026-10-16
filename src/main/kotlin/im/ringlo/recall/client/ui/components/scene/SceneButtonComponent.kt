package im.ringlo.recall.client.ui.components.scene

import im.ringlo.recall.client.ui.RecallDraw
import im.ringlo.recall.client.ui.components.BaseComponent
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.MouseButtonEvent

class SceneButtonComponent(
    private val font: Font,
    private val label: () -> String,
    private val action: () -> Unit,
    private val opacity: () -> Float = { 1f },
) : BaseComponent("scene-button") {
    override fun paint(
        gui: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        val alpha = opacity().coerceIn(0f, 1f)
        if (alpha <= 0.001f) return
        val hovered = bounds.contains(mouseX.toDouble(), mouseY.toDouble())
        val color =
            RecallDraw.withAlpha(
                if (hovered) RecallDraw.TEXT else RecallDraw.MUTED,
                alpha,
            )
        val text = label()
        gui.text(
            font,
            text,
            bounds.x + (bounds.width - font.width(text)) / 2,
            bounds.y + (bounds.height - font.lineHeight) / 2,
            color,
        )
    }

    override fun onMouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() != 0 || opacity() <= 0.001f) return false
        action()
        return true
    }
}
