package im.ringlo.recall.client.ui.components.scene

import im.ringlo.recall.client.ui.RecallDraw
import im.ringlo.recall.client.ui.components.BaseComponent
import im.ringlo.recall.client.ui.components.layout.LayoutBox
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

class SceneToolbarComponent(
    private val font: Font,
    private val title: () -> String,
    private val playing: () -> Boolean,
    private val expanded: () -> Boolean,
    private val reset: () -> Unit,
    private val togglePlaying: () -> Unit,
    private val toggleFullscreen: () -> Unit,
    private val opacity: () -> Float = { 1f },
) : BaseComponent("scene-toolbar") {
    private val resetButton = add(SceneButtonComponent(font, { "|<" }, reset, opacity))
    private val playbackButton =
        add(SceneButtonComponent(font, { if (playing()) "||" else ">" }, togglePlaying, opacity))
    private val fullscreenButton =
        add(
            SceneButtonComponent(
                font,
                { if (expanded()) "><" else "[]" },
                toggleFullscreen,
                opacity,
            )
        )

    override fun layout(box: LayoutBox) {
        super.layout(box)
        fullscreenButton.layout(LayoutBox(bounds.right - 30, bounds.y, 26, bounds.height))
        playbackButton.layout(LayoutBox(bounds.right - 52, bounds.y, 22, bounds.height))
        resetButton.layout(LayoutBox(bounds.right - 74, bounds.y, 22, bounds.height))
    }

    override fun paint(
        gui: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        val alpha = opacity().coerceIn(0f, 1f)
        if (alpha <= 0.001f) return
        gui.text(
            font,
            font.plainSubstrByWidth(title(), (bounds.width - 88).coerceAtLeast(1)),
            bounds.x + 8,
            bounds.y + 7,
            RecallDraw.withAlpha(RecallDraw.TEXT, alpha),
        )
    }
}
