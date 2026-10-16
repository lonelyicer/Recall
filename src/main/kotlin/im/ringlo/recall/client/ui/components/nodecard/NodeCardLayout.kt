package im.ringlo.recall.client.ui.components.nodecard

data class NodeCardLayout(
    val contentHeight: Float,
    val rows: List<NodeCardRow>,
    val height: Float,
    val viewportHeight: Float,
) {
    val maxScroll: Float
        get() = (contentHeight - viewportHeight).coerceAtLeast(0f)
}
