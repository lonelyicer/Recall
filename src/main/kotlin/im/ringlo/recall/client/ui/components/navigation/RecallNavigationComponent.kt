package im.ringlo.recall.client.ui.components.navigation

import im.ringlo.recall.client.ui.RecallDraw
import im.ringlo.recall.client.ui.UiRect
import im.ringlo.recall.client.ui.components.BaseComponent
import im.ringlo.recall.client.ui.components.layout.LayoutBox
import im.ringlo.recall.content.ClientContentStore
import im.ringlo.recall.content.ContentSnapshot
import kotlin.math.min
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.MouseButtonEvent

class RecallNavigationComponent(
    private val font: Font,
    private val session: RecallNavigationSession,
    private val onGuide: (ContentSnapshot.Guide) -> Unit,
    private val onMap: (ContentSnapshot.MapDefinition) -> Unit,
) : BaseComponent("navigation") {
    var nodeAt: (Double, Double) -> Boolean = { _, _ -> false }

    override fun hitTest(x: Double, y: Double): Boolean = bounds.contains(x, y) && !nodeAt(x, y)

    private var maps: List<ContentSnapshot.MapDefinition> = emptyList()
    private var scroll = 0f
    private var dragging = false
    private var dragOffset = 0f
    private val maxScroll
        get() = (maps.size * 16f - bounds.height).coerceAtLeast(0f)

    private val contentTop
        get() = bounds.y + ((bounds.height - maps.size * 16f) / 2f).coerceAtLeast(0f) - scroll

    private val thumbHeight
        get() =
            if (maxScroll <= 0f) min(16f, bounds.height.toFloat())
            else
                (bounds.height * bounds.height / (maps.size * 16f)).coerceIn(
                    min(6f, bounds.height.toFloat()),
                    bounds.height.toFloat(),
                )

    private val thumbTop
        get() =
            if (maxScroll <= 0f)
                contentTop + maps.indexOfFirst { it.id == session.mapId }.coerceAtLeast(0) * 16f
            else bounds.y + (bounds.height - thumbHeight) * scroll / maxScroll

    override fun layout(box: LayoutBox) {
        super.layout(box)
        val snapshot = ClientContentStore.snapshot()
        maps = session.currentGuide(snapshot)?.maps.orEmpty().mapNotNull(snapshot.maps::get)
        scroll = scroll.coerceIn(0f, maxScroll)
        clip =
            UiRect(
                bounds.x.toFloat(),
                bounds.y.toFloat(),
                bounds.width.toFloat(),
                bounds.height.toFloat(),
            )
    }

    override fun paint(gui: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        gui.nextStratum()
        maps.forEachIndexed { index, map ->
            val row =
                UiRect(bounds.x.toFloat(), contentTop + index * 16f, bounds.width.toFloat(), 16f)
            if (row.bottom < bounds.y || row.top > bounds.bottom) return@forEachIndexed
            val hovered =
                row.contains(mouseX.toFloat(), mouseY.toFloat()) &&
                    bounds.contains(mouseX.toDouble(), mouseY.toDouble())
            val selected = map.id == session.mapId
            RecallDraw.horizontalGradient(
                gui,
                row,
                if (hovered) 0xA0181818.toInt() else if (selected) 0x18181818 else 0x0A181818,
                0x00181818,
            )
            if (hovered)
                RecallDraw.drawItem(
                    gui,
                    map.nodes.firstOrNull()?.item,
                    UiRect(row.left + 3f, row.top + 1f, 14f, 14f),
                )
            RecallDraw.text(
                gui,
                font,
                font.plainSubstrByWidth(map.title, (bounds.width - 23).coerceAtLeast(1)),
                row.left + 20f,
                row.top + 3.5f,
                if (hovered) RecallDraw.TEXT else if (selected) 0x40F1F1F1 else 0x18F1F1F1,
            )
        }
        if (maps.isNotEmpty()) {
            RecallDraw.rect(
                gui,
                UiRect(
                    bounds.x.toFloat(),
                    if (maxScroll > 0) bounds.y.toFloat() else contentTop,
                    1f,
                    min(bounds.height.toFloat(), maps.size * 16f),
                ),
                0x30202020,
            )
            RecallDraw.rect(
                gui,
                UiRect(bounds.x.toFloat(), thumbTop, 1f, thumbHeight),
                if (bounds.contains(mouseX.toDouble(), mouseY.toDouble())) 0xD0C0C0FF.toInt()
                else 0x70C0C0FF,
            )
        }
    }

    override fun onMouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() != 0) return false
        if (maxScroll > 0f && event.x() < bounds.x + 4) {
            dragging = true
            dragOffset =
                if (event.y() >= thumbTop && event.y() < thumbTop + thumbHeight)
                    event.y().toFloat() - thumbTop
                else thumbHeight / 2f
            drag(event.y())
            return true
        }
        val index = ((event.y() - contentTop) / 16f).toInt()
        if (event.y() < contentTop || index !in maps.indices) return false
        if (maps[index].id != session.mapId) onMap(maps[index])
        return true
    }

    private fun drag(y: Double) {
        val travel = bounds.height - thumbHeight
        if (travel > 0f)
            scroll = ((y.toFloat() - bounds.y - dragOffset) / travel).coerceIn(0f, 1f) * maxScroll
    }

    override fun onMouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        if (dragging) drag(event.y())
        return dragging
    }

    override fun onMouseReleased(event: MouseButtonEvent): Boolean {
        dragging = false
        return true
    }

    override fun onMouseScrolled(
        mouseX: Double,
        mouseY: Double,
        scrollX: Double,
        scrollY: Double,
    ): Boolean {
        scroll = (scroll - scrollY.toFloat() * 32f).coerceIn(0f, maxScroll)
        return true
    }
}
