package im.ringlo.recall.client.ui.components.nodecard

import im.ringlo.recall.client.ui.RecallDraw
import im.ringlo.recall.client.ui.UiRect
import im.ringlo.recall.client.ui.components.BaseComponent
import im.ringlo.recall.client.ui.components.layout.LayoutBox
import im.ringlo.recall.client.ui.components.nodecard.markdown.*
import im.ringlo.recall.client.ui.components.scene.ScenePreviewComponent
import im.ringlo.recall.content.ContentSnapshot
import java.net.URI
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.Util
import org.commonmark.node.Node

class ArticleBodyComponent(
    private val font: Font,
    private val snapshot: () -> ContentSnapshot,
    private val id: Identifier,
    private val onAnchor: (Float) -> Unit = {},
) : BaseComponent("article-body:$id") {
    private var source: String? = null
    private var document: Node? = null
    private var measuredWidth = -1
    private var imageRevision = Long.MIN_VALUE
    private var markdown = MarkdownLayout(emptyList(), 0f, emptyMap())
    private val scenes = mutableListOf<Pair<MarkdownElement.Scene, ScenePreviewComponent>>()
    private var pendingLink: String? = null
    var contentHeight = 0f
        private set

    var opacity = 1f

    fun measure(value: ContentSnapshot.Article, width: Int): Float {
        if (source != value.body) {
            source = value.body
            document = MarkdownParser.parse(value.body)
            measuredWidth = -1
            pendingLink = null
        }
        if (measuredWidth == width && imageRevision == MarkdownImages.revision) return contentHeight
        measuredWidth = width
        imageRevision = MarkdownImages.revision
        markdown =
            MarkdownLayoutEngine(font, id.namespace)
                .layout(checkNotNull(document), width.coerceAtLeast(1))
        val previous = scenes.toMutableList()
        scenes.clear()
        markdown.elements.filterIsInstance<MarkdownElement.Scene>().forEach { element ->
            val index = previous.indexOfFirst { it.first.id == element.id }
            val component =
                if (index >= 0) previous.removeAt(index).second
                else add(ScenePreviewComponent(font) { snapshot().scenes[element.id] })
            scenes += element to component
        }
        previous.forEach { remove(it.second) }
        contentHeight = markdown.height
        return contentHeight
    }

    override fun layout(box: LayoutBox) {
        super.layout(box)
        scenes.forEach { (element, scene) ->
            val rect = element.rect
            val sceneTop = bounds.y + rect.top
            val sceneBottom = sceneTop + rect.height
            val visibleTop = maxOf(clip?.top ?: bounds.y.toFloat(), bounds.y.toFloat())
            val visibleBottom =
                minOf(clip?.bottom ?: bounds.bottom.toFloat(), bounds.bottom.toFloat())
            val visibleHeight =
                (minOf(sceneBottom, visibleBottom) - maxOf(sceneTop, visibleTop)).coerceAtLeast(0f)
            scene.enabled = enabled && visibleHeight > 0.5f
            scene.visible = visibleHeight > 0.5f
            scene.opacity =
                opacity * (visibleHeight / rect.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
            scene.clip =
                UiRect(
                    bounds.x.toFloat(),
                    visibleTop.coerceAtLeast(sceneTop),
                    bounds.width.toFloat(),
                    visibleHeight,
                )
            scene.layout(
                LayoutBox(
                    (bounds.x + rect.left).toInt(),
                    (bounds.y + rect.top).toInt(),
                    rect.width.toInt(),
                    rect.height.toInt(),
                )
            )
        }
    }

    override fun paint(gui: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (opacity <= 0.001f) return
        markdown.elements.forEach { element ->
            val rect = element.rect
            val x = bounds.x + rect.left
            val y = bounds.y + rect.top
            if (clip?.let { y + rect.height < it.top || y > it.bottom } == true) return@forEach
            when (element) {
                is MarkdownElement.Text -> {
                    gui.pose().pushMatrix()
                    gui.pose().translate(x, y)
                    gui.pose().scale(element.scale)
                    gui.text(
                        font,
                        element.text,
                        0,
                        0,
                        RecallDraw.withAlpha(RecallDraw.TEXT, opacity),
                    )
                    gui.pose().popMatrix()
                }
                is MarkdownElement.Fill ->
                    RecallDraw.rect(
                        gui,
                        UiRect(x, y, rect.width, rect.height),
                        RecallDraw.withAlpha(element.color, opacity),
                    )
                is MarkdownElement.Image -> {
                    gui.pose().pushMatrix()
                    try {
                        gui.pose().translate(x, y)
                        gui.pose()
                            .scale(
                                rect.width / element.sourceWidth,
                                rect.height / element.sourceHeight,
                            )
                        gui.blit(
                            RenderPipelines.GUI_TEXTURED,
                            element.texture,
                            0,
                            0,
                            0f,
                            0f,
                            element.sourceWidth,
                            element.sourceHeight,
                            element.sourceWidth,
                            element.sourceHeight,
                            RecallDraw.withAlpha(0xFFFFFFFF.toInt(), opacity),
                        )
                    } finally {
                        gui.pose().popMatrix()
                    }
                }
                is MarkdownElement.Scene ->
                    if (!snapshot().scenes.containsKey(element.id))
                        RecallDraw.text(
                            gui,
                            font,
                            "Missing scene: ${element.id}",
                            x,
                            y,
                            RecallDraw.withAlpha(RecallDraw.MUTED, opacity),
                        )
            }
        }
        if (
            !enabled ||
                !bounds.contains(mouseX.toDouble(), mouseY.toDouble()) ||
                clip?.contains(mouseX.toFloat(), mouseY.toFloat()) == false
        )
            return
        val hovered = linkAt(mouseX.toFloat(), mouseY.toFloat())
        val image = imageAt(mouseX.toFloat(), mouseY.toFloat())
        val hint = image?.title ?: image?.alt?.takeIf(String::isNotBlank)
        val tooltip =
            when {
                hovered != null ->
                    Component.literal(
                            if (pendingLink == hovered) "Click again to open: $hovered" else hovered
                        )
                        .also { if (hint != null) it.append("\n$hint") }
                hint != null -> Component.literal(hint)
                else -> null
            }
        if (tooltip != null) gui.setTooltipForNextFrame(font, tooltip, mouseX, mouseY)
    }

    private fun imageAt(x: Float, y: Float): MarkdownElement.Image? =
        markdown.elements.filterIsInstance<MarkdownElement.Image>().firstOrNull {
            it.rect.contains(x - bounds.x, y - bounds.y)
        }

    private fun linkAt(x: Float, y: Float): String? {
        imageAt(x, y)?.link?.let {
            return it
        }
        val localX = x - bounds.x
        val localY = y - bounds.y
        markdown.elements
            .filterIsInstance<MarkdownElement.Text>()
            .firstOrNull { it.rect.contains(localX, localY) }
            ?.let { line ->
                var advance = line.rect.left
                var destination: String? = null
                line.text.accept { _, style, codePoint ->
                    val character = String(Character.toChars(codePoint))
                    val next =
                        advance +
                            font.width(Component.literal(character).setStyle(style)) * line.scale
                    if (localX >= advance && localX < next) {
                        destination = style.insertion
                        false
                    } else {
                        advance = next
                        true
                    }
                }
                return destination
            }
        return null
    }

    override fun onMouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() != 0) return false
        val link = linkAt(event.x().toFloat(), event.y().toFloat()) ?: return false
        if (link.startsWith('#')) {
            markdown.anchors[link.removePrefix("#")]?.let(onAnchor)
            pendingLink = null
        } else {
            val uri = runCatching { URI(link) }.getOrNull()
            if (uri?.scheme?.lowercase() in setOf("https", "http") && !uri?.host.isNullOrBlank()) {
                if (pendingLink == link) {
                    pendingLink = null
                    Util.getPlatform().openUri(checkNotNull(uri))
                } else pendingLink = link
            }
        }
        return true
    }
}
