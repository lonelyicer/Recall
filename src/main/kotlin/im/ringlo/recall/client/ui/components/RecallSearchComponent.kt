package im.ringlo.recall.client.ui.components

import com.mojang.blaze3d.platform.InputConstants
import im.ringlo.recall.client.ui.RecallDraw
import im.ringlo.recall.client.ui.UiRect
import im.ringlo.recall.client.ui.components.layout.LayoutBox
import im.ringlo.recall.client.ui.components.navigation.RecallNavigationSession
import im.ringlo.recall.content.ClientContentStore
import im.ringlo.recall.content.ContentSnapshot
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent

class RecallSearchComponent(
    private val font: Font,
    private val session: RecallNavigationSession,
    private val onHit: (ContentSnapshot.MapDefinition, ContentSnapshot.MapNode) -> Unit,
) : BaseComponent("search") {
    private var query = ""
    private var searchFocused = false
    private var cachedQuery = ""
    private var revision = Long.MIN_VALUE
    private var results: List<Pair<ContentSnapshot.MapDefinition, ContentSnapshot.MapNode>> =
        emptyList()
    private val popupTop
        get() = bounds.y - results.size * 16 - 6

    override fun layout(box: LayoutBox) {
        super.layout(box)
        refreshResults()
    }

    override fun paint(gui: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        gui.nextStratum()
        refreshResults()
        gui.fill(bounds.x, bounds.y, bounds.right, bounds.bottom, 0xE0000000.toInt())
        gui.outline(bounds.x, bounds.y, bounds.width, bounds.height, 0xFFB0B0B0.toInt())
        val text = if (query.isBlank()) "SEARCH" else query
        gui.enableScissor(bounds.x + 7, bounds.y + 1, bounds.right - 7, bounds.bottom - 1)
        gui.text(
            font,
            font.plainSubstrByWidth(text, bounds.width - 14),
            bounds.x + 7,
            bounds.y + 6,
            if (query.isBlank()) 0x8890909A.toInt() else RecallDraw.TEXT,
        )
        if (searchFocused && System.nanoTime() / 500_000_000L % 2 == 0L)
            gui.fill(
                bounds.x + 7 + font.width(query).coerceAtMost(bounds.width - 15),
                bounds.y + 5,
                bounds.x + 8 + font.width(query).coerceAtMost(bounds.width - 15),
                bounds.y + 14,
                RecallDraw.TEXT,
            )
        gui.disableScissor()
        if (results.isEmpty()) return
        gui.fill(bounds.x, popupTop, bounds.right, bounds.y - 2, 0xD0101010.toInt())
        results.forEachIndexed { index, (_, node) ->
            val row = resultRect(index)
            if (row.contains(mouseX.toFloat(), mouseY.toFloat()))
                RecallDraw.rect(gui, row, 0x70404040)
            RecallDraw.drawItem(gui, node.item, UiRect(bounds.x + 5f, row.top + 1f, 14f, 14f))
            RecallDraw.text(
                gui,
                font,
                font.plainSubstrByWidth(node.title, bounds.width - 26),
                bounds.x + 23f,
                row.top + 3f,
                RecallDraw.TEXT,
            )
        }
    }

    private fun resultRect(index: Int) =
        UiRect(bounds.x + 2f, popupTop + 2f + index * 16f, bounds.width - 4f, 14f)

    override fun hitTest(x: Double, y: Double) =
        bounds.contains(x, y) ||
            results.isNotEmpty() &&
                x >= bounds.x &&
                x < bounds.right &&
                y >= popupTop &&
                y < bounds.y

    fun blurOutside(x: Double, y: Double) {
        if (!hitTest(x, y)) searchFocused = false
    }

    private fun refreshResults() {
        val snapshot = ClientContentStore.snapshot()
        val normalized = query.trim().lowercase()
        if (revision == snapshot.revision && cachedQuery == normalized) return
        revision = snapshot.revision
        cachedQuery = normalized
        if (normalized.isBlank()) {
            results = emptyList()
            return
        }
        val articles = snapshot.articles
        val maps =
            snapshot.guides.values
                .asSequence()
                .flatMap { it.maps.asSequence() }
                .mapNotNull(snapshot.maps::get)
        results =
            maps
                .flatMap { map ->
                    map.nodes
                        .asSequence()
                        .filter { node ->
                            node.title.contains(normalized, true) ||
                                node.summary.contains(normalized, true) ||
                                node.articleIds.any { id ->
                                    articles[id]?.let {
                                        it.title.contains(normalized, true) ||
                                            it.body.contains(normalized, true) ||
                                            it.tags.any { tag -> tag.contains(normalized, true) }
                                    } == true
                                }
                        }
                        .map { map to it }
                }
                .take(8)
                .toList()
    }

    private fun activate(result: Pair<ContentSnapshot.MapDefinition, ContentSnapshot.MapNode>) {
        query = ""
        searchFocused = false
        refreshResults()
        onHit(result.first, result.second)
    }

    override fun onMouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() != 0) return false
        results.forEachIndexed { index, result ->
            if (resultRect(index).contains(event.x().toFloat(), event.y().toFloat())) {
                activate(result)
                return true
            }
        }
        searchFocused = true
        return true
    }

    override fun onKeyPressed(event: KeyEvent): Boolean {
        if (!searchFocused) return false
        when (event.key()) {
            InputConstants.KEY_BACKSPACE -> query = query.dropLast(1)
            InputConstants.KEY_ESCAPE -> searchFocused = false
            InputConstants.KEY_RETURN -> results.firstOrNull()?.let(::activate)
            else -> return false
        }
        refreshResults()
        return true
    }

    override fun onCharTyped(event: CharacterEvent): Boolean {
        if (!searchFocused || !event.isAllowedChatCharacter || query.length >= 80) return false
        query += event.codepointAsString()
        refreshResults()
        return true
    }
}
