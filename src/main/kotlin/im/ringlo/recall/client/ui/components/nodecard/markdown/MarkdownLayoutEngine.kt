package im.ringlo.recall.client.ui.components.nodecard.markdown

import im.ringlo.recall.client.ui.RecallDraw
import im.ringlo.recall.client.ui.UiRect
import kotlin.math.min
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.tables.*
import org.commonmark.node.*

class MarkdownLayoutEngine(private val font: Font, private val namespace: String) {
    private val elements = mutableListOf<MarkdownElement>()
    private val anchors = linkedMapOf<String, Float>()
    private var y = 0f
    private val normal = Style.EMPTY.withColor(RecallDraw.TEXT)
    private val mono = FontDescription.Resource(Identifier.parse("minecraft:uniform"))

    fun layout(document: Node, width: Int): MarkdownLayout {
        elements.clear()
        anchors.clear()
        y = 0f
        children(document).forEach {
            block(it, 0f, width.toFloat().coerceAtLeast(1f), normal, false)
        }
        return MarkdownLayout(elements.toList(), y + 8f, anchors.toMap())
    }

    private fun children(node: Node): Sequence<Node> = generateSequence(node.firstChild) { it.next }

    private fun box(x: Float, top: Float, width: Float, height: Float, color: Int) {
        elements +=
            MarkdownElement.Fill(
                UiRect(x, top, width.coerceAtLeast(1f), height.coerceAtLeast(1f)),
                color,
            )
    }

    private fun alignedX(
        x: Float,
        width: Float,
        contentWidth: Float,
        alignment: TableCell.Alignment?,
    ) =
        x +
            when (alignment) {
                TableCell.Alignment.CENTER -> (width - contentWidth).coerceAtLeast(0f) / 2f
                TableCell.Alignment.RIGHT -> (width - contentWidth).coerceAtLeast(0f)
                else -> 0f
            }

    private fun text(
        value: Component,
        x: Float,
        width: Float,
        scale: Float = 1f,
        alignment: TableCell.Alignment? = null,
    ) {
        font.split(value, (width / scale).toInt().coerceAtLeast(1)).forEach { line ->
            val lineWidth = font.width(line) * scale
            elements +=
                MarkdownElement.Text(
                    UiRect(alignedX(x, width, lineWidth, alignment), y, lineWidth, 12f * scale),
                    line,
                    scale,
                )
            y += 12f * scale
        }
    }

    private fun block(node: Node, x: Float, width: Float, style: Style, tight: Boolean) {
        when (node) {
            is Heading -> {
                y += 4f
                val slug =
                    plain(node)
                        .lowercase()
                        .replace(Regex("[^\\p{L}\\p{N} _-]"), "")
                        .trim()
                        .replace(Regex("\\s+"), "-")
                var anchor = slug
                var suffix = 1
                while (anchor in anchors) anchor = "$slug-${suffix++}"
                anchors[anchor] = y
                flow(
                    node,
                    x,
                    width,
                    style.withBold(true),
                    when (node.level) {
                        1 -> 1.5f
                        2 -> 1.3f
                        3 -> 1.15f
                        else -> 1f
                    },
                )
                y += 6f
            }
            is Paragraph -> paragraph(node, x, width, style, tight)
            is BlockQuote -> {
                val top = y
                children(node).forEach {
                    block(
                        it,
                        x + 10f,
                        (width - 10f).coerceAtLeast(1f),
                        style.withColor(RecallDraw.MUTED),
                        tight,
                    )
                }
                box(x, top, 2f, (y - top - 4f).coerceAtLeast(12f), RecallDraw.ACCENT)
            }
            is BulletList,
            is OrderedList -> {
                val ordered = node as? OrderedList
                var number = ordered?.markerStartNumber ?: 1
                children(node).forEach { item ->
                    val marker = if (ordered == null) "•" else "${number++}."
                    val indent =
                        (font.width(marker) + 8f).coerceAtLeast(16f).coerceAtMost(width * 0.45f)
                    val top = y
                    elements +=
                        MarkdownElement.Text(
                            UiRect(x, top, indent, 12f),
                            Component.literal(marker).setStyle(style).visualOrderText,
                        )
                    children(item).forEach {
                        block(
                            it,
                            x + indent,
                            (width - indent).coerceAtLeast(1f),
                            style,
                            node.isTight,
                        )
                    }
                    if (y == top) y += 12f
                }
                y += if (tight) 2f else 6f
            }
            is FencedCodeBlock -> code(node.literal, node.info.orEmpty(), x, width)
            is IndentedCodeBlock -> code(node.literal, "", x, width)
            is ThematicBreak -> {
                y += 5f
                box(x, y, width, 1f, RecallDraw.MUTED)
                y += 9f
            }
            is TableBlock -> table(node, x, width, style)
            is HtmlBlock -> {
                text(Component.literal(node.literal).setStyle(style.withFont(mono)), x, width)
                y += 6f
            }
            else -> children(node).forEach { block(it, x, width, style, tight) }
        }
    }

    private fun paragraph(node: Paragraph, x: Float, width: Float, style: Style, tight: Boolean) {
        flow(node, x, width, style, allowScenes = true)
        if (!tight) y += 6f
    }

    private fun flow(
        node: Node,
        x: Float,
        width: Float,
        style: Style,
        scale: Float = 1f,
        alignment: TableCell.Alignment? = null,
        allowScenes: Boolean = false,
    ) {
        var run = Component.empty()
        fun flush() {
            if (run.string.isNotEmpty()) text(run, x, width, scale, alignment)
            run = Component.empty()
        }
        fun append(child: Node, current: Style) {
            when (child) {
                is Image -> {
                    flush()
                    image(child, x, width, current, alignment)
                }
                is Link ->
                    children(child).forEach {
                        append(
                            it,
                            current
                                .withColor(RecallDraw.ACCENT)
                                .withUnderlined(true)
                                .withInsertion(child.destination),
                        )
                    }
                is Emphasis -> children(child).forEach { append(it, current.withItalic(true)) }
                is StrongEmphasis -> children(child).forEach { append(it, current.withBold(true)) }
                is Strikethrough ->
                    children(child).forEach { append(it, current.withStrikethrough(true)) }
                is Text if allowScenes && SCENE.containsMatchIn(child.literal) -> {
                    var cursor = 0
                    SCENE.findAll(child.literal).forEach { match ->
                        run.append(
                            Component.literal(child.literal.substring(cursor, match.range.first))
                                .setStyle(current)
                        )
                        flush()
                        val id = Identifier.tryParse(match.groupValues[1])
                        if (id != null) {
                            val sceneHeight = (width * 9f / 16f).coerceAtLeast(96f)
                            elements += MarkdownElement.Scene(UiRect(x, y, width, sceneHeight), id)
                            y += sceneHeight + 8f
                        } else
                            text(
                                Component.literal(match.value).setStyle(current),
                                x,
                                width,
                                scale,
                                alignment,
                            )
                        cursor = match.range.last + 1
                    }
                    run.append(Component.literal(child.literal.substring(cursor)).setStyle(current))
                }
                else -> appendInline(child, current, run)
            }
        }
        children(node).forEach { append(it, style) }
        flush()
    }

    private fun inline(node: Node, style: Style): MutableComponent =
        Component.empty().also { out -> children(node).forEach { appendInline(it, style, out) } }

    private fun appendInline(node: Node, style: Style, out: MutableComponent) {
        when (node) {
            is Text -> out.append(Component.literal(node.literal).setStyle(style))
            is Code ->
                out.append(
                    Component.literal(node.literal)
                        .setStyle(style.withFont(mono).withColor(0xE4D3B2))
                )
            is SoftLineBreak -> out.append(Component.literal(" ").setStyle(style))
            is HardLineBreak -> out.append(Component.literal("\n").setStyle(style))
            is Emphasis -> out.append(inline(node, style.withItalic(true)))
            is StrongEmphasis -> out.append(inline(node, style.withBold(true)))
            is Strikethrough -> out.append(inline(node, style.withStrikethrough(true)))
            is Link ->
                out.append(
                    inline(
                        node,
                        style
                            .withColor(RecallDraw.ACCENT)
                            .withUnderlined(true)
                            .withInsertion(node.destination),
                    )
                )
            is Image -> out.append(Component.literal(plain(node)).setStyle(style.withItalic(true)))
            is HtmlInline -> out.append(Component.literal(node.literal).setStyle(style))
            else -> out.append(inline(node, style))
        }
    }

    private fun plain(node: Node): String =
        when (node) {
            is Text -> node.literal
            is Code -> node.literal
            else -> children(node).joinToString("") { plain(it) }
        }

    private fun code(literal: String, language: String, x: Float, width: Float) {
        val top = y
        val index = elements.size
        y += 6f
        if (language.isNotBlank()) {
            text(
                Component.literal(language.take(80)).setStyle(normal.withColor(RecallDraw.MUTED)),
                x + 6f,
                (width - 12f).coerceAtLeast(1f),
            )
            y += 2f
        }
        literal.removeSuffix("\n").split('\n').forEach { line ->
            if (line.isEmpty()) y += 12f
            else
                text(
                    Component.literal(line.replace("\t", "    ")).setStyle(normal.withFont(mono)),
                    x + 6f,
                    (width - 12f).coerceAtLeast(1f),
                )
        }
        y += 6f
        elements.add(
            index,
            MarkdownElement.Fill(UiRect(x, top, width, y - top), 0xA0252530.toInt()),
        )
        y += 6f
    }

    private fun image(
        node: Image,
        x: Float,
        width: Float,
        style: Style,
        alignment: TableCell.Alignment?,
    ) {
        val id = MarkdownImages.resolve(node.destination, namespace)
        val size = id?.let(MarkdownImages::dimensions)
        val alt = plain(node)
        if (id == null || size == null) {
            text(
                Component.translatable(
                        if (id == null) "markdown.recall.unsupported_image"
                        else "markdown.recall.missing_image",
                        alt.ifBlank { node.destination },
                    )
                    .setStyle(style.withColor(RecallDraw.MUTED).withItalic(true)),
                x,
                width,
                alignment = alignment,
            )
            return
        }
        val scale = min(1f, min(width / size.first, 240f / size.second))
        val imageWidth = size.first * scale
        val imageHeight = size.second * scale
        elements +=
            MarkdownElement.Image(
                UiRect(alignedX(x, width, imageWidth, alignment), y, imageWidth, imageHeight),
                id,
                size.first,
                size.second,
                alt,
                node.title?.takeIf(String::isNotBlank),
                style.insertion,
            )
        y += imageHeight + 6f
    }

    private fun table(node: TableBlock, x: Float, width: Float, style: Style) {
        val rows = children(node).flatMap { children(it) }.filterIsInstance<TableRow>().toList()
        val count = rows.maxOfOrNull { children(it).count() } ?: return
        val cellWidth = width / count
        rows.forEach { row ->
            val top = y
            val startIndex = elements.size
            var bottom = top + 20f
            children(row).filterIsInstance<TableCell>().forEachIndexed { index, cell ->
                y = top + 4f
                flow(
                    cell,
                    x + index * cellWidth + 5f,
                    (cellWidth - 10f).coerceAtLeast(1f),
                    if (cell.isHeader) style.withBold(true) else style,
                    alignment = cell.alignment,
                )
                bottom = maxOf(bottom, y + 4f)
            }
            elements.add(
                startIndex,
                MarkdownElement.Fill(
                    UiRect(x, top, width, bottom - top),
                    if (children(row).filterIsInstance<TableCell>().firstOrNull()?.isHeader == true)
                        0x50333350
                    else RecallDraw.CARD_INNER,
                ),
            )
            for (i in 0..count) box(
                x + i * cellWidth - if (i == count) 1f else 0f,
                top,
                1f,
                bottom - top,
                0x60666680,
            )
            box(x, top, width, 1f, 0x60666680)
            box(x, bottom - 1f, width, 1f, 0x60666680)
            y = bottom
        }
        y += 8f
    }

    companion object {
        private val SCENE = Regex("\\[\\[scene:([^]]+)]]")
    }
}
