package im.ringlo.recall.client.scene

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.sqrt
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.animal.parrot.Parrot
import net.minecraft.world.phys.Vec3

class SceneEntityElement(
    val entity: Entity,
    val location: Vec3,
    var pose: String = "face_point_of_interest",
) {
    var offset = Vec3.ZERO
    var rotation = Vec3.ZERO
    var fade = 1f
    var transition = Vec3.ZERO

    fun tick(scene: SceneInstance) {
        entity.xOld = entity.x
        entity.yOld = entity.y
        entity.zOld = entity.z
        entity.tickCount++
        entity.xRotO = entity.xRot
        entity.yRotO = entity.yRot
        entity.setPos(location.add(offset).add(transition))
        entity.xRot = rotation.x.toFloat()
        entity.yRot = rotation.y.toFloat()
        val parrot = entity as? Parrot ?: return
        parrot.oFlap = parrot.flap
        parrot.oFlapSpeed = parrot.flapSpeed
        parrot.setOnGround(true)
        parrot.setRecordPlayingNearby(BlockPos.ZERO, pose == "dance")
        when (pose) {
            "dance" -> parrot.yRot = rotation.y.toFloat() - entity.tickCount * 2f
            "flappy" -> {
                parrot.setOnGround(false)
                val speed =
                    parrot.position().subtract(parrot.xOld, parrot.yOld, parrot.zOld).length()
                parrot.flapSpeed =
                    if (speed == 0.0) 0f
                    else
                        (sin((entity.tickCount % 100) * (speed * 15).coerceAtMost(8.0)) + 1)
                            .toFloat()
                parrot.flap += parrot.flapSpeed
            }
            "face_point_of_interest",
            "face_cursor" -> {
                val target = if (pose == "face_cursor") scene.cursor else scene.pointOfInterest
                val delta = target.subtract(parrot.eyePosition)
                val yaw = (-atan2(delta.z, delta.x) * 180 / Math.PI + 90).toFloat()
                val pitch =
                    (-atan2(delta.y, sqrt(delta.x * delta.x + delta.z * delta.z)) * 180 / Math.PI)
                        .toFloat()
                parrot.yRot = parrot.yRotO + Mth.wrapDegrees(yaw - parrot.yRotO) * .4f
                parrot.xRot = parrot.xRotO + Mth.wrapDegrees(pitch - parrot.xRotO) * .4f
            }
            else -> error("Unknown parrot pose $pose")
        }
        parrot.yHeadRotO = parrot.yHeadRot
        parrot.yHeadRot = parrot.yRot
    }

    fun render(
        pose: PoseStack,
        queue: SubmitNodeCollector,
        camera: CameraRenderState,
        partialTick: Float,
    ) {
        if (fade <= 0 || !entity.isAlive) return
        val dispatcher = Minecraft.getInstance().entityRenderDispatcher
        val state = dispatcher.extractEntity(entity, partialTick)
        pose.pushPose()
        try {
            pose.translate(
                location.x + offset.x + transition.x,
                location.y + offset.y + transition.y,
                location.z + offset.z + transition.z,
            )
            pose.mulPose(Axis.ZP.rotationDegrees(rotation.z.toFloat()))
            dispatcher.submit(state, camera, 0.0, 0.0, 0.0, pose, queue)
        } finally {
            pose.popPose()
        }
    }
}
