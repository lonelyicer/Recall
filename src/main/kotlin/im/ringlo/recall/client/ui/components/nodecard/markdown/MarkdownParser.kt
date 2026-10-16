package im.ringlo.recall.client.ui.components.nodecard.markdown

import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.Node
import org.commonmark.parser.Parser

object MarkdownParser {
    private val parser =
        Parser.builder()
            .extensions(listOf(TablesExtension.create(), StrikethroughExtension.create()))
            .build()

    fun parse(source: String): Node = parser.parse(source)
}
