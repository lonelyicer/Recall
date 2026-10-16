package im.ringlo.recall.client.scene

import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.createmod.catnip.api.client.gui.UIRenderHelper
import net.createmod.catnip.api.client.render.DefaultSuperRenderTypeBuffer
import net.createmod.catnip.api.client.render.PonderRenderTypes
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer
import net.minecraft.client.renderer.MultiBufferSource

class SceneRenderer(buffer: MultiBufferSource.BufferSource) :
    PictureInPictureRenderer<SceneRenderState>(buffer) {
    override fun getRenderStateClass(): Class<SceneRenderState> = SceneRenderState::class.java

    override fun getTextureLabel(): String = "recall:scene"

    override fun renderToTexture(state: SceneRenderState, pose: PoseStack) {
        val gameRenderer = Minecraft.getInstance().gameRenderer
        val dispatcher = gameRenderer.featureRenderDispatcher
        val buffer = DefaultSuperRenderTypeBuffer.getInstance()
        gameRenderer.lighting.setupFor(Lighting.Entry.ENTITY_IN_UI)
        pose.pushPose()
        try {
            pose.setIdentity()
            pose.mulPose(state.transform)
            state.scene.render(
                buffer,
                dispatcher.submitNodeStorage,
                pose,
                state.time,
                state.camera,
                state.opacity,
            )
            buffer.draw()
            if (state.scene.shadow && state.scene.basePlate.isNotEmpty()) {
                val base = state.scene.basePlate
                val size =
                    maxOf(
                        base.maxOf { it.x } - base.minOf { it.x },
                        base.maxOf { it.z } - base.minOf { it.z },
                    ) + 1
                pose.pushPose()
                try {
                    pose.translate(base.minOf { it.x }.toFloat(), 0f, base.minOf { it.z }.toFloat())
                    UIRenderHelper.flipForGuiRender(pose)
                    repeat(4) {
                        pose.translate(size.toFloat(), 0f, 0f)
                        val vertices = bufferSource.getBuffer(PonderRenderTypes.gui())
                        val matrix = pose.last().pose()
                        vertices.addVertex(matrix, 0f, 0f, .001f).setColor(0x66000000)
                        vertices.addVertex(matrix, 0f, 4f, .001f).setColor(0)
                        vertices.addVertex(matrix, -size.toFloat(), 4f, .001f).setColor(0)
                        vertices.addVertex(matrix, -size.toFloat(), 0f, .001f).setColor(0x66000000)
                        pose.mulPose(Axis.YP.rotationDegrees(-90f))
                    }
                } finally {
                    pose.popPose()
                }
            }
            dispatcher.renderAllFeatures()
            bufferSource.endBatch()
            state.scene.world.particles.render(pose, state.camera, state.time * 20f % 1f)
        } finally {
            pose.popPose()
            gameRenderer.lighting.setupFor(Lighting.Entry.ITEMS_3D)
        }
    }
}
