package im.ringlo.recall.client.scene

import net.minecraft.client.renderer.culling.Frustum
import org.joml.Matrix4f

class SceneParticleFrustum : Frustum(Matrix4f(), Matrix4f()) {
    override fun pointInFrustum(x: Double, y: Double, z: Double): Boolean = true
}
