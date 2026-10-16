package im.ringlo.recall.client.ui.components

import com.mojang.blaze3d.platform.InputConstants
import im.ringlo.recall.client.tooltip.RecallItemIndex
import im.ringlo.recall.client.ui.components.layout.LayoutBox
import im.ringlo.recall.client.ui.components.map.MapCanvasComponent
import im.ringlo.recall.client.ui.components.map.MapViewport
import im.ringlo.recall.client.ui.components.navigation.RecallNavigationComponent
import im.ringlo.recall.client.ui.components.navigation.RecallNavigationSession
import im.ringlo.recall.client.ui.components.nodecard.NodeCardComponent
import im.ringlo.recall.client.ui.components.scene.SceneFullscreenComponent
import im.ringlo.recall.client.ui.components.scene.ScenePreviewComponent
import im.ringlo.recall.content.ClientContentStore
import im.ringlo.recall.content.ContentSnapshot
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.resources.Identifier

class RecallRoot(font: Font) : BaseComponent("recall-root") {
    private val session = RecallNavigationSession()
    private var revision = Long.MIN_VALUE
    private var laidOutWidth = -1
    private var laidOutHeight = -1

    private val viewport = MapViewport()
    private val map =
        add(MapCanvasComponent(font, session, ClientContentStore::snapshot, viewport, ::selectNode))
    private val navigation =
        add(RecallNavigationComponent(font, session, ::selectGuide, ::selectMap))
    private val card =
        add(NodeCardComponent(font, session, ClientContentStore::snapshot, viewport, map::nodeRect))
    private val header = add(RecallHeaderComponent(font, session, ClientContentStore::snapshot))
    private val search = add(RecallSearchComponent(font, session, ::focusSearchResult))
    private val fullscreen = add(SceneFullscreenComponent())

    fun openItem(item: Identifier) {
        val (definition, node) = RecallItemIndex.find(item) ?: return
        val snapshot = ClientContentStore.snapshot()
        session.selectMap(definition, snapshot)
        invalidateAll()
        layoutIfNeeded(bounds.width, bounds.height)
        viewport.focus(map.nodeRect(node))
        session.selectNode(node)
        node.articleIds.singleOrNull()?.let(snapshot.articles::get)?.let(session::selectArticle)
        invalidateAll()
    }

    fun isItemSubject(item: Identifier): Boolean =
        session.currentNode(ClientContentStore.snapshot())?.item == item

    fun toggleSceneFullscreen(scene: ScenePreviewComponent) {
        if (scene.expanded) fullscreen.close() else fullscreen.open(scene)
    }

    init {
        map.connectCard(card)
        navigation.nodeAt = map::containsNode
    }

    fun layoutIfNeeded(width: Int, height: Int) {
        val snapshot = ClientContentStore.snapshot()
        if (revision != snapshot.revision) {
            revision = snapshot.revision
            session.install(snapshot)
            laidOutWidth = -1
            laidOutHeight = -1
            navigation.invalidate()
            map.invalidate()
            card.invalidate()
            search.invalidate()
        }
        if (width != laidOutWidth || height != laidOutHeight || isDirty()) {
            layout(LayoutBox(0, 0, width, height))
            laidOutWidth = width
            laidOutHeight = height
        }
    }

    override fun layout(box: LayoutBox) {
        super.layout(box)
        map.layout(LayoutBox(box.x, box.y, box.width, box.height))
        navigation.layout(
            LayoutBox(
                box.x + 16,
                box.y + 24,
                minOf(168, (box.width - 32).coerceAtLeast(0)),
                (box.height - 48).coerceAtLeast(0),
            )
        )
        header.layout(LayoutBox(box.x + 16, box.y + 16, (box.width - 32).coerceAtLeast(0), 44))
        card.layout(LayoutBox(box.x, box.y, box.width, box.height))
        search.layout(LayoutBox(box.x + (box.width - 128) / 2, box.bottom - 26, 128, 20))
        fullscreen.layout(LayoutBox(box.x, box.y, box.width, box.height))
    }

    override fun paint(gui: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        gui.fill(0, 0, bounds.width, bounds.height, 0xB0101012.toInt())
    }

    override fun frame(seconds: Float) {
        super.frame(seconds)
        map.advanceLayout(seconds)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (!fullscreen.visible) search.blurOutside(event.x(), event.y())
        return super.mouseClicked(event, doubleClick)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (super.keyPressed(event)) return true
        if (event.key() == InputConstants.KEY_ESCAPE) {
            when {
                session.selectedArticleId != null -> session.collapseArticle()
                session.selectedNodeId != null -> session.clearNode()
                else -> return false
            }
            card.invalidate()
            return true
        }
        return false
    }

    private fun selectGuide(guide: ContentSnapshot.Guide) {
        session.selectGuide(guide)
        invalidateAll()
    }

    private fun selectMap(mapDefinition: ContentSnapshot.MapDefinition) {
        session.selectMap(mapDefinition, ClientContentStore.snapshot())
        invalidateAll()
    }

    private fun invalidateAll() {
        map.invalidate()
        navigation.invalidate()
        header.invalidate()
        card.invalidate()
        search.invalidate()
    }

    private fun selectNode(node: ContentSnapshot.MapNode) {
        session.selectNode(node)
        map.invalidate()
        card.invalidate()
    }

    private fun focusSearchResult(
        mapDefinition: ContentSnapshot.MapDefinition,
        node: ContentSnapshot.MapNode,
    ) {
        session.selectMap(mapDefinition, ClientContentStore.snapshot())
        map.invalidate()
        map.focusNode(node)
        invalidateAll()
    }
}
