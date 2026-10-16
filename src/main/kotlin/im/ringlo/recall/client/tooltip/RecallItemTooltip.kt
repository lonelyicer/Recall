package im.ringlo.recall.client.tooltip

import com.mojang.blaze3d.systems.RenderSystem
import im.ringlo.recall.Recall
import im.ringlo.recall.client.RecallKeyMappings
import im.ringlo.recall.client.ui.RecallScreen
import im.ringlo.recall.mixin.accessor.AbstractContainerScreenAccessor
import kotlin.math.max
import kotlin.math.min
import net.createmod.catnip.api.animation.LerpedFloat
import net.createmod.catnip.api.client.animation.AnimationTickHolder
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent

@EventBusSubscriber(modid = Recall.ID, value = [Dist.CLIENT])
object RecallItemTooltip {
    private val openProgress = LerpedFloat.linear().startWithValue(0.0)
    private var lastQueriedItem: Item = Items.AIR
    private val subject =
        Component.translatable("tooltip.recall.subject").withStyle(ChatFormatting.GREEN)

    @SubscribeEvent
    fun beforeTick(event: ClientTickEvent.Pre) {
        val minecraft = Minecraft.getInstance()
        val hovered = hoveredItem()
        if (hovered == Items.AIR || isCurrentSubject(hovered) || !hasEntry(hovered)) {
            openProgress.startWithValue(0.0)
            return
        }
        val progress = openProgress.value
        if (progress >= 1f) {
            minecraft.setScreen(
                RecallScreen(minecraft.screen, BuiltInRegistries.ITEM.getKey(hovered))
            )
            openProgress.startWithValue(0.0)
            return
        }
        openProgress.setValue(
            if (RecallKeyMappings.isOpenKeyDown())
                min(1.0, progress + max(.25, progress.toDouble()) * .25)
            else max(0.0, progress - .05)
        )
    }

    @SubscribeEvent
    fun afterTick(event: ClientTickEvent.Post) {
        lastQueriedItem = Items.AIR
    }

    @SubscribeEvent
    fun tooltip(event: ItemTooltipEvent) {
        val item = event.itemStack.item
        val rendering = RenderSystem.isOnRenderThread()
        if (rendering) lastQueriedItem = item
        if (!hasEntry(item)) return
        val line =
            when {
                !rendering -> holdMessage()
                isCurrentSubject(item) -> subject
                visualProgress() <= 0f -> holdMessage()
                else -> progressBar(visualProgress())
            }
        event.toolTip.add(if (event.toolTip.size < 2) event.toolTip.size else 1, line)
    }

    @JvmStatic
    fun visualProgress(): Float =
        min(1f, openProgress.getValue(AnimationTickHolder.getGuiPartialTicks()) * 8f / 7f)

    @SubscribeEvent
    fun loggingOut(event: ClientPlayerNetworkEvent.LoggingOut) {
        openProgress.startWithValue(0.0)
        lastQueriedItem = Items.AIR
    }

    private fun hoveredItem(): Item {
        val screen = Minecraft.getInstance().screen ?: return Items.AIR
        if (screen is AbstractContainerScreenAccessor) {
            val hovered = screen.recallHoveredSlot()?.item?.item
            if (hovered != null && hovered != Items.AIR) return hovered
        }
        return lastQueriedItem
    }

    private fun isCurrentSubject(item: Item): Boolean =
        (Minecraft.getInstance().screen as? RecallScreen)?.isItemSubject(
            BuiltInRegistries.ITEM.getKey(item)
        ) == true

    private fun hasEntry(item: Item): Boolean =
        item != Items.AIR && RecallItemIndex.find(BuiltInRegistries.ITEM.getKey(item)) != null

    private fun holdMessage(): Component =
        Component.translatable(
                "tooltip.recall.hold_to_recall",
                RecallKeyMappings.open.translatedKeyMessage.copy().withStyle(ChatFormatting.GRAY),
            )
            .withStyle(ChatFormatting.DARK_GRAY)

    private fun progressBar(progress: Float): Component {
        val font = Minecraft.getInstance().font
        val total = (font.width(holdMessage()).toFloat() / font.width("|")).toInt()
        val current = (progress * total).toInt()
        return Component.empty()
            .append(Component.literal("|".repeat(current)).withStyle(ChatFormatting.GRAY))
            .append(
                Component.literal("|".repeat(total - current)).withStyle(ChatFormatting.DARK_GRAY)
            )
    }
}
