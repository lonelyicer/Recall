package im.ringlo.recall.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import im.ringlo.recall.client.tooltip.RecallItemTooltip;
import im.ringlo.recall.mixin.accessor.GuiGraphicsExtractorAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TooltipRenderUtil.class)
public final class TooltipRenderUtilMixin {
    @WrapOperation(
        method = "extractTooltipBackground",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
            ordinal = 1
        )
    )
    private static void recallOpeningFrame(
        GuiGraphicsExtractor graphics,
        RenderPipeline renderPipeline,
        Identifier location,
        int x,
        int y,
        int width,
        int height,
        Operation<Void> original
    ) {
        float progress = RecallItemTooltip.visualProgress();
        if (progress > 0) {
            Identifier initial = location.withPath(path -> path + "_recall_initial");
            Identifier complete = location.withPath(path -> path + "_recall_final");
            if (recall$SpriteExists(graphics, initial) && recall$SpriteExists(graphics, complete)) {
                original.call(graphics, renderPipeline, initial, x, y, width, height);
                graphics.blitSprite(renderPipeline, complete, x, y, width, height, progress);
                return;
            }
        }
        original.call(graphics, renderPipeline, location, x, y, width, height);
    }

    @Unique
    private static boolean recall$SpriteExists(GuiGraphicsExtractor graphics, Identifier sprite) {
        TextureAtlas atlas = ((GuiGraphicsExtractorAccessor) graphics).recallGuiSprites();
        return atlas.getSprite(sprite) != atlas.missingSprite();
    }
}
