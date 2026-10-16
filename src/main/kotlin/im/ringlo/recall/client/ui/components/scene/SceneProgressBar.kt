package im.ringlo.recall.client.ui.components.scene

import im.ringlo.recall.client.ui.RecallDraw
import im.ringlo.recall.client.ui.UiRect
import im.ringlo.recall.client.ui.components.BaseComponent
import im.ringlo.recall.client.ui.components.layout.LayoutBox
import net.createmod.catnip.api.animation.LerpedFloat
import net.createmod.catnip.api.client.gui.UIRenderHelper
import net.createmod.catnip.api.theme.Color
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

class SceneProgressBar(
    private val playback: ScenePlayback,
    private val opacity: () -> Float = { 1f },
) : BaseComponent("scene-progress") {
    private val progress = LerpedFloat.linear().startWithValue(0.0)
    private var remainder = 0f

    private val trackX: Int
        get() = bounds.x + 4

    private val trackY: Int
        get() = bounds.y + 4

    private val trackWidth: Int
        get() = (bounds.width - 8).coerceAtLeast(1)

    fun reset() {
        progress.startWithValue(0.0)
        remainder = 0f
    }

    override fun frame(seconds: Float) {
        remainder += seconds * 20f
        while (remainder >= 1f) {
            progress.chase(
                if (playback.totalTime > 0) (playback.currentTime / playback.totalTime).toDouble()
                else 1.0,
                .5,
                LerpedFloat.Chaser.EXP,
            )
            progress.tickChaser()
            remainder--
        }
    }

    override fun layout(box: LayoutBox) {
        super.layout(box)
        clip =
            UiRect(
                bounds.x.toFloat(),
                bounds.y.toFloat(),
                bounds.width.toFloat(),
                bounds.height.toFloat(),
            )
    }

    private fun hovered(mouseX: Double, mouseY: Double): Boolean =
        playback.keyframes.isNotEmpty() &&
            playback.totalTime > 0 &&
            mouseX >= trackX &&
            mouseX < trackX + trackWidth + 4 &&
            mouseY >= trackY - 3 &&
            mouseY < trackY + 21

    private fun hoveredKeyframeIndex(mouseX: Double): Int {
        if (playback.keyframes.isEmpty() || playback.totalTime <= 0) return -1
        val clickedAtTime =
            ((mouseX - trackX) / (trackWidth + 4).toDouble() * playback.totalTime).toInt()
        val lastKeyframeTime = playback.keyframes.last()
        val diffToEnd = playback.totalTime - clickedAtTime
        val diffToLast = clickedAtTime - lastKeyframeTime
        if (diffToEnd > 0 && diffToEnd < diffToLast / 2) return playback.keyframes.size
        return playback.keyframes.indexOfLast { it <= clickedAtTime }
    }

    override fun paint(
        gui: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        val alpha = opacity().coerceIn(0f, 1f)
        if (alpha <= 0.001f || bounds.width <= 0 || bounds.height <= 0) return
        ponderBox(gui, trackX, trackY, trackWidth, alpha)

        val pose = gui.pose()
        pose.pushMatrix()
        pose.translate(trackX - 2f, trackY - 2f)
        pose.pushMatrix()
        pose.scale(
            (trackWidth + 4) * progress.getValue(remainder).coerceIn(0f, 1f),
            1f,
        )
        gradient(gui, 0, 1, 1, 3, RecallDraw.withAlpha(0x80AAAADD.toInt(), alpha))
        gradient(gui, 0, 3, 1, 4, RecallDraw.withAlpha(0x50AAAADD, alpha))
        pose.popMatrix()

        val hoverIndex =
            if (hovered(mouseX.toDouble(), mouseY.toDouble()))
                hoveredKeyframeIndex(mouseX.toDouble())
            else -2
        if (hoverIndex == -1) marker(gui, true, 0f, 0, alpha)
        else if (hoverIndex == playback.keyframes.size)
            marker(gui, true, playback.totalTime, trackWidth + 4, alpha)
        if (playback.totalTime > 0)
            playback.keyframes.forEachIndexed { index, time ->
                val position = (time / playback.totalTime * (trackWidth + 2)).toInt()
                marker(gui, index == hoverIndex, time, position, alpha)
            }
        pose.popMatrix()
    }

    private fun marker(
        gui: GuiGraphicsExtractor,
        selected: Boolean,
        time: Float,
        position: Int,
        alpha: Float,
    ) {
        val color =
            RecallDraw.withAlpha(
                if (selected) 0xE0FFFFFF.toInt() else 0x70FFFFFF,
                alpha,
            )
        val height = if (selected) 8 else 4
        if (selected) {
            gradient(gui, position, 9, position + 2, 9 + height, color)
            val font = Minecraft.getInstance().font
            val text = if (playback.currentTime < time) ">" else "<"
            val offset = if (playback.currentTime < time) -2 - font.width(text) else 4
            gui.text(
                font,
                Component.literal(text).withStyle(ChatFormatting.BOLD),
                position + offset,
                10,
                color,
                false,
            )
        }
        gradient(gui, position, 0, position + 2, 1 + height, color)
    }

    private fun ponderBox(
        gui: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        alpha: Float,
    ) {
        val c1 = RecallDraw.withAlpha(0xFF000000.toInt(), alpha)
        val c2 = RecallDraw.withAlpha(0x40FFEEDD, alpha)
        val c3 = RecallDraw.withAlpha(0x20FFEEDD, alpha)
        gui.fill(x - 3, y - 4, x + width + 3, y - 3, c1)
        gui.fill(x - 4, y - 3, x - 3, y + 4, c1)
        gui.fill(x - 3, y + 4, x + width + 3, y + 5, c1)
        gui.fill(x + width + 3, y - 3, x + width + 4, y + 4, c1)
        gui.fill(x - 3, y - 3, x + width + 3, y + 4, c1)
        gui.fill(x - 3, y - 3, x + width + 3, y - 2, c2)
        gradient(gui, x - 3, y - 2, x - 2, y + 3, c2, c3)
        gui.fill(x - 3, y + 3, x + width + 3, y + 4, c3)
        gradient(gui, x + width + 2, y - 2, x + width + 3, y + 3, c2, c3)
    }

    private fun gradient(
        gui: GuiGraphicsExtractor,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        color: Int,
    ) = gradient(gui, x0, y0, x1, y1, color, color)

    private fun gradient(
        gui: GuiGraphicsExtractor,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        start: Int,
        end: Int,
    ) {
        if (x1 <= x0 || y1 <= y0) return
        UIRenderHelper.drawGradientRect(
            gui,
            x0.toFloat(),
            y0.toFloat(),
            x1.toFloat(),
            y1.toFloat(),
            Color(start, true),
            Color(end, true),
        )
    }

    override fun onMouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() != 0 || !hovered(event.x(), event.y())) return false
        when (val index = hoveredKeyframeIndex(event.x())) {
            -1 -> playback.seekToTime(0f)
            playback.keyframes.size -> playback.seekToTime(playback.totalTime)
            else -> playback.seekToTime(playback.keyframes[index])
        }
        return true
    }
}
