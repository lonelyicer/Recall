package im.ringlo.recall.client.ui.components.nodecard.markdown

data class MarkdownLayout(
    val elements: List<MarkdownElement>,
    val height: Float,
    val anchors: Map<String, Float>,
)
