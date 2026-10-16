package im.ringlo.recall.client.ui.components.nodecard

import im.ringlo.recall.content.ContentSnapshot

data class NodeCardRow(
    val article: ContentSnapshot.Article,
    val top: Float,
    val bodyHeight: Float,
) {
    val headerHeight: Float
        get() = 44f

    val bodyTop: Float
        get() = top + headerHeight + 6f
}
