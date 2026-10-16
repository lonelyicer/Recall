package im.ringlo.recall.client.ui.components.nodecard

import im.ringlo.recall.client.ui.RecallDraw
import im.ringlo.recall.client.ui.UiRect
import im.ringlo.recall.client.ui.components.BaseComponent
import im.ringlo.recall.client.ui.components.layout.LayoutBox
import im.ringlo.recall.client.ui.components.navigation.RecallNavigationSession
import im.ringlo.recall.content.ContentSnapshot
import kotlin.math.min
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

class ArticleListComponent(
    private val font: Font,
    private val session: RecallNavigationSession,
    private val snapshot: () -> ContentSnapshot,
    private val accordion: ArticleAccordion,
) : BaseComponent("article-list") {
    private val bodies = linkedMapOf<Identifier, ArticleBodyComponent>()
    private var measured = NodeCardLayout(0f, emptyList(), 0f, 0f)
    private var opacity = 1f
    private var dragging = false
    private var dragOffset = 0f

    fun clear() {
        bodies.values.toList().forEach(::remove)
        bodies.clear()
        measured = NodeCardLayout(0f, emptyList(), 0f, 0f)
        dragging = false
    }

    fun measure(
        articles: List<ContentSnapshot.Article>,
        width: Int,
        availableHeight: Float,
    ): NodeCardLayout {
        val ids = articles.map { it.id }.toSet()
        bodies.keys.filter { it !in ids }.forEach { bodies.remove(it)?.let(::remove) }
        val maxHeight = (availableHeight - 32f).coerceAtLeast(0f)
        val chrome = articles.size * 50f
        val capacity = (maxHeight - 94f - 16f - chrome).coerceAtLeast(0f)
        var top = 0f
        var animatedHeight = 0f
        val rows = articles.map { article ->
            val body =
                bodies.getOrPut(article.id) {
                    add(
                        ArticleBodyComponent(font, snapshot, article.id) { offset ->
                            measured.rows
                                .firstOrNull { it.article.id == article.id }
                                ?.let { row ->
                                    session.articleScroll =
                                        (row.bodyTop + offset).coerceIn(0f, measured.maxScroll)
                                }
                        }
                    )
                }
            val fullHeight = body.measure(article, (width - 36).coerceAtLeast(1))
            val progress = accordion.progress(article.id)
            animatedHeight += min(fullHeight, capacity) * progress
            NodeCardRow(article, top, fullHeight * progress).also { top += 50f + it.bodyHeight }
        }
        val height =
            min(maxHeight, 94f + (chrome + min(capacity, animatedHeight)).coerceAtLeast(12f) + 16f)
        return NodeCardLayout(
            top.coerceAtLeast(12f),
            rows,
            height,
            (height - 110f).coerceAtLeast(0f),
        )
    }

    fun place(card: UiRect, layout: NodeCardLayout, alpha: Float, interactive: Boolean) {
        measured = layout
        opacity = alpha
        enabled = interactive
        val content =
            UiRect(
                card.left + 12f,
                card.top + 94f,
                (card.width - 24f).coerceAtLeast(0f),
                (card.height - 110f).coerceAtLeast(0f),
            )
        layout(
            LayoutBox(
                content.left.toInt(),
                content.top.toInt(),
                content.width.toInt(),
                content.height.toInt(),
            )
        )
        clip = content
        visible = alpha > 0.001f && content.height > 0f
        session.articleScroll = session.articleScroll.coerceIn(0f, layout.maxScroll)
        bodies.values.forEach { it.visible = false }
        layout.rows.forEach { row ->
            val body = bodies.getValue(row.article.id)
            val y = bounds.y + row.bodyTop - session.articleScroll
            val bodyVisible = row.bodyHeight > 0.01f && visible && opacity > 0.001f
            body.visible = bodyVisible
            body.enabled = interactive && bodyVisible && row.article.id == session.selectedArticleId
            body.opacity = alpha * accordion.progress(row.article.id)
            body.clip = UiRect(bounds.x.toFloat(), y, bounds.width.toFloat(), row.bodyHeight)
            body.layout(
                LayoutBox(
                    bounds.x + 6,
                    y.toInt(),
                    (bounds.width - 12).coerceAtLeast(1),
                    row.bodyHeight.toInt(),
                )
            )
        }
    }

    override fun paint(gui: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        measured.rows.forEach { row ->
            val y = bounds.y + row.top - session.articleScroll
            if (y + 44f < bounds.y || y > bounds.bottom) return@forEach
            val rect = UiRect(bounds.x.toFloat(), y, bounds.width.toFloat(), 44f)
            val hovered =
                enabled &&
                    rect.contains(mouseX.toFloat(), mouseY.toFloat()) &&
                    bounds.contains(mouseX.toDouble(), mouseY.toDouble())
            RecallDraw.rect(
                gui,
                rect,
                RecallDraw.withAlpha(if (hovered) 0x40C0C0FF else RecallDraw.CARD_INNER, opacity),
            )
            RecallDraw.text(
                gui,
                font,
                Component.literal(row.article.title).withStyle(ChatFormatting.BOLD),
                rect.left + 10f,
                y + 7f,
                RecallDraw.withAlpha(if (hovered) RecallDraw.ACCENT else RecallDraw.TEXT, opacity),
            )
            gui.enableScissor(
                bounds.x + 10,
                (y + 22f).toInt(),
                bounds.right - 18,
                (y + 43f).toInt(),
            )
            gui.textWithWordWrap(
                font,
                Component.literal(row.article.summary),
                bounds.x + 10,
                (y + 23f).toInt(),
                (bounds.width - 28).coerceAtLeast(1),
                RecallDraw.withAlpha(RecallDraw.MUTED, opacity),
            )
            gui.disableScissor()
            RecallDraw.text(
                gui,
                font,
                if (session.selectedArticleId == row.article.id) "-" else "+",
                rect.right - 18f,
                y + 13f,
                RecallDraw.withAlpha(RecallDraw.ACCENT, opacity),
            )
        }
    }

    fun drawScrollbar(gui: GuiGraphicsExtractor, card: UiRect) {
        if (!visible || measured.maxScroll <= 0f || bounds.height <= 0) return
        val height = thumbHeight()
        val top = bounds.y + (bounds.height - height) * session.articleScroll / measured.maxScroll
        RecallDraw.rect(
            gui,
            UiRect(card.right - 8f, bounds.y.toFloat(), 2f, bounds.height.toFloat()),
            0x40505060,
        )
        RecallDraw.rect(
            gui,
            UiRect(card.right - 8f, top, 2f, height),
            RecallDraw.withAlpha(0xD0B6B6C0.toInt(), opacity),
        )
    }

    fun beginScrollbar(x: Float, y: Float, card: UiRect): Boolean {
        if (
            !enabled ||
                measured.maxScroll <= 0f ||
                x < card.right - 13f ||
                x >= card.right - 1f ||
                y < bounds.y ||
                y >= bounds.bottom
        )
            return false
        val height = thumbHeight()
        val top = bounds.y + (bounds.height - height) * session.articleScroll / measured.maxScroll
        dragOffset = if (y in top..(top + height)) y - top else height / 2f
        dragging = true
        dragScrollbar(y)
        return true
    }

    fun dragScrollbar(y: Float): Boolean {
        if (!dragging) return false
        val travel = bounds.height - thumbHeight()
        session.articleScroll =
            if (travel <= 0f) 0f
            else ((y - bounds.y - dragOffset) / travel).coerceIn(0f, 1f) * measured.maxScroll
        return true
    }

    fun endScrollbar() {
        dragging = false
    }

    private fun thumbHeight() =
        (bounds.height * bounds.height / measured.contentHeight).coerceIn(
            min(8f, bounds.height.toFloat()),
            bounds.height.toFloat(),
        )

    override fun onMouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() != 0) return false
        val y = event.y() - bounds.y + session.articleScroll
        measured.rows
            .firstOrNull { y >= it.top && y < it.top + 44f }
            ?.let { session.toggleArticle(it.article) }
        return true
    }

    override fun onMouseScrolled(
        mouseX: Double,
        mouseY: Double,
        scrollX: Double,
        scrollY: Double,
    ): Boolean {
        scroll(scrollY)
        return true
    }

    fun scroll(wheel: Double) {
        session.articleScroll =
            (session.articleScroll - wheel.toFloat() * 22f).coerceIn(0f, measured.maxScroll)
    }
}
