package im.ringlo.recall.client.ui.components.map

import im.ringlo.recall.client.ui.RecallDraw
import im.ringlo.recall.client.ui.RecallSpring
import im.ringlo.recall.client.ui.UiRect
import im.ringlo.recall.client.ui.components.BaseComponent
import im.ringlo.recall.client.ui.components.layout.LayoutBox
import im.ringlo.recall.client.ui.components.navigation.RecallNavigationSession
import im.ringlo.recall.client.ui.components.nodecard.NodeCardComponent
import im.ringlo.recall.content.ContentSnapshot
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

class MapCanvasComponent(
    private val font: Font,
    private val session: RecallNavigationSession,
    private val snapshot: () -> ContentSnapshot,
    private val viewport: MapViewport,
    private val onNode: (ContentSnapshot.MapNode) -> Unit,
) : BaseComponent("map-canvas") {
    private val transition = RecallSpring(0.65f).apply { snap(1f) }
    private var currentMap: ContentSnapshot.MapDefinition? = null
    private var outgoingMap: ContentSnapshot.MapDefinition? = null
    private var outgoingRects: Map<Identifier, UiRect> = emptyMap()
    private var engine: MapLayoutEngine? = null
    private var reverse = false
    private var panning = false
    private var pendingFocus: ContentSnapshot.MapNode? = null
    private var card: NodeCardComponent? = null

    fun connectCard(component: NodeCardComponent) {
        card = component
    }

    override fun layout(box: LayoutBox) {
        super.layout(box)
        viewport.resize(box.width, box.height)
        val content = snapshot()
        val map = session.currentMap(content)
        if (map != currentMap) {
            if (map?.id != currentMap?.id) {
                outgoingMap = currentMap
                outgoingRects = currentMap?.nodes?.associate { it.id to nodeRect(it) }.orEmpty()
                val order = session.currentGuide(content)?.maps.orEmpty()
                val previousIndex = order.indexOf(currentMap?.id)
                val nextIndex = order.indexOf(map?.id)
                reverse = previousIndex >= 0 && nextIndex >= 0 && nextIndex < previousIndex
                transition.snap(if (outgoingMap == null) 1f else 0f)
                transition.setTarget(1f)
                viewport.fit()
            }
            currentMap = map
            engine = map?.let(::MapLayoutEngine)
            panning = false
        }
    }

    override fun frame(seconds: Float) {
        transition.tick(seconds)
        if (transition.settled()) {
            outgoingMap = null
            outgoingRects = emptyMap()
            pendingFocus?.let { node ->
                viewport.focus(nodeRect(node))
                onNode(node)
                pendingFocus = null
            }
        }
    }

    fun advanceLayout(seconds: Float) {
        if (outgoingMap == null)
            engine?.frame(
                seconds,
                { card?.pinned(it) == true || session.selectedNodeId == it },
                card?.obstacle(),
            )
    }

    override fun paint(gui: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val map = currentMap ?: return
        val progress = RecallDraw.smoothstep(transition.value)
        viewport.push(gui)
        try {
            outgoingMap?.let {
                drawLayer(gui, it, outgoingRects, progress, false, 1f - progress, mouseX, mouseY)
            }
            drawLayer(
                gui,
                map,
                map.nodes.associate { it.id to nodeRect(it) },
                progress,
                true,
                if (outgoingMap == null) 1f else progress,
                mouseX,
                mouseY,
            )
        } finally {
            viewport.pop(gui)
        }
    }

    private fun drawLayer(
        gui: GuiGraphicsExtractor,
        map: ContentSnapshot.MapDefinition,
        rects: Map<Identifier, UiRect>,
        progress: Float,
        entering: Boolean,
        alpha: Float,
        mouseX: Int,
        mouseY: Int,
    ) {
        if (alpha <= 0.01f) return
        val visuals = rects.mapValues { (id, rect) ->
            if (entering) card?.visualRect(id) ?: flight(rect, progress, true)
            else flight(rect, progress, false)
        }
        map.edges.forEach { edge ->
            val from = visuals[edge.from] ?: return@forEach
            val to = visuals[edge.to] ?: return@forEach
            val active =
                entering &&
                    (card?.visualRect(edge.from) != null || card?.visualRect(edge.to) != null)
            RecallDraw.drawLine(
                gui,
                from.centerX,
                from.centerY,
                to.centerX,
                to.centerY,
                RecallDraw.withAlpha(
                    if (active) RecallDraw.CONNECTION_ACTIVE else RecallDraw.CONNECTION,
                    alpha,
                ),
            )
        }
        map.nodes.forEach { node ->
            if (entering && card?.visualRect(node.id) != null) return@forEach
            val rect = visuals[node.id] ?: return@forEach
            val hovered =
                entering &&
                    outgoingMap == null &&
                    rect.contains(
                        viewport.worldX(mouseX.toDouble()),
                        viewport.worldY(mouseY.toDouble()),
                    )
            RecallDraw.box(gui, rect, hovered, alpha)
            RecallDraw.drawItem(
                gui,
                node.item,
                UiRect(rect.centerX - 12f, rect.centerY - 12f, 24f, 24f),
                alpha,
            )
            val title =
                Component.literal(node.title.ifBlank { node.id.path })
                    .withStyle(ChatFormatting.BOLD)
            RecallDraw.text(
                gui,
                font,
                title,
                rect.centerX - font.width(title) / 2f,
                rect.bottom + 7f,
                RecallDraw.withAlpha(RecallDraw.TEXT, alpha),
            )
            if (hovered)
                gui.setTooltipForNextFrame(font, RecallDraw.itemStack(node.item), mouseX, mouseY)
        }
    }

    private fun flight(rect: UiRect, progress: Float, entering: Boolean): UiRect {
        if (outgoingMap == null) return rect
        val distance =
            if (reverse) {
                if (entering) 1.7f - 0.7f * progress else 1f - 0.78f * progress
            } else {
                if (entering) 0.22f + 0.78f * progress else 1f + 0.7f * progress
            }
        val scale =
            if (entering) 0.55f + 0.45f * progress
            else if (reverse) 1f - 0.45f * progress else 1f - 0.25f * progress
        return UiRect(
            360f + (rect.centerX - 360f) * distance - rect.width * scale / 2f,
            250f + (rect.centerY - 250f) * distance - rect.height * scale / 2f,
            rect.width * scale,
            rect.height * scale,
        )
    }

    fun nodeRect(node: ContentSnapshot.MapNode): UiRect =
        engine?.rect(node) ?: UiRect(node.x + 18f, node.y + 1f, 28f, 28f)

    fun containsNode(screenX: Double, screenY: Double): Boolean =
        outgoingMap == null &&
            currentMap?.nodes?.any {
                card?.visualRect(it.id) == null &&
                    nodeRect(it).contains(viewport.worldX(screenX), viewport.worldY(screenY))
            } == true

    fun focusNode(node: ContentSnapshot.MapNode) {
        pendingFocus = node
    }

    override fun onMouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (outgoingMap != null) return true
        if (event.button() != 0 && event.button() != 2) return false
        val x = viewport.worldX(event.x())
        val y = viewport.worldY(event.y())
        val node =
            currentMap?.nodes?.firstOrNull {
                card?.visualRect(it.id) == null && nodeRect(it).contains(x, y)
            }
        if (event.button() == 0 && node != null) onNode(node)
        else if (event.button() == 0 && doubleClick) session.clearNode() else panning = true
        return true
    }

    override fun onMouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        if (!panning) return false
        viewport.pan(dragX.toFloat(), dragY.toFloat())
        return true
    }

    override fun onMouseReleased(event: MouseButtonEvent): Boolean {
        panning = false
        return true
    }

    override fun onMouseScrolled(
        mouseX: Double,
        mouseY: Double,
        scrollX: Double,
        scrollY: Double,
    ): Boolean {
        if (outgoingMap == null) viewport.zoom(mouseX, mouseY, scrollY)
        return true
    }
}
