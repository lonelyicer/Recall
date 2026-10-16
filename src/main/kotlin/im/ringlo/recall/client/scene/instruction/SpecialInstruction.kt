package im.ringlo.recall.client.scene.instruction

import im.ringlo.recall.api.client.scene.SceneInstruction
import im.ringlo.recall.api.scene.SceneArguments
import im.ringlo.recall.client.scene.SceneEntityElement
import im.ringlo.recall.client.scene.SceneInstance
import im.ringlo.recall.client.scene.SceneSection
import im.ringlo.recall.content.ContentSnapshot
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.Identifier
import net.minecraft.util.ProblemReporter
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.animal.parrot.Parrot
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart
import net.minecraft.world.level.storage.TagValueInput
import net.minecraft.world.phys.Vec3

class SpecialInstruction(private val step: ContentSnapshot.SceneStep) : SceneInstruction() {
    private val p = step.parameters
    private var element: SceneEntityElement? = null
    private var section: SceneSection? = null
    private var initial = Vec3.ZERO

    override fun start(scene: SceneInstance) {
        val action = step.action.substringAfter('/')
        if (action == "move_point_of_interest") {
            scene.pointOfInterest = SceneArguments.vector(p, "position")
            return
        }
        val link = SceneArguments.text(p, "link", "special_${scene.nextElementId()}")
        if (action == "create_birb" || action == "create_cart") {
            val id =
                Identifier.parse(
                    if (action == "create_birb") "minecraft:parrot"
                    else SceneArguments.text(p, "entity", "minecraft:minecart")
                )
            val entity =
                BuiltInRegistries.ENTITY_TYPE.getValue(id)
                    .create(scene.world, EntitySpawnReason.LOAD) ?: error("Cannot create $id")
            require(entity is Parrot || entity is AbstractMinecart) {
                "Special entity must be a parrot or minecart"
            }
            val location = SceneArguments.vector(p, "position")
            entity.setPos(location)
            if (entity is Parrot) {
                val tag =
                    scene.world
                        .saveEntity(entity)
                        .merge(
                            CompoundTag().apply {
                                putString("variant", SceneArguments.text(p, "variant", "red_blue"))
                            }
                        )
                entity.load(
                    TagValueInput.create(
                        ProblemReporter.DISCARDING,
                        scene.world.registryAccess(),
                        tag,
                    )
                )
            }
            scene.world.addFreshEntity(entity)
            scene.entityLinks[link] = entity
            element =
                SceneEntityElement(
                        entity,
                        location,
                        SceneArguments.text(p, "pose", "face_point_of_interest"),
                    )
                    .also {
                        it.rotation =
                            Vec3(
                                0.0,
                                SceneArguments.number(
                                        p,
                                        "angle",
                                        if (action == "create_birb") 180f else 0f,
                                    )
                                    .toDouble(),
                                0.0,
                            )
                        scene.specialElements[link] = it
                    }
            return
        }
        element = scene.specialElements[link]
        if (action == "hide_element" && element == null) {
            section = scene.section(link)
            return
        }
        val element = checkNotNull(element) { "Unknown special element $link" }
        when (action) {
            "change_birb_pose" -> element.pose = SceneArguments.text(p, "pose")
            "move_parrot",
            "move_cart" -> initial = element.offset
            "rotate_parrot",
            "rotate_cart" -> initial = element.rotation
            "hide_element" -> Unit
            else -> error("Unknown special instruction $action")
        }
    }

    override fun tick(scene: SceneInstance, progress: Float) {
        section?.let { section ->
            section.frame.fade = 1 - progress
            section.frame.transition =
                Direction.valueOf(SceneArguments.text(p, "direction", "down").uppercase())
                    .unitVec3
                    .scale(progress * .5)
        }
        val element = element ?: return
        when (step.action.substringAfter('/')) {
            "create_birb",
            "create_cart" -> {
                element.fade = progress
                element.transition = Vec3(0.0, -.5 * (1 - progress), 0.0)
            }
            "hide_element" -> {
                element.fade = 1 - progress
                val d = Direction.valueOf(SceneArguments.text(p, "direction", "down").uppercase())
                element.transition = d.unitVec3.scale(progress * .5)
            }
            "move_parrot",
            "move_cart" ->
                element.offset =
                    initial.add(SceneArguments.vector(p, "offset").scale(progress.toDouble()))
            "rotate_parrot",
            "rotate_cart" ->
                element.rotation =
                    initial.add(SceneArguments.vector(p, "rotation").scale(progress.toDouble()))
        }
    }

    override fun reset(scene: SceneInstance) {
        element = null
        section = null
        initial = Vec3.ZERO
    }
}
