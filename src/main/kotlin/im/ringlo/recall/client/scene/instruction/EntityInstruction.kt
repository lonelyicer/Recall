package im.ringlo.recall.client.scene.instruction

import im.ringlo.recall.api.client.scene.SceneInstruction
import im.ringlo.recall.api.scene.SceneArguments
import im.ringlo.recall.client.scene.SceneInstance
import im.ringlo.recall.content.ContentSnapshot
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.TagParser
import net.minecraft.resources.Identifier
import net.minecraft.util.ProblemReporter
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.TagValueInput

class EntityInstruction(private val step: ContentSnapshot.SceneStep) : SceneInstruction() {
    override fun start(scene: SceneInstance) {
        val p = step.parameters
        val action = step.action.substringAfter('/')
        val patch =
            p.get("nbt")?.let {
                TagParser.parseCompoundFully(if (it.isJsonPrimitive) it.asString else it.toString())
            } ?: CompoundTag()
        if (action == "create_entity" || action == "create_item_entity") {
            val position = SceneArguments.vector(p, "position")
            val entity =
                if (action == "create_item_entity") {
                    val item =
                        BuiltInRegistries.ITEM.getValue(Identifier.parse(p.get("item").asString))
                    ItemEntity(
                        scene.world,
                        position.x,
                        position.y,
                        position.z,
                        ItemStack(item, (p.get("count")?.asInt ?: 1).coerceIn(1, 99)),
                    )
                } else {
                    val type =
                        BuiltInRegistries.ENTITY_TYPE.getValue(
                            Identifier.parse(p.get("entity").asString)
                        )
                    type.create(scene.world, EntitySpawnReason.LOAD)
                        ?: error("Cannot create scene entity")
                }
            entity.setPos(position)
            entity.deltaMovement = SceneArguments.vector(p, "motion")
            if (!patch.isEmpty)
                entity.load(
                    TagValueInput.create(
                        ProblemReporter.DISCARDING,
                        scene.world.registryAccess(),
                        scene.world.saveEntity(entity).merge(patch),
                    )
                )
            scene.world.addFreshEntity(entity)
            scene.entityLinks[SceneArguments.text(p, "link", "entity_${scene.nextElementId()}")] =
                entity
            return
        }
        val entities =
            if (action == "modify_entity")
                listOf(
                    checkNotNull(scene.entityLinks[SceneArguments.text(p, "link")]) {
                        "Unknown scene entity link"
                    }
                )
            else scene.world.entityList.toList()
        val area = if (action == "modify_entities_inside") scene.select(p) else null
        entities
            .filter { entity ->
                (area == null || entity.blockPosition() in area) &&
                    (!p.has("entity") ||
                        BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString() ==
                            p.get("entity").asString)
            }
            .forEach { entity ->
                entity.load(
                    TagValueInput.create(
                        ProblemReporter.DISCARDING,
                        scene.world.registryAccess(),
                        scene.world.saveEntity(entity).merge(patch),
                    )
                )
                if (p.get("remove")?.asBoolean == true) entity.discard()
            }
    }

    companion object {
        val ACTIONS =
            setOf(
                "create_entity",
                "create_item_entity",
                "modify_entity",
                "modify_entities",
                "modify_entities_inside",
            )
    }
}
