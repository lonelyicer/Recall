package im.ringlo.recall.client.ui.components.nodecard

import im.ringlo.recall.client.ui.RecallDraw
import im.ringlo.recall.client.ui.RecallSpring
import im.ringlo.recall.client.ui.UiRect
import im.ringlo.recall.client.ui.components.BaseComponent
import im.ringlo.recall.client.ui.components.layout.LayoutBox
import im.ringlo.recall.client.ui.components.map.MapViewport
import im.ringlo.recall.client.ui.components.navigation.RecallNavigationSession
import im.ringlo.recall.content.ContentSnapshot
import kotlin.math.max
import kotlin.math.min
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

class NodeCardComponent(
    private val font: Font,
    private val session: RecallNavigationSession,
    private val snapshot: () -> ContentSnapshot,
    private val viewport: MapViewport,
    private val sourceRect: (ContentSnapshot.MapNode) -> UiRect,
) : BaseComponent("node-card") {
    private val expansion = RecallSpring(0.34f)
    private val accordion = ArticleAccordion()
    private val articles = add(ArticleListComponent(font, session, snapshot, accordion))
    private val closing = linkedMapOf<Identifier, ClosingNodeCard>()
    private var activeNode: ContentSnapshot.MapNode? = null
    private var activeMap: Identifier? = null
    private var source = UiRect(0f, 0f, 28f, 28f)
    private var openingViewport = UiRect(0f, 0f, 720f, 500f)
    private var current: UiRect? = null
    private var currentIcon = UiRect(0f, 0f, 24f, 24f)
    private var currentTitle = UiRect(0f, 0f, 0f, 0f)
    private var snapshotRevision = Long.MIN_VALUE

    override fun layout(box: LayoutBox) {
        val resized = bounds.width != box.width || bounds.height != box.height
        super.layout(box)
        if (resized) openingViewport = viewport.cardViewport()
    }

    override fun frame(seconds: Float) {
        val content = snapshot()
        if (activeMap != session.mapId || snapshotRevision != content.revision) {
            activeMap = session.mapId
            snapshotRevision = content.revision
            articles.clear()
            activeNode = null
            current = null
            closing.clear()
            expansion.snap(0f)
            accordion.clear()
        }
        closing.values.forEach { it.frame(seconds) }
        closing.entries.removeIf { it.value.settled }
        val selected = session.currentNode(content)
        if (selected != null && selected.id != activeNode?.id) {
            activeNode?.let { old ->
                current?.let { rect ->
                    closing[old.id] =
                        ClosingNodeCard(
                            old,
                            source,
                            rect,
                            currentIcon,
                            currentTitle,
                            nodeTitle(old, source),
                        )
                }
            }
            val interrupted = closing.remove(selected.id)
            activeNode = selected
            source = interrupted?.source ?: sourceRect(selected)
            openingViewport = viewport.cardViewport()
            expansion.snap(0f)
            expansion.setTarget(1f)
            accordion.clear()
            session.articleScroll = 0f

            reopening = interrupted
        }
        if (selected == null && reopening != null) {
            activeNode?.let { old ->
                current?.let {
                    closing[old.id] =
                        ClosingNodeCard(
                            old,
                            source,
                            it,
                            currentIcon,
                            currentTitle,
                            nodeTitle(old, source),
                        )
                }
            }
            activeNode = null
            current = null
            reopening = null
            expansion.snap(0f)
            accordion.clear()
        }
        expansion.setTarget(if (selected == null) 0f else 1f)
        expansion.tick(seconds)
        accordion.update(session.selectedArticleId, seconds)
        val node = activeNode
        if (node == null || selected == null && expansion.settled() && accordion.settled()) {
            activeNode = null
            current = null
            articles.visible = false
            articles.clear()
            reopening = null
            return
        }
        val width = (openingViewport.width - 32f).coerceIn(1f, 400f)
        val entries = node.articleIds.mapNotNull(content.articles::get)
        val layout = articles.measure(entries, width.toInt(), openingViewport.height)
        val target =
            UiRect(
                (source.centerX - width / 2f).coerceIn(
                    openingViewport.left + 16f,
                    max(openingViewport.left + 16f, openingViewport.right - width - 16f),
                ),
                (source.centerY - layout.height / 2f).coerceIn(
                    openingViewport.top + 16f,
                    max(openingViewport.top + 16f, openingViewport.bottom - layout.height - 16f),
                ),
                width,
                layout.height,
            )
        val progress = expansion.value.coerceIn(0f, 1f)
        if (selected != null && expansion.settled()) reopening = null
        val start = reopening
        val card = UiRect.lerp(start?.card ?: source, target, progress)
        current = card
        val nodeIcon = UiRect(source.centerX - 12f, source.centerY - 12f, 24f, 24f)
        currentIcon =
            UiRect.lerp(
                start?.icon ?: nodeIcon,
                UiRect(card.left + 16f, card.top + 16f, 16f, 16f),
                progress,
            )
        currentTitle =
            UiRect.lerp(
                start?.title ?: nodeTitle(node, source),
                UiRect(card.left + 47f, card.top + 18f, 0f, 0f),
                progress,
            )
        val alpha = RecallDraw.smoothstep((progress - 0.16f) / 0.70f)
        articles.place(card, layout, alpha, selected != null && progress > 0.8f)
        super.frame(seconds)
    }

    private var reopening: ClosingNodeCard? = null

    override fun extract(gui: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        gui.nextStratum()
        viewport.push(gui)
        try {
            super.extract(
                gui,
                viewport.worldX(mouseX.toDouble()).toInt(),
                viewport.worldY(mouseY.toDouble()).toInt(),
                partialTick,
            )
            current?.let { articles.drawScrollbar(gui, it) }
        } finally {
            viewport.pop(gui)
        }
    }

    override fun paint(gui: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        closing.values.forEach { old ->
            RecallDraw.box(gui, old.card)
            RecallDraw.drawItem(gui, old.node.item, old.icon)
            RecallDraw.text(
                gui,
                font,
                title(old.node),
                old.title.left,
                old.title.top,
                RecallDraw.TEXT,
            )
        }
        val node = activeNode ?: return
        val card = current ?: return
        RecallDraw.box(gui, card, card.contains(mouseX.toFloat(), mouseY.toFloat()))
        RecallDraw.drawItem(gui, node.item, currentIcon)
        RecallDraw.text(
            gui,
            font,
            title(node),
            currentTitle.left,
            currentTitle.top,
            RecallDraw.TEXT,
        )
        val alpha = RecallDraw.smoothstep((expansion.value - 0.16f) / 0.70f)
        if (alpha <= 0.001f) return
        gui.enableScissor(
            card.left.toInt(),
            card.top.toInt(),
            card.right.toInt(),
            min(card.bottom, card.top + 92f).toInt(),
        )
        gui.textWithWordWrap(
            font,
            Component.literal(node.summary),
            (card.left + 14f).toInt(),
            (card.top + 50f).toInt(),
            (card.width - 48f).toInt().coerceAtLeast(1),
            RecallDraw.withAlpha(RecallDraw.MUTED, alpha),
        )
        RecallDraw.text(
            gui,
            font,
            "X",
            card.right - 25f,
            card.top + 14f,
            RecallDraw.withAlpha(RecallDraw.MUTED, alpha),
        )
        gui.disableScissor()
    }

    fun visualRect(id: Identifier): UiRect? =
        if (activeNode?.id == id) current else closing[id]?.card

    fun pinned(id: Identifier): Boolean = activeNode?.id == id || id in closing

    fun obstacle(): UiRect? = current?.let {
        val padding = 46f * expansion.value.coerceIn(0f, 1f)
        UiRect(
            it.left - padding,
            it.top - padding,
            it.width + padding * 2f,
            it.height + padding * 2f,
        )
    }

    private fun title(node: ContentSnapshot.MapNode) =
        Component.literal(node.title.ifBlank { node.id.path }).withStyle(ChatFormatting.BOLD)

    private fun nodeTitle(node: ContentSnapshot.MapNode, rect: UiRect) =
        UiRect(rect.centerX - font.width(title(node)) / 2f, rect.bottom + 7f, 0f, 0f)

    private fun world(event: MouseButtonEvent) =
        MouseButtonEvent(
            viewport.worldX(event.x()).toDouble(),
            viewport.worldY(event.y()).toDouble(),
            event.buttonInfo(),
        )

    override fun hitTest(x: Double, y: Double): Boolean =
        current?.contains(x.toFloat(), y.toFloat()) == true ||
            closing.values.any { it.card.contains(x.toFloat(), y.toFloat()) }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val world = world(event)
        if (
            event.button() == 0 &&
                current?.let {
                    articles.beginScrollbar(world.x().toFloat(), world.y().toFloat(), it)
                } == true
        )
            return true
        return super.mouseClicked(world, doubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean =
        articles.dragScrollbar(viewport.worldY(event.y())) ||
            super.mouseDragged(world(event), dragX / viewport.scale, dragY / viewport.scale)

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        articles.endScrollbar()
        return super.mouseReleased(world(event))
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        scrollX: Double,
        scrollY: Double,
    ): Boolean =
        super.mouseScrolled(
            viewport.worldX(mouseX).toDouble(),
            viewport.worldY(mouseY).toDouble(),
            scrollX,
            scrollY,
        )

    override fun onMouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() != 0) return true
        val card = current
        if (card == null || !card.contains(event.x().toFloat(), event.y().toFloat())) {
            closing.values
                .lastOrNull { it.card.contains(event.x().toFloat(), event.y().toFloat()) }
                ?.let { session.selectNode(it.node) }
            return true
        }
        if (session.selectedNodeId == null) {
            activeNode?.let(session::selectNode)
            return true
        }
        if (expansion.value < 0.8f) return true
        if (event.x() >= card.right - 34 && event.y() >= card.top + 8 && event.y() < card.top + 36)
            session.clearNode()
        return true
    }

    override fun onMouseScrolled(
        mouseX: Double,
        mouseY: Double,
        scrollX: Double,
        scrollY: Double,
    ): Boolean {
        articles.scroll(scrollY)
        return true
    }
}
