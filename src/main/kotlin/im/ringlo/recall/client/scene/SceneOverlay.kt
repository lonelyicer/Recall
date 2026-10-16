package im.ringlo.recall.client.scene

import im.ringlo.recall.api.scene.SceneArguments
import im.ringlo.recall.client.ui.RecallDraw
import im.ringlo.recall.content.ContentSnapshot
import net.createmod.catnip.api.client.gui.element.BoxElement
import net.createmod.catnip.api.client.gui.element.ScreenElement
import net.createmod.catnip.api.client.gui.texture.CatnipGuiTextures
import net.createmod.catnip.api.theme.Color
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f

class SceneOverlay(private val step: ContentSnapshot.SceneStep) {
    var fade = 0f
    var customIcon: ScreenElement? = null
    var customItem: ItemStack = ItemStack.EMPTY
    private val p = step.parameters

    fun extract(gui: GuiGraphicsExtractor, stage: ScreenRectangle, matrix: Matrix4f, scale: Int) {
        if (fade < 1f / 16) return
        val font = Minecraft.getInstance().font
        val position =
            if (p.has("block")) Vec3.atCenterOf(SceneArguments.position(p))
            else SceneArguments.vector(p, "position")
        val projected =
            matrix.transformPosition(
                Vector3f(position.x.toFloat(), position.y.toFloat(), position.z.toFloat())
            )
        val anchored = p.has("position") || p.has("block")
        val px =
            if (anchored) stage.left() + projected.x / scale else stage.left() + stage.width() / 2f
        val py =
            if (anchored) stage.bottom() - projected.y / scale
            else stage.top() + 12f + SceneArguments.number(p, "independent_y")
        if (step.action == "overlay/show_controls") {
            controls(gui, px.toInt(), py.toInt())
            return
        }
        val text =
            if (p.has("translation")) Component.translatable(p.get("translation").asString)
            else Component.literal(SceneArguments.text(p, "text"))
        val maxWidth = (stage.width() / 2).coerceIn(40, 180)
        val lines = font.split(text, maxWidth)
        val width = lines.maxOfOrNull { font.width(it) } ?: 0
        val x =
            if (p.get("near_target")?.asBoolean == true)
                (px + 30).toInt().coerceAtMost(stage.right() - width - 6)
            else stage.right() - width - 12
        val y =
            py.toInt()
                .coerceIn(
                    stage.top() + 3,
                    (stage.bottom() - lines.size * 9 - 5).coerceAtLeast(stage.top() + 3),
                )
        BoxElement()
            .withBackground<BoxElement>(0xe0101010.toInt())
            .gradientBorder<BoxElement>(0x60ffeedd, 0x20ffeedd)
            .at<BoxElement>((x - 3).toFloat(), (y - 3).toFloat(), 0f)
            .withBounds<BoxElement>(width + 6, lines.size * 9 + 5)
            .submit(gui)
        val color = RecallDraw.withAlpha(0xff000000.toInt() or color(p), fade)
        if (anchored) gui.fill(minOf(px.toInt(), x), y - 4, maxOf(px.toInt(), x) + 1, y - 3, color)
        lines.forEachIndexed { i, line -> gui.text(font, line, x, y + i * 9, color, false) }
    }

    private fun controls(gui: GuiGraphicsExtractor, x: Int, y: Int) {
        val mc = Minecraft.getInstance()
        val icon =
            customIcon
                ?: when (SceneArguments.text(p, "input", "right_click")) {
                    "left_click" -> CatnipGuiTextures.ICON_LMB
                    "scroll" -> CatnipGuiTextures.ICON_SCROLL
                    else -> CatnipGuiTextures.ICON_RMB
                }
        val key =
            when {
                p.get("sneaking")?.asBoolean == true ->
                    mc.options.keyShift.translatedKeyMessage.string + " + "
                p.get("ctrl")?.asBoolean == true -> "Ctrl + "
                else -> ""
            }
        val keyWidth = mc.font.width(key)
        val item =
            if (!customItem.isEmpty) customItem
            else
                p.get("item")?.asString?.let {
                    BuiltInRegistries.ITEM.getValue(Identifier.parse(it)).let(::ItemStack)
                } ?: ItemStack.EMPTY
        val width = keyWidth + 24 + if (!item.isEmpty) 24 else 0
        val direction = SceneArguments.text(p, "pointing", "down")
        val bx =
            when (direction) {
                "left" -> x + 10
                "right" -> x - width - 10
                else -> x - width / 2
            }
        val by =
            when (direction) {
                "up" -> y + 10
                "down" -> y - 34
                else -> y - 12
            }
        BoxElement()
            .withBackground<BoxElement>(0xff101010.toInt())
            .gradientBorder<BoxElement>(0x40ffeedd, 0x20ffeedd)
            .at<BoxElement>(bx.toFloat(), by.toFloat(), 0f)
            .withBounds<BoxElement>(width, 24)
            .submit(gui)
        gui.pose().pushMatrix()
        try {
            gui.pose().translate(x.toFloat(), y.toFloat())
            gui.pose()
                .rotate(
                    when (direction) {
                        "up" -> Math.PI.toFloat()
                        "left" -> Math.PI.toFloat() / 2
                        "right" -> -Math.PI.toFloat() / 2
                        else -> 0f
                    }
                )
            CatnipGuiTextures.SPEECH_TOOLTIP_BACKGROUND.render(gui, -4, -9)
            CatnipGuiTextures.SPEECH_TOOLTIP_COLOR.render(gui, -4, -9, Color(0x40ffeedd, true))
        } finally {
            gui.pose().popMatrix()
        }
        gui.text(mc.font, key, bx + 2, by + 8, 0xffffffff.toInt(), false)
        gui.pose().pushMatrix()
        try {
            gui.pose().translate((bx + keyWidth).toFloat(), by.toFloat())
            gui.pose().scale(1.5f)
            icon.render(gui, 0, 0)
            if (!item.isEmpty) gui.item(item, 16, 0)
        } finally {
            gui.pose().popMatrix()
        }
    }

    companion object {
        fun color(parameters: com.google.gson.JsonObject): Int {
            val color = parameters.get("color") ?: return 0xffffff
            if (color.asJsonPrimitive.isNumber) return color.asInt
            return when (color.asString.lowercase()) {
                "red" -> 0xff5d6c
                "green" -> 0x9de06b
                "blue" -> 0x6bafff
                "white" -> 0xffffff
                "black" -> 0x000000
                "slow" -> 0x9de06b
                "medium" -> 0xffd36b
                "fast" -> 0xff5d6c
                else -> color.asString.removePrefix("#").toInt(16)
            }
        }
    }
}
