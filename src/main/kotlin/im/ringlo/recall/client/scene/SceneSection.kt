package im.ringlo.recall.client.scene

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import im.ringlo.recall.Recall
import net.createmod.catnip.api.client.outliner.AABBOutline
import net.createmod.catnip.api.client.render.RenderHelper
import net.createmod.catnip.api.client.render.SuperByteBuffer
import net.createmod.catnip.api.client.render.SuperRenderTypeBuffer
import net.createmod.catnip.api.client.render.model.BakedModelBufferer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.chunk.ChunkSectionLayer
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.core.BlockPos
import net.minecraft.util.LightCoordsUtil
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class SceneSection(
    val name: String,
    positions: Set<BlockPos>,
) : AutoCloseable {
    var positions: Set<BlockPos> = positions
        set(value) {
            if (field != value) clearBuffers()
            field = value
        }

    val frame = SceneSectionFrame()
    private var previousFrame: SceneSectionFrame? = null

    fun capturePreviousFrame() {
        previousFrame = frame.copy()
    }

    fun interpolatedFrame(partialTick: Float): SceneSectionFrame {
        val previous = previousFrame ?: return frame
        return frame.copy(
            offset = previous.offset.lerp(frame.offset, partialTick.toDouble()),
            rotation = previous.rotation.lerp(frame.rotation, partialTick.toDouble()),
            transition = previous.transition.lerp(frame.transition, partialTick.toDouble()),
            fade = previous.fade + (frame.fade - previous.fade) * partialTick,
        )
    }

    private val buffers = mutableMapOf<ChunkSectionLayer, SuperByteBuffer>()
    private var modelSet: Any? = null
    private var revision = -1L
    private val failedEntities = mutableSetOf<BlockPos>()

    fun bounds(): AABB =
        if (positions.isEmpty()) AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
        else
            AABB(
                positions.minOf { it.x }.toDouble(),
                positions.minOf { it.y }.toDouble(),
                positions.minOf { it.z }.toDouble(),
                positions.maxOf { it.x } + 1.0,
                positions.maxOf { it.y } + 1.0,
                positions.maxOf { it.z } + 1.0,
            )

    fun tick(world: SceneLevel) {
        if (frame.fade <= 0f) return
        positions.forEach { pos ->
            if (pos in failedEntities) return@forEach
            val entity = world.getBlockEntity(pos) ?: return@forEach
            val state = world.getBlockState(pos)
            val block = state.block as? EntityBlock ?: return@forEach
            entity.blockState = state
            try {
                @Suppress("UNCHECKED_CAST") val type = entity.type as BlockEntityType<BlockEntity>
                block.getTicker(world, state, type)?.tick(world, pos, state, entity)
            } catch (exception: Exception) {
                failedEntities.add(pos)
                Recall.LOGGER.warn("Could not tick scene block entity at {}", pos, exception)
            }
        }
    }

    fun render(
        world: SceneLevel,
        frame: SceneSectionFrame,
        pose: PoseStack,
        buffer: SuperRenderTypeBuffer,
        queue: SubmitNodeCollector,
        camera: CameraRenderState,
        partialTick: Float,
        opacity: Float,
    ) {
        if (frame.fade <= 0f || opacity <= 0f || positions.isEmpty()) return
        val minecraft = Minecraft.getInstance()
        val currentModels = minecraft.modelManager.blockStateModelSet
        if (modelSet !== currentModels || revision != world.revision) {
            clearBuffers()
            failedEntities.clear()
            modelSet = currentModels
            revision = world.revision
        }
        pose.pushPose()
        try {
            transform(pose, frame)
            val brightness = (5f + 10f * frame.fade * opacity).toInt().coerceIn(0, 15)
            val oldLight = world.light
            world.light = brightness
            try {
                renderBlockEntities(world, pose, queue, camera, partialTick)
            } finally {
                world.light = oldLight
            }
            ChunkSectionLayer.entries.forEach { layer ->
                val geometry = buffers.getOrPut(layer) { buildBuffer(world, layer) }
                geometry
                    .reset<SuperByteBuffer>()
                    .light<SuperByteBuffer>(LightCoordsUtil.pack(brightness, brightness))
                    .color<SuperByteBuffer>(255, 255, 255, (opacity * 255).toInt())
                    .renderInto(pose, buffer.getBuffer(RenderHelper.convertLayerToType(layer)))
            }
            world.breaking.forEach { (pos, progress) ->
                if (pos in positions) {
                    pose.pushPose()
                    try {
                        pose.translate(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat())
                        val state = world.getBlockState(pos)
                        queue.submitBreakingBlockModel(
                            pose,
                            currentModels.get(state),
                            state.getSeed(pos),
                            progress,
                        )
                    } finally {
                        pose.popPose()
                    }
                }
            }
            if (frame.highlighted)
                AABBOutline(bounds())
                    .apply { params.lineWidth(1f / 32f).colored(0xc0c0ff).disableLineNormals() }
                    .render(pose, buffer, Vec3.ZERO, partialTick)
        } finally {
            pose.popPose()
        }
    }

    fun transform(pose: PoseStack, frame: SceneSectionFrame = this.frame) {
        val offset = frame.offset.add(frame.transition)
        pose.translate(offset.x, offset.y, offset.z)
        val pivot = frame.pivot
        val rotation = frame.rotation
        pose.translate(pivot.x, pivot.y, pivot.z)
        pose.mulPose(Axis.XP.rotationDegrees(rotation.x.toFloat()))
        pose.mulPose(Axis.YP.rotationDegrees(rotation.y.toFloat()))
        pose.mulPose(Axis.ZP.rotationDegrees(rotation.z.toFloat()))
        pose.translate(-pivot.x, -pivot.y, -pivot.z)
        frame.stabilization?.let { anchor ->
            pose.translate(anchor.x, anchor.y, anchor.z)
            pose.mulPose(Axis.XP.rotationDegrees(-rotation.x.toFloat()))
            pose.mulPose(Axis.YP.rotationDegrees(-rotation.y.toFloat()))
            pose.mulPose(Axis.ZP.rotationDegrees(-rotation.z.toFloat()))
            pose.translate(-anchor.x, -anchor.y, -anchor.z)
        }
    }

    private fun buildBuffer(world: SceneLevel, layer: ChunkSectionLayer): SuperByteBuffer {
        val builder = SceneBufferBuilder(layer.pipeline())
        val oldMask = world.mask
        val oldLight = world.light
        try {
            world.mask = positions
            world.light = 0
            BakedModelBufferer.bufferBlocks(positions.iterator(), world, null, true, builder)
            return builder.build()
        } finally {
            world.mask = oldMask
            world.light = oldLight
        }
    }

    private fun renderBlockEntities(
        world: SceneLevel,
        pose: PoseStack,
        queue: SubmitNodeCollector,
        camera: CameraRenderState,
        partialTick: Float,
    ) {
        val dispatcher = Minecraft.getInstance().blockEntityRenderDispatcher
        positions.forEach { pos ->
            if (pos in failedEntities) return@forEach
            val entity = world.getBlockEntity(pos) ?: return@forEach
            val renderer =
                dispatcher.getRenderer<BlockEntity, BlockEntityRenderState>(entity)
                    ?: return@forEach
            pose.pushPose()
            try {
                pose.translate(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat())
                val state = renderer.createRenderState()
                renderer.extractRenderState(entity, state, partialTick, camera.pos, null)
                renderer.submit(state, pose, queue, camera)
            } catch (exception: Exception) {
                failedEntities.add(pos)
                Recall.LOGGER.warn("Could not render scene block entity at {}", pos, exception)
            } finally {
                pose.popPose()
            }
        }
    }

    private fun clearBuffers() {
        buffers.values.forEach { it.delete() }
        buffers.clear()
    }

    override fun close() {
        clearBuffers()
        failedEntities.clear()
        modelSet = null
    }
}
