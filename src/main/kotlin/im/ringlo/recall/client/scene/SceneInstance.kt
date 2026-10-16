package im.ringlo.recall.client.scene

import com.google.gson.JsonObject
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.brigadier.StringReader
import im.ringlo.recall.api.scene.SceneArguments
import im.ringlo.recall.client.scene.instruction.SceneInstructions
import im.ringlo.recall.client.scene.instruction.ScheduledInstruction
import im.ringlo.recall.content.ContentSnapshot
import java.util.Random
import net.createmod.catnip.api.client.outliner.Outliner
import net.createmod.catnip.api.client.render.SuperRenderTypeBuffer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.commands.arguments.blocks.BlockStateParser
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.TagParser
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class SceneInstance(val definition: ContentSnapshot.SceneDefinition, val sourceLevel: Level) :
    AutoCloseable {
    val world = SceneLevel(sourceLevel)
    val sections = linkedMapOf<String, SceneSection>()
    val entityLinks = linkedMapOf<String, Entity>()
    val specialElements = linkedMapOf<String, SceneEntityElement>()
    val overlays = linkedMapOf<String, SceneOverlay>()
    val outliner = Outliner()
    val random = Random(0)
    val keyframes = sortedSetOf<Float>()

    fun addKeyframe(time: Float) {
        require(time.isFinite() && time in 0f..definition.duration)
        keyframes.add(time)
    }

    var title = definition.title
    var cameraYaw = 0f
    var cameraPitch = 0f
    var cameraScale = 1f
    var cameraOffset = 0f
    var shadow = true
    var finished = false
    var nextUpEnabled = true
    var pointOfInterest = Vec3.ZERO
    var cursor = Vec3.ZERO
    var basePlate = emptySet<BlockPos>()
    private val instructions =
        definition.steps.map { ScheduledInstruction(it, SceneInstructions.create(it)) }
    private var elementId = 0
    private var tick = -1L
    private var time = 0f
    val bounds: AABB

    init {
        try {
            definition.blocks.forEach { block ->
                val pos = BlockPos(block.x, block.y, block.z)
                world.setBlock(pos, parseState(block.state), 0)
                block.nbt?.let { world.loadBlockEntity(pos, TagParser.parseCompoundFully(it)) }
            }
            definition.entities.forEach { world.loadEntity(TagParser.parseCompoundFully(it)) }
            world.createBackup()
            var extent: AABB? = null
            definition.blocks.forEach { block ->
                var box = AABB(BlockPos(block.x, block.y, block.z))
                extent = extent?.minmax(box) ?: box
                definition.steps
                    .filter {
                        it.group == block.group && it.action in setOf("move", "world/move_section")
                    }
                    .forEach { step ->
                        box = box.move(SceneArguments.vector(step.parameters, "offset"))
                        extent = extent!!.minmax(box)
                    }
            }
            bounds = extent ?: AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
            reset()
        } catch (exception: Exception) {
            close()
            throw exception
        }
    }

    fun parseState(value: String): BlockState {
        val reader = StringReader(value)
        val state =
            BlockStateParser.parseForBlock(
                    sourceLevel.registryAccess().lookupOrThrow(Registries.BLOCK),
                    reader,
                    false,
                )
                .blockState()
        require(!reader.canRead()) { "Invalid scene block state $value" }
        return state
    }

    fun nextElementId(): Int = elementId++

    fun section(name: String): SceneSection =
        checkNotNull(sections[name]) { "Unknown scene section $name" }

    fun select(parameters: JsonObject): Set<BlockPos> =
        SceneArguments.selection(parameters)
            ?: parameters.get("group")?.asString?.let { section(it).positions }
            ?: parameters.get("link")?.asString?.let { section(it).positions }
            ?: world.allPositions.toSet()

    fun detach(name: String, selected: Set<BlockPos>): SceneSection {
        sections.values.forEach { it.positions -= selected }
        return sections
            .getOrPut(name) { SceneSection(name, emptySet()) }
            .also {
                it.positions = selected
                it.frame.pivot = it.bounds().center
            }
    }

    fun removeSection(name: String) {
        sections.remove(name)?.close()
    }

    private fun reset() {
        world.restore()
        sections.values.forEach { it.close() }
        sections.clear()
        entityLinks.clear()
        specialElements.clear()
        overlays.clear()
        outliner.outlines.keys.toList().forEach(outliner::remove)
        random.setSeed(0)
        world.random.setSeed(0)
        elementId = 0
        tick = -1
        time = 0f
        cameraYaw = 0f
        cameraPitch = 0f
        cameraScale = 1f
        cameraOffset = 0f
        title = definition.title
        shadow = true
        finished = false
        nextUpEnabled = true
        pointOfInterest = bounds.center
        basePlate = world.allPositions.filter { it.y == 0 }.toSet()
        val explicitSections =
            definition.steps.any {
                it.action.startsWith("world/show_") || it.action == "show_base_plate"
            }
        definition.blocks
            .groupBy { it.group }
            .forEach { (name, blocks) ->
                sections[name] =
                    SceneSection(name, blocks.map { BlockPos(it.x, it.y, it.z) }.toSet()).also {
                        section ->
                        val firstVisibility =
                            definition.steps.firstOrNull {
                                it.group == name && it.action in setOf("reveal", "hide")
                            }
                        section.frame.fade =
                            if (
                                firstVisibility?.action == "reveal" ||
                                    explicitSections && name == "main"
                            )
                                0f
                            else 1f
                        section.frame.pivot = section.bounds().center
                    }
            }
        sections.putIfAbsent("main", SceneSection("main", emptySet()))
        instructions.forEach { it.reset(this) }
    }

    fun seek(seconds: Float) {
        require(seconds.isFinite())
        val target = seconds.coerceIn(0f, definition.duration)
        val targetTick = (target * 20.0 + .00001).toLong()
        if (target < time) reset()
        while (tick < targetTick) {
            tick++
            world.sceneTicks = tick
            sections.values.forEach { it.capturePreviousFrame() }
            outliner.tickOutlines()
            instructions.forEach { it.tick(this, tick) }
            if (tick > 0) {
                sections.values.forEach { it.tick(world) }
                specialElements.values.forEach { it.tick(this) }
                world.tickEntities(specialElements.values.map { it.entity }.toSet())
            }
        }
        time = target
        if (target >= definition.duration) finished = true
    }

    fun render(
        buffer: SuperRenderTypeBuffer,
        queue: SubmitNodeCollector,
        pose: PoseStack,
        time: Float,
        camera: CameraRenderState,
        opacity: Float,
    ) {
        val partialTick = time * 20f % 1f
        sections.values.forEach {
            it.render(
                world,
                it.interpolatedFrame(partialTick),
                pose,
                buffer,
                queue,
                camera,
                partialTick,
                opacity,
            )
        }
        val special = specialElements.values.map { it.entity }.toSet()
        val dispatcher = Minecraft.getInstance().entityRenderDispatcher
        world.entityList
            .filter { it !in special && it.isAlive }
            .forEach { entity ->
                val state = dispatcher.extractEntity(entity, partialTick)
                dispatcher.submit(state, camera, state.x, state.y, state.z, pose, queue)
            }
        specialElements.values.forEach { it.render(pose, queue, camera, partialTick) }
        outliner.renderOutlines(pose, buffer, Vec3.ZERO, partialTick)
    }

    override fun close() {
        sections.values.forEach { it.close() }
        sections.clear()
        entityLinks.clear()
        specialElements.clear()
        overlays.clear()
        outliner.outlines.keys.toList().forEach(outliner::remove)
        world.clear()
    }
}
