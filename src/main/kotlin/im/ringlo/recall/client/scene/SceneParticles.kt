package im.ringlo.recall.client.scene

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.vertex.PoseStack
import im.ringlo.recall.mixin.accessor.ParticleBufferAccessor
import java.util.IdentityHashMap
import java.util.OptionalDouble
import java.util.OptionalInt
import net.createmod.catnip.impl.client.mixin.ParticleEngineAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.particle.Particle
import net.minecraft.client.particle.ParticleGroup
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.client.renderer.SubmitNodeStorage
import net.minecraft.client.renderer.feature.ParticleFeatureRenderer
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.state.level.ParticlesRenderState

class SceneParticles : AutoCloseable {
    private val groups = IdentityHashMap<ParticleRenderType, ParticleGroup<*>>()
    private val pending = ArrayDeque<Particle>()
    private val state = ParticlesRenderState()
    private val camera = SceneCamera()
    private val frustum = SceneParticleFrustum()
    private val buffers = mutableListOf<ParticleFeatureRenderer.ParticleBufferCache>()
    private val counts = mutableMapOf<net.minecraft.core.particles.ParticleLimit, Int>()
    private var modelSet: Any? = null
    private val queue = SubmitNodeStorage()

    fun add(particle: Particle) {
        if (pending.size + groups.values.sumOf { it.size() } >= 4096) return
        val limit = particle.particleLimit.orElse(null)
        if (limit != null) {
            val count = counts[limit] ?: 0
            if (count >= limit.limit()) return
            counts[limit] = count + 1
        }
        pending.add(particle)
    }

    fun tick() {
        groups.values.forEach { group ->
            val iterator = group.all.iterator()
            while (iterator.hasNext()) {
                val particle = iterator.next()
                particle.tick()
                if (!particle.isAlive) {
                    particle.particleLimit.ifPresent { limit ->
                        counts[limit] = ((counts[limit] ?: 1) - 1).coerceAtLeast(0)
                    }
                    iterator.remove()
                }
            }
        }
        val engine = Minecraft.getInstance().particleEngine as ParticleEngineAccessor
        while (pending.isNotEmpty()) {
            val particle = pending.removeFirst()
            groups
                .getOrPut(particle.group) { engine.callCreateParticleGroup(particle.group) }
                .add(particle)
        }
    }

    fun render(pose: PoseStack, cameraState: CameraRenderState, partialTick: Float) {
        val minecraft = Minecraft.getInstance()
        val currentModels = minecraft.modelManager.blockStateModelSet
        if (modelSet != null && modelSet !== currentModels) clear()
        modelSet = currentModels
        var bufferIndex = 0
        camera.configure(cameraState)
        val stack = RenderSystem.getModelViewStack()
        stack.pushMatrix()
        stack.mul(pose.last().pose())
        try {
            ParticleEngineAccessor.getRENDER_ORDER().forEach { type ->
                groups[type]
                    ?.takeUnless { it.isEmpty }
                    ?.let { state.add(it.extractRenderState(frustum, camera, partialTick)) }
            }
            state.submit(queue, cameraState)
            queue.submitsPerOrder.values.forEach { collection ->
                listOf(false, true).forEach { translucent ->
                    collection.particleGroupRenderers.forEach renderer@{ renderer ->
                        if (renderer.isEmpty) return@renderer
                        if (bufferIndex == buffers.size)
                            buffers.add(ParticleFeatureRenderer.ParticleBufferCache())
                        val buffer = buffers[bufferIndex++]
                        (buffer as ParticleBufferAccessor).recallRotate()
                        val prepared = renderer.prepare(buffer, translucent) ?: return@renderer
                        val target = minecraft.mainRenderTarget
                        val color =
                            RenderSystem.outputColorTextureOverride ?: target.colorTextureView!!
                        val depth =
                            RenderSystem.outputDepthTextureOverride ?: target.depthTextureView!!
                        RenderSystem.getDevice()
                            .createCommandEncoder()
                            .createRenderPass(
                                { "Recall scene particles" },
                                color,
                                OptionalInt.empty(),
                                depth,
                                OptionalDouble.empty(),
                            )
                            .use { pass ->
                                pass.setUniform(
                                    "Projection",
                                    RenderSystem.getProjectionMatrixBuffer()!!,
                                )
                                pass.setUniform("Fog", RenderSystem.getShaderFog()!!)
                                pass.bindTexture(
                                    "Sampler2",
                                    minecraft.gameRenderer.lightmap(),
                                    RenderSystem.getSamplerCache()
                                        .getClampToEdge(FilterMode.LINEAR),
                                )
                                renderer.render(prepared, buffer, pass, minecraft.textureManager)
                            }
                    }
                }
            }
        } finally {
            state.reset()
            queue.clear()
            stack.popMatrix()
        }
    }

    fun clear() {
        groups.clear()
        pending.clear()
        counts.clear()
        state.reset()
        queue.clear()
    }

    override fun close() {
        clear()
        buffers.forEach { it.close() }
        buffers.clear()
    }
}
