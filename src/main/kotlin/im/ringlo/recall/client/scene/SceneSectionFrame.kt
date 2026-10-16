package im.ringlo.recall.client.scene

import net.minecraft.world.phys.Vec3

data class SceneSectionFrame(
    var offset: Vec3 = Vec3.ZERO,
    var fade: Float = 1f,
    var highlighted: Boolean = false,
    var rotation: Vec3 = Vec3.ZERO,
    var pivot: Vec3 = Vec3.ZERO,
    var stabilization: Vec3? = null,
    var transition: Vec3 = Vec3.ZERO,
)
