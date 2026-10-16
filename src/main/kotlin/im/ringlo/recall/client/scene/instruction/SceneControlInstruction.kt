package im.ringlo.recall.client.scene.instruction

import im.ringlo.recall.api.client.scene.SceneInstruction
import im.ringlo.recall.api.scene.SceneArguments
import im.ringlo.recall.client.scene.SceneInstance
import im.ringlo.recall.client.scene.SceneSection
import im.ringlo.recall.content.ContentSnapshot
import net.minecraft.core.BlockPos

class SceneControlInstruction(private val step: ContentSnapshot.SceneStep) : SceneInstruction() {
    private var yaw = 0f
    private var pitch = 0f
    private var basePlate: SceneSection? = null
    private val p = step.parameters

    override fun start(scene: SceneInstance) {
        yaw = scene.cameraYaw
        pitch = scene.cameraPitch
        when (step.action) {
            "title" -> scene.title = SceneArguments.text(p, "text")
            "mark_as_finished" -> scene.finished = true
            "hide_all" -> {
                scene.sections.values.forEach { it.frame.fade = 0f }
                scene.world.entityList.forEach { it.isInvisible = true }
                scene.overlays.clear()
            }
            "configure_base_plate" -> {
                val size = SceneArguments.number(p, "size").toInt()
                require(size in 1..128)
                val x = SceneArguments.number(p, "x").toInt()
                val z = SceneArguments.number(p, "z").toInt()
                scene.basePlate =
                    BlockPos.betweenClosed(
                            BlockPos(x, 0, z),
                            BlockPos(x + size - 1, 0, z + size - 1),
                        )
                        .map { it.immutable() }
                        .toSet()
            }
            "show_base_plate" -> {
                basePlate = scene.detach("base_plate", scene.basePlate)
                basePlate!!.frame.fade = 0f
            }
            "debug/schematic" ->
                scene.detach("debug", scene.world.allPositions.toSet()).frame.fade = 1f
            "scale_scene_view" ->
                scene.cameraScale = SceneArguments.number(p, "scale", 1f).also { require(it > 0) }
            "set_scene_offset_y" -> scene.cameraOffset = SceneArguments.number(p, "offset_y")
            "remove_shadow" -> scene.shadow = false
            "set_next_up_enabled" -> scene.nextUpEnabled = p.get("enabled").asBoolean
            "wait",
            "keyframe",
            "lazy_keyframe",
            "rotate_camera_y",
            "camera" -> Unit
            else -> error("Unhandled scene instruction ${step.type}")
        }
    }

    override fun reset(scene: SceneInstance) {
        basePlate = null
    }

    override fun tick(scene: SceneInstance, progress: Float) {
        basePlate?.let {
            it.frame.fade = progress
            it.frame.transition = net.minecraft.world.phys.Vec3(0.0, -.5 * (1 - progress), 0.0)
        }
        if (step.action == "rotate_camera_y")
            scene.cameraYaw = yaw + SceneArguments.number(p, "degrees") * progress
        if (step.action == "camera") {
            scene.cameraYaw = yaw + (SceneArguments.number(p, "yaw", yaw) - yaw) * progress
            scene.cameraPitch =
                pitch + (SceneArguments.number(p, "pitch", pitch) - pitch) * progress
        }
    }
}
