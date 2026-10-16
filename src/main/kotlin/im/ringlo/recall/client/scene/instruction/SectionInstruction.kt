package im.ringlo.recall.client.scene.instruction

import im.ringlo.recall.api.client.scene.SceneInstruction
import im.ringlo.recall.api.scene.SceneArguments
import im.ringlo.recall.client.scene.SceneInstance
import im.ringlo.recall.client.scene.SceneSection
import im.ringlo.recall.content.ContentSnapshot
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3

class SectionInstruction(private val step: ContentSnapshot.SceneStep) : SceneInstruction() {
    private val parameters = step.parameters
    private var section: SceneSection? = null
    private var merge: SceneSection? = null
    private var start = Vec3.ZERO
    private val action = step.action

    override fun start(scene: SceneInstance) {
        val name = SceneArguments.text(parameters, "link", step.group)
        val selected = scene.select(parameters)
        section =
            when (action) {
                "world/show_section",
                "world/show_section_and_merge",
                "world/glue_block_onto",
                "world/hide_section" -> {
                    merge =
                        if (action == "world/hide_section") null
                        else scene.section(if (parameters.has("link")) name else "main")
                    scene.detach("@${scene.nextElementId()}", selected)
                }
                "world/show_independent_section",
                "world/show_independent_section_immediately",
                "world/make_section_independent" -> scene.detach(name, selected)
                else -> scene.section(name)
            }
        val section = section!!
        when (action) {
            "move",
            "world/move_section" -> start = section.frame.offset
            "rotate",
            "world/rotate_section" -> start = section.frame.rotation
            "world/configure_center_of_rotation" ->
                section.frame.pivot = SceneArguments.vector(parameters, "anchor")
            "world/configure_stabilization" ->
                section.frame.stabilization = SceneArguments.vector(parameters, "anchor")
            "world/make_section_independent",
            "world/show_independent_section_immediately" -> section.frame.fade = 1f
            else -> {
                section.frame.fade = if (action in SHOW) 0f else 1f
                merge?.let {
                    section.frame.offset = it.frame.offset
                    section.frame.rotation = it.frame.rotation
                    section.frame.pivot = it.frame.pivot
                }
            }
        }
    }

    override fun tick(scene: SceneInstance, progress: Float) {
        val frame = section?.frame ?: return
        when (action) {
            "move",
            "world/move_section" ->
                frame.offset =
                    start.add(
                        SceneArguments.vector(parameters, "offset").scale(progress.toDouble())
                    )
            "rotate",
            "world/rotate_section" ->
                frame.rotation =
                    start.add(
                        SceneArguments.vector(parameters, "rotation").scale(progress.toDouble())
                    )
            in SHOW -> {
                frame.fade = progress
                frame.transition = offset().scale((1 - progress).toDouble())
            }
            in HIDE -> {
                frame.fade = 1 - progress
                frame.transition = offset().scale(progress.toDouble())
            }
        }
    }

    private fun offset(): Vec3 {
        if (parameters.has("offset")) return SceneArguments.vector(parameters, "offset")
        if (!action.startsWith("world/")) return Vec3.ZERO
        val direction =
            Direction.valueOf(SceneArguments.text(parameters, "direction", "down").uppercase())
        return Vec3(direction.unitVec3.x, direction.unitVec3.y, direction.unitVec3.z).scale(.5)
    }

    override fun finish(scene: SceneInstance) {
        val section = section ?: return
        val target = merge ?: return
        target.positions = target.positions + section.positions
        target.frame.fade = 1f
        scene.removeSection(section.name)
    }

    override fun reset(scene: SceneInstance) {
        section = null
        merge = null
        start = Vec3.ZERO
    }

    companion object {
        val SHOW =
            setOf(
                "reveal",
                "world/show_section",
                "world/show_section_and_merge",
                "world/glue_block_onto",
                "world/show_independent_section",
            )
        val HIDE = setOf("hide", "world/hide_section", "world/hide_independent_section")
        val ACTIONS =
            SHOW +
                HIDE +
                setOf(
                    "move",
                    "rotate",
                    "world/move_section",
                    "world/rotate_section",
                    "world/configure_center_of_rotation",
                    "world/configure_stabilization",
                    "world/make_section_independent",
                    "world/show_independent_section_immediately",
                )
    }
}
