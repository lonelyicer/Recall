package im.ringlo.recall.client.scene.instruction

import im.ringlo.recall.api.client.scene.SceneInstruction
import im.ringlo.recall.api.scene.SceneArguments
import im.ringlo.recall.client.scene.SceneInstance
import im.ringlo.recall.client.scene.SceneOverlay
import im.ringlo.recall.content.ContentSnapshot
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class OverlayInstruction(
    private val step: ContentSnapshot.SceneStep,
    private val configure: (SceneOverlay) -> Unit = {},
) : SceneInstruction() {
    private var slot = ""
    private var overlay: SceneOverlay? = null
    private val p = step.parameters
    private val action = step.action.substringAfter('/')

    override fun start(scene: SceneInstance) {
        slot = SceneArguments.text(p, "slot", "overlay_${scene.nextElementId()}")
        if (
            action in
                setOf("show_text", "show_outline_with_text", "show_controls", "caption", "tooltip")
        ) {
            overlay =
                SceneOverlay(step).also {
                    configure(it)
                    scene.overlays[slot] = it
                }
        }
    }

    override fun tick(scene: SceneInstance, progress: Float) {
        overlay?.fade =
            if (step.duration == 0f) 1f
            else {
                val elapsed = progress * step.duration
                val fade =
                    minOf(elapsed / .25f, (step.duration - elapsed) / .25f, 1f).coerceAtLeast(0f)
                fade * fade
            }
        val color = SceneOverlay.color(p)
        when (action) {
            "highlight" -> scene.section(step.group).frame.highlighted = progress < 1f
            "show_outline",
            "show_outline_with_text" ->
                scene.outliner.showCluster(slot, scene.select(p)).colored(color).lineWidth(1f / 16)
            "chase_bounding_box_outline" ->
                scene.outliner
                    .chaseAABB(
                        slot,
                        AABB(SceneArguments.vector(p, "from"), SceneArguments.vector(p, "to")),
                    )
                    .colored(color)
                    .lineWidth(1f / 16)
            "show_line",
            "show_big_line" ->
                scene.outliner
                    .showLine(
                        slot,
                        SceneArguments.vector(p, "from"),
                        SceneArguments.vector(p, "to"),
                    )
                    .colored(color)
                    .lineWidth(if (action == "show_big_line") 1f / 8 else 1f / 32)
            "show_scroll_input",
            "show_centered_scroll_input",
            "show_repeater_scroll_input",
            "show_filter_slot_input" -> {
                val side = Direction.valueOf(SceneArguments.text(p, "side", "up").uppercase())
                var location =
                    if (p.has("position")) SceneArguments.vector(p, "position")
                    else Vec3.atCenterOf(SceneArguments.position(p)).add(side.unitVec3.scale(.5))
                val axis = side.axis
                var expand =
                    Vec3(
                        if (axis == Direction.Axis.X) 1.0 / 16 else .25,
                        if (axis == Direction.Axis.Y) 1.0 / 16 else .25,
                        if (axis == Direction.Axis.Z) 1.0 / 16 else .25,
                    )
                if (action == "show_repeater_scroll_input") {
                    location = Vec3.atCenterOf(SceneArguments.position(p)).add(0.0, -5.0 / 16, 0.0)
                    expand = Vec3(1.0 / 6, 1.0 / 16, 1.0 / 6)
                }
                if (action == "show_filter_slot_input") {
                    expand = Vec3(.1, .1, .1)
                    if (p.has("side")) {
                        location = location.add(side.unitVec3.scale(-3.0 / 128))
                        expand =
                            Vec3(
                                if (axis == Direction.Axis.X) 0.0 else 11.0 / 128,
                                if (axis == Direction.Axis.Y) 0.0 else 11.0 / 128,
                                if (axis == Direction.Axis.Z) 0.0 else 11.0 / 128,
                            )
                    }
                }
                scene.outliner
                    .chaseAABB(
                        slot,
                        AABB(location, location)
                            .inflate(
                                if (progress == 0f) 0.0 else expand.x,
                                if (progress == 0f) 0.0 else expand.y,
                                if (progress == 0f) 0.0 else expand.z,
                            ),
                    )
                    .colored(0xffffff)
                    .lineWidth(1f / 15)
            }
        }
    }

    override fun finish(scene: SceneInstance) {
        scene.overlays.remove(slot)
        if (action == "highlight") scene.section(step.group).frame.highlighted = false
    }

    override fun reset(scene: SceneInstance) {
        overlay = null
    }
}
