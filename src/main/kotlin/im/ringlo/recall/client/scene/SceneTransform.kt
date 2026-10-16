package im.ringlo.recall.client.scene

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import kotlin.math.sqrt
import net.createmod.catnip.api.client.gui.UIRenderHelper
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.world.phys.AABB
import org.joml.Matrix4f
import org.joml.Vector3f

class SceneTransform {
    var pitch = -35f
    var yaw = 145f
    var zoom = 1f
    var sceneYaw = 0f
    var scenePitch = 0f
    var sceneScale = 1f
    var sceneOffset = 0f

    fun reset() {
        pitch = -35f
        yaw = 145f
        zoom = 1f
    }

    fun matrix(bounds: AABB, width: Int, height: Int, guiScale: Int): Matrix4f {
        val pose = PoseStack()
        pose.translate(width * guiScale / 2f, (height / 2f - sceneOffset) * guiScale, -600f)
        pose.mulPose(Axis.XP.rotationDegrees(pitch + scenePitch))
        pose.mulPose(Axis.YP.rotationDegrees(yaw + sceneYaw))
        UIRenderHelper.flipForGuiRender(pose)
        val diameter =
            sqrt(
                    bounds.xsize * bounds.xsize +
                        bounds.ysize * bounds.ysize +
                        bounds.zsize * bounds.zsize
                )
                .toFloat()
                .coerceAtLeast(1f)
        val scale = minOf(width, height) * 0.85f / diameter * guiScale * zoom * sceneScale
        pose.scale(scale, scale, scale)
        val center = bounds.center
        pose.translate(-center.x, -center.y, -center.z)
        return Matrix4f(pose.last().pose())
    }

    fun camera(bounds: AABB): CameraRenderState =
        CameraRenderState().apply {
            val rotation =
                Axis.YP.rotationDegrees(-yaw - sceneYaw)
                    .mul(Axis.XP.rotationDegrees(-pitch - scenePitch))
            val direction = rotation.transform(Vector3f(0f, 0f, -16f))
            pos =
                bounds.center.add(
                    direction.x.toDouble(),
                    direction.y.toDouble(),
                    direction.z.toDouble(),
                )
            orientation.set(rotation)
            xRot = pitch + scenePitch
            yRot = yaw + sceneYaw
            initialized = true
        }
}
