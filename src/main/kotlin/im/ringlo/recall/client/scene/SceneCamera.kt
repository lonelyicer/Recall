package im.ringlo.recall.client.scene

import net.minecraft.client.Camera
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.world.phys.Vec3

class SceneCamera : Camera() {
    fun configure(state: CameraRenderState) {
        setPosition(Vec3.ZERO)
        setRotation(state.yRot, state.xRot, 0f)
    }
}
