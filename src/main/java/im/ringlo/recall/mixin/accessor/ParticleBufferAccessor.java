package im.ringlo.recall.mixin.accessor;

import net.minecraft.client.renderer.feature.ParticleFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ParticleFeatureRenderer.ParticleBufferCache.class)
public interface ParticleBufferAccessor {
    @Invoker("rotate")
    void recallRotate();
}
