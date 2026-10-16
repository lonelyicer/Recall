package im.ringlo.recall.client.ui.components

import im.ringlo.recall.client.ui.RecallDraw
import im.ringlo.recall.client.ui.UiRect
import im.ringlo.recall.client.ui.components.navigation.RecallNavigationSession
import im.ringlo.recall.content.ContentSnapshot
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

class RecallHeaderComponent(
    private val font: Font,
    private val session: RecallNavigationSession,
    private val snapshot: () -> ContentSnapshot,
) : BaseComponent("header") {
    override fun paint(gui: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        gui.nextStratum()
        val guide = session.currentGuide(snapshot())
        RecallDraw.drawItem(
            gui,
            guide?.icon,
            UiRect(bounds.x.toFloat(), bounds.y.toFloat(), 40f, 40f),
        )
        val title = guide?.title ?: "RECALL"
        val subtitle = guide?.subtitle.orEmpty()
        gui.pose().pushMatrix()
        gui.pose().translate(bounds.x + 48f, bounds.y + if (subtitle.isBlank()) 16f else 7f)
        gui.pose().scale(1.5f)
        gui.text(
            font,
            font.plainSubstrByWidth(
                title,
                minOf(195, ((bounds.width - 48) / 1.5f).toInt().coerceAtLeast(1)),
            ),
            0,
            0,
            RecallDraw.TEXT,
        )
        gui.pose().popMatrix()
        if (subtitle.isNotBlank())
            gui.text(
                font,
                font.plainSubstrByWidth(subtitle, minOf(292, (bounds.width - 48).coerceAtLeast(1))),
                bounds.x + 48,
                bounds.y + 27,
                RecallDraw.MUTED,
            )
    }
}
