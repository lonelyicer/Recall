package im.ringlo.recall.client.ui.components.nodecard

import im.ringlo.recall.client.ui.RecallSpring
import im.ringlo.recall.client.ui.UiRect
import im.ringlo.recall.content.ContentSnapshot

class ClosingNodeCard(
    val node: ContentSnapshot.MapNode,
    val source: UiRect,
    private val startCard: UiRect,
    private val startIcon: UiRect,
    private val startTitle: UiRect,
    private val nodeTitle: UiRect,
) {
    private val spring =
        RecallSpring(0.34f).apply {
            snap(1f)
            setTarget(0f)
        }

    fun frame(seconds: Float) = spring.tick(seconds)

    val settled: Boolean
        get() = spring.settled()

    val card: UiRect
        get() = UiRect.lerp(source, startCard, spring.value)

    val icon: UiRect
        get() =
            UiRect.lerp(
                UiRect(source.centerX - 12f, source.centerY - 12f, 24f, 24f),
                startIcon,
                spring.value,
            )

    val title: UiRect
        get() = UiRect.lerp(nodeTitle, startTitle, spring.value)
}
