package im.ringlo.recall.content.scene

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import im.ringlo.recall.api.scene.SceneActions
import im.ringlo.recall.api.scene.SceneArguments
import im.ringlo.recall.content.ContentSnapshot
import java.io.InputStream
import java.io.Reader
import net.minecraft.core.BlockPos
import net.minecraft.nbt.TagParser
import net.minecraft.resources.Identifier

object SceneCodec {
    fun resource(
        id: Identifier,
        reader: Reader,
        nbt: InputStream,
    ): ContentSnapshot.SceneDefinition {
        val json = JsonParser.parseReader(reader).asJsonObject
        require(
            json.keySet().all { it in setOf("timeline", "zeroLayer") } && json.has("timeline")
        ) {
            "Scene $id requires timeline JSON and a paired structure NBT"
        }
        val zero =
            SceneArguments.number(json, "zeroLayer")
                .also { require(it == it.toInt().toFloat()) }
                .toInt()
        val structure = SceneStructure.read(nbt, zero)
        val steps = timeline(json.getAsJsonArray("timeline"))
        val groups = linkedMapOf<BlockPos, String>()
        val declared = hashSetOf<String>()
        steps
            .filter {
                it.type.namespace == "recall" &&
                    it.action in setOf("reveal", "hide", "move", "rotate") &&
                    it.parameters.has("selection")
            }
            .forEach { step ->
                require(step.group != "main" && declared.add(step.group)) {
                    "Declare selection for group ${step.group} exactly once"
                }
                val selection = SceneArguments.selection(step.parameters)!!
                val selected = structure.blocks.filter { BlockPos(it.x, it.y, it.z) in selection }
                require(selected.isNotEmpty()) { "Group ${step.group} selects no structure blocks" }
                require(
                    selection.all {
                        it.x in 0 until structure.size[0] &&
                            it.y + zero in 0 until structure.size[1] &&
                            it.z in 0 until structure.size[2]
                    }
                ) {
                    "Selection exceeds structure bounds"
                }
                selected.forEach { block ->
                    require(groups.put(BlockPos(block.x, block.y, block.z), step.group) == null) {
                        "Overlapping scene groups"
                    }
                }
            }
        val blocks =
            structure.blocks.map { it.copy(group = groups[BlockPos(it.x, it.y, it.z)] ?: "main") }
        val title =
            steps.firstOrNull { it.action == "title" }?.parameters?.get("text")?.asString ?: id.path
        return validate(
            ContentSnapshot.SceneDefinition(
                id,
                title,
                steps.maxOfOrNull { it.time + it.duration } ?: 0f,
                steps,
                blocks,
                structure.entities,
                structure.size,
                zero,
            )
        )
    }

    private fun timeline(
        array: JsonArray,
        authored: Boolean = true,
    ): List<ContentSnapshot.SceneStep> {
        require(array.size() <= 2048) { "Scene exceeds 2048 instructions" }
        return array
            .map { element ->
                val json = element.asJsonObject.deepCopy()
                val type =
                    SceneArguments.id(
                        json.remove("type")?.asString ?: error("Timeline entry requires type")
                    )
                val at = json.remove("at")?.asFloat ?: 0f
                val duration =
                    json.remove("duration")?.asFloat
                        ?: if (
                            authored &&
                                type.toString() == "recall:effects/emit_particles" &&
                                json.has("cycles")
                        )
                            json.get("cycles").asFloat / 20f
                        else defaultDuration(type)
                ContentSnapshot.SceneStep(
                    at,
                    type,
                    duration + if (authored) SceneTimeline.fadeDuration(type) else 0f,
                    json,
                )
            }
            .sortedBy { it.time }
    }

    private fun defaultDuration(type: Identifier): Float =
        when (type.toString()) {
            "recall:world/show_section",
            "recall:show_base_plate",
            "recall:world/show_section_and_merge",
            "recall:world/show_independent_section",
            "recall:world/hide_section",
            "recall:world/hide_independent_section",
            "recall:world/glue_block_onto",
            "recall:special/hide_element" -> .75f
            "recall:special/create_birb",
            "recall:special/create_cart",
            "recall:rotate_camera_y" -> .5f
            "recall:lazy_keyframe" -> .25f
            else -> 0f
        }

    fun decode(json: JsonObject): ContentSnapshot.SceneDefinition =
        validate(
            ContentSnapshot.SceneDefinition(
                Identifier.parse(json.get("id").asString),
                json.get("title").asString,
                json.get("duration").asFloat,
                timeline(json.getAsJsonArray("timeline"), false),
                json.getAsJsonArray("blocks").map { element ->
                    val block = element.asJsonObject
                    fun coordinate(key: String): Int {
                        val value = block.get(key).asDouble
                        require(
                            value.isFinite() &&
                                value in -128.0..128.0 &&
                                value == value.toInt().toDouble()
                        )
                        return value.toInt()
                    }
                    ContentSnapshot.SceneBlock(
                        coordinate("x"),
                        coordinate("y"),
                        coordinate("z"),
                        block.get("state").asString,
                        block.get("group").asString,
                        block.get("nbt")?.asString,
                    )
                },
                json.getAsJsonArray("entities").map { it.asString },
                json.getAsJsonArray("size").map { it.asInt },
                json.get("zeroLayer").asInt,
            )
        )

    fun validate(scene: ContentSnapshot.SceneDefinition): ContentSnapshot.SceneDefinition {
        require(scene.duration.isFinite() && scene.duration in 0f..600f) {
            "Scene ${scene.id} exceeds 600 seconds"
        }
        require(
            scene.blocks.size <= SceneStructure.MAX_BLOCKS &&
                scene.entities.size <= 256 &&
                scene.steps.size <= 2048
        ) {
            "Scene ${scene.id} exceeds content limits"
        }
        require(
            scene.size.size == 3 &&
                scene.size.all { it in 1..128 } &&
                scene.zeroLayer in 0 until scene.size[1]
        ) {
            "Invalid structure size"
        }
        val occupied = hashSetOf<BlockPos>()
        scene.blocks.forEach { block ->
            require(
                block.x in 0 until scene.size[0] &&
                    block.y + scene.zeroLayer in 0 until scene.size[1] &&
                    block.z in 0 until scene.size[2]
            ) {
                "Block outside structure bounds"
            }
            require(occupied.add(BlockPos(block.x, block.y, block.z))) { "Duplicate scene block" }
            require(block.state.isNotBlank() && block.state.length <= 512 && '{' !in block.state) {
                "Invalid scene block state"
            }
            require(block.group.matches(Regex("[a-zA-Z0-9_:/.-]{1,128}"))) { "Invalid scene group" }
            block.nbt?.let(::validateNbt)
        }
        scene.entities.forEach(::validateNbt)
        scene.steps.forEach { step ->
            require(
                step.time.isFinite() &&
                    step.duration.isFinite() &&
                    step.time >= 0 &&
                    step.duration >= 0 &&
                    step.time + step.duration <= scene.duration
            ) {
                "Invalid scene instruction time"
            }
            SceneActions.require(step.type).validate(step.parameters)
        }
        SceneTimeline.validateReferences(scene)
        return scene
    }

    private fun validateNbt(value: String) {
        require(value.length <= 65536) { "Scene NBT entry is too large" }
        TagParser.parseCompoundFully(value)
    }

    fun encode(scene: ContentSnapshot.SceneDefinition) =
        JsonObject().apply {
            addProperty("id", scene.id.toString())
            addProperty("title", scene.title)
            addProperty("duration", scene.duration)
            addProperty("zeroLayer", scene.zeroLayer)
            add("size", JsonArray().apply { scene.size.forEach(::add) })
            add("entities", JsonArray().apply { scene.entities.forEach(::add) })
            add(
                "blocks",
                JsonArray().apply {
                    scene.blocks.forEach { block ->
                        add(
                            JsonObject().apply {
                                addProperty("x", block.x)
                                addProperty("y", block.y)
                                addProperty("z", block.z)
                                addProperty("state", block.state)
                                addProperty("group", block.group)
                                block.nbt?.let { addProperty("nbt", it) }
                            }
                        )
                    }
                },
            )
            add(
                "timeline",
                JsonArray().apply {
                    scene.steps.forEach { step ->
                        add(
                            step.parameters.deepCopy().apply {
                                addProperty("type", step.type.toString())
                                addProperty("at", step.time)
                                addProperty("duration", step.duration)
                            }
                        )
                    }
                },
            )
        }
}
