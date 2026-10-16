package im.ringlo.recall.client.ui

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object RecallDraw {
    const val TEXT = 0xFFF1F1F1.toInt()
    const val MUTED = 0xFFB6B6C0.toInt()
    const val ACCENT = 0xFFC0C0FF.toInt()
    const val CONNECTION = 0x90C0C0FF.toInt()
    const val CONNECTION_ACTIVE = 0xE0FFFFFF.toInt()
    const val CARD_INNER = 0x28181830

    fun rect(gui: GuiGraphicsExtractor, rect: UiRect, color: Int) {
        gradient(gui, rect, color, color)
    }

    fun gradient(gui: GuiGraphicsExtractor, rect: UiRect, top: Int, bottom: Int) {
        if (rect.width <= 0f || rect.height <= 0f || (top ushr 24 == 0 && bottom ushr 24 == 0))
            return
        gui.pose().pushMatrix()
        gui.pose().translate(rect.left, rect.top)
        gui.pose().scale(rect.width, rect.height)
        gui.fillGradient(0, 0, 1, 1, top, bottom)
        gui.pose().popMatrix()
    }

    fun horizontalGradient(gui: GuiGraphicsExtractor, rect: UiRect, left: Int, right: Int) {
        gui.pose().pushMatrix()
        gui.pose().translate(rect.left, rect.bottom)
        gui.pose().rotate((-Math.PI / 2).toFloat())
        gradient(gui, UiRect(0f, 0f, rect.height, rect.width), left, right)
        gui.pose().popMatrix()
    }

    fun box(gui: GuiGraphicsExtractor, rect: UiRect, hovered: Boolean = false, alpha: Float = 1f) {
        val x = rect.left
        val y = rect.top
        val w = rect.width
        val h = rect.height
        val black = withAlpha(0xFF000000.toInt(), alpha)
        val top = withAlpha(if (hovered) 0xF0C0C0FF.toInt() else 0x60C0C0FF, alpha)
        val bottom = withAlpha(if (hovered) 0xA0C0C0FF.toInt() else 0x30C0C0FF, alpha)
        rect(gui, UiRect(x - 3f, y - 4f, w + 6f, h + 8f), black)
        rect(gui, UiRect(x - 4f, y - 3f, 1f, h + 6f), black)
        rect(gui, UiRect(x + w + 3f, y - 3f, 1f, h + 6f), black)
        rect(gui, UiRect(x - 3f, y - 3f, w + 6f, 1f), top)
        rect(gui, UiRect(x - 3f, y + h + 2f, w + 6f, 1f), bottom)
        gradient(gui, UiRect(x - 3f, y - 2f, 1f, h + 4f), top, bottom)
        gradient(gui, UiRect(x + w + 2f, y - 2f, 1f, h + 4f), top, bottom)
    }

    fun drawLine(
        gui: GuiGraphicsExtractor,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        color: Int,
        thickness: Float = 1f,
    ) {
        val length = hypot(x2 - x1, y2 - y1)
        if (length < 0.01f || thickness <= 0f || color ushr 24 == 0) return
        val pose = gui.pose()
        pose.pushMatrix()
        pose.translate(x1, y1)
        pose.rotate(atan2(y2 - y1, x2 - x1))
        val pixelsPerUnit =
            hypot(pose.m10(), pose.m11()) * Minecraft.getInstance().window.guiScale.toFloat()
        val feather = 1f / pixelsPerUnit.coerceAtLeast(0.0001f)
        val core = (thickness - feather).coerceAtLeast(0f)
        val covered = withAlpha(color, (thickness / feather).coerceAtMost(1f))
        val transparent = covered and 0xFFFFFF
        gradient(gui, UiRect(0f, -core / 2f - feather, length, feather), transparent, covered)
        rect(gui, UiRect(0f, -core / 2f, length, core), covered)
        gradient(gui, UiRect(0f, core / 2f, length, feather), covered, transparent)
        pose.popMatrix()
    }

    fun drawItem(gui: GuiGraphicsExtractor, id: Identifier?, rect: UiRect, alpha: Float = 1f) {
        if (alpha <= 0f || rect.width <= 0f) return
        val scale = rect.width / 16f * sqrt(alpha.coerceIn(0f, 1f))
        gui.pose().pushMatrix()
        gui.pose().translate(rect.centerX - 8f * scale, rect.centerY - 8f * scale)
        gui.pose().scale(scale)
        gui.item(itemStack(id), 0, 0)
        gui.pose().popMatrix()
    }

    fun itemStack(id: Identifier?): ItemStack =
        ItemStack(
            if (id == null) Items.KNOWLEDGE_BOOK
            else BuiltInRegistries.ITEM.get(id).map { it.value() }.orElse(Items.BARRIER)
        )

    fun text(gui: GuiGraphicsExtractor, font: Font, text: String, x: Float, y: Float, color: Int) =
        text(gui, font, Component.literal(text), x, y, color)

    fun text(
        gui: GuiGraphicsExtractor,
        font: Font,
        text: Component,
        x: Float,
        y: Float,
        color: Int,
    ) {
        if (color ushr 24 == 0) return
        gui.pose().pushMatrix()
        gui.pose().translate(x, y)
        gui.text(font, text, 0, 0, color)
        gui.pose().popMatrix()
    }

    fun withAlpha(color: Int, alpha: Float): Int =
        ((color ushr 24) * alpha.coerceIn(0f, 1f)).toInt().shl(24) or (color and 0x00FFFFFF)

    fun smoothstep(value: Float): Float = value.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
}
