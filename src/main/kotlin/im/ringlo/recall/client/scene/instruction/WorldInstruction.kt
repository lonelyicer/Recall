package im.ringlo.recall.client.scene.instruction

import im.ringlo.recall.api.client.scene.SceneInstruction
import im.ringlo.recall.api.scene.SceneArguments
import im.ringlo.recall.client.scene.SceneInstance
import im.ringlo.recall.content.ContentSnapshot
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.TagParser
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.Property

class WorldInstruction(private val step: ContentSnapshot.SceneStep) : SceneInstruction() {
    override fun start(scene: SceneInstance) {
        val p = step.parameters
        val action = step.action.substringAfter('/')
        val positions = scene.select(p)
        if (action == "restore_blocks") {
            scene.world.restoreBlocks(positions)
            return
        }
        positions.forEach { pos ->
            val old = scene.world.getBlockState(pos)
            var state = old
            when (action) {
                "modify_block_entity_nbt",
                "modify_block_entity" -> {
                    val entity = scene.world.getBlockEntity(pos) ?: return@forEach
                    if (
                        p.has("block_entity") &&
                            BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.type).toString() !=
                                p.get("block_entity").asString
                    )
                        return@forEach
                    val value = p.get("nbt")
                    val patch =
                        TagParser.parseCompoundFully(
                            if (value.isJsonPrimitive) value.asString else value.toString()
                        )
                    scene.world.loadBlockEntity(
                        pos,
                        entity.saveWithFullMetadata(scene.world.registryAccess()).merge(patch),
                    )
                    return@forEach
                }
                "increment_block_breaking_progress" -> {
                    val progress = (scene.world.breaking[pos] ?: -1) + 1
                    if (progress >= 9) {
                        scene.world.breaking.remove(pos)
                        state = Blocks.AIR.defaultBlockState()
                    } else {
                        scene.world.breaking[pos] = progress
                        return@forEach
                    }
                }
                "destroy_block" -> state = Blocks.AIR.defaultBlockState()
                "set_block",
                "set_blocks",
                "replace_blocks" -> {
                    if (action == "replace_blocks" && old.isAir) return@forEach
                    state = scene.parseState(p.get("state").asString)
                }
                "modify_block",
                "modify_blocks" -> {
                    if (old.isAir) return@forEach
                    p.getAsJsonObject("properties").entrySet().forEach { (name, value) ->
                        val property =
                            state.block.stateDefinition.getProperty(name)
                                ?: error("Unknown property $name on ${state.block}")
                        state = setProperty(state, property, value.asString)
                    }
                }
                "cycle_block_property" -> {
                    val property =
                        state.block.stateDefinition.getProperty(p.get("property").asString)
                            ?: return@forEach
                    state = cycle(state, property)
                }
                "toggle_redstone_power" -> {
                    if (state.hasProperty(BlockStateProperties.POWER))
                        state =
                            state.setValue(
                                BlockStateProperties.POWER,
                                if (state.getValue(BlockStateProperties.POWER) == 0) 15 else 0,
                            )
                    if (state.hasProperty(BlockStateProperties.POWERED))
                        state = state.cycle(BlockStateProperties.POWERED)
                    if (state.hasProperty(BlockStateProperties.LIT))
                        state = state.cycle(BlockStateProperties.LIT)
                }
                else -> error("Unhandled world action ${step.type}")
            }
            if (
                p.get("particles")?.asBoolean == true ||
                    action == "destroy_block" ||
                    action == "increment_block_breaking_progress"
            )
                scene.world.destroyEffects(pos, old)
            scene.world.setBlock(pos, state, 0)
            if (action in setOf("set_block", "set_blocks")) {
                val section = scene.section(SceneArguments.text(p, "link", step.group))
                section.positions = section.positions + pos
            }
        }
    }

    private fun <T : Comparable<T>> setProperty(
        state: BlockState,
        property: Property<T>,
        value: String,
    ): BlockState =
        state.setValue(
            property,
            property.getValue(value).orElseThrow {
                IllegalArgumentException("Invalid property ${property.name}=$value")
            },
        )

    private fun <T : Comparable<T>> cycle(state: BlockState, property: Property<T>) =
        state.cycle(property)

    companion object {
        val ACTIONS =
            setOf(
                "restore_blocks",
                "set_block",
                "set_blocks",
                "replace_blocks",
                "destroy_block",
                "modify_block",
                "modify_blocks",
                "cycle_block_property",
                "toggle_redstone_power",
                "modify_block_entity_nbt",
                "modify_block_entity",
                "increment_block_breaking_progress",
            )
    }
}
