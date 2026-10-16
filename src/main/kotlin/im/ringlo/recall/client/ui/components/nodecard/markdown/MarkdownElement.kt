package im.ringlo.recall.client.ui.components.nodecard.markdown

import im.ringlo.recall.client.ui.UiRect
import net.minecraft.resources.Identifier
import net.minecraft.util.FormattedCharSequence

sealed interface MarkdownElement {
    val rect: UiRect

    data class Text(
        override val rect: UiRect,
        val text: FormattedCharSequence,
        val scale: Float = 1f,
    ) : MarkdownElement

    data class Fill(override val rect: UiRect, val color: Int) : MarkdownElement

    data class Image(
        override val rect: UiRect,
        val texture: Identifier,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val alt: String,
        val title: String?,
        val link: String?,
    ) : MarkdownElement

    data class Scene(override val rect: UiRect, val id: Identifier) : MarkdownElement
}
