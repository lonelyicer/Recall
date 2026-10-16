package im.ringlo.recall.client.scene

import net.createmod.catnip.api.client.level.wrapper.WrappedClientLevel
import net.createmod.catnip.api.client.platform.ModClientHooksHelper
import net.createmod.catnip.api.level.wrapper.SchematicLevel
import net.minecraft.client.renderer.block.BlockAndTintGetter
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.ProblemReporter
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.CardinalLighting
import net.minecraft.world.level.ColorResolver
import net.minecraft.world.level.Level
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.TagValueInput
import net.minecraft.world.level.storage.TagValueOutput

class SceneLevel(original: Level) : SchematicLevel(BlockPos.ZERO, original), BlockAndTintGetter {
    private val clientWorld by lazy {
        WrappedClientLevel.of(this)
    }
    private val originalBlocks = linkedMapOf<BlockPos, BlockState>()
    private val originalBlockEntities = linkedMapOf<BlockPos, CompoundTag>()
    private val originalEntities = mutableListOf<CompoundTag>()
    val breaking = linkedMapOf<BlockPos, Int>()
    val particles = SceneParticles()
    var mask: Set<BlockPos>? = null
    var sceneTicks = 0L
    var light = 15
    var revision = 0L
        private set

    init {
        renderMode = true
    }

    fun createBackup() {
        originalBlocks.clear()
        originalBlocks.putAll(blocks)
        originalBlockEntities.clear()
        blockEntities.forEach { (pos, entity) ->
            originalBlockEntities[pos] = entity.saveWithFullMetadata(registryAccess())
        }
        originalEntities.clear()
        entities.forEach { originalEntities.add(saveEntity(it)) }
    }

    fun restore() {
        blockEntities.values.forEach { it.setRemoved() }
        blocks.clear()
        blocks.putAll(originalBlocks)
        blockEntities.clear()
        renderedBlockEntities.clear()
        entities.forEach { it.discard() }
        entities.clear()
        breaking.clear()
        particles.clear()
        originalEntities.forEach { loadEntity(it.copy()) }
        originalBlockEntities.forEach { (pos, tag) ->
            BlockEntity.loadStatic(pos, originalBlocks.getValue(pos), tag.copy(), registryAccess())
                ?.let {
                    onBEadded(it, pos)
                    blockEntities[pos] = it
                    renderedBlockEntities.add(it)
                }
        }
        sceneTicks = 0
        revision++
    }

    fun restoreBlocks(positions: Set<BlockPos>) {
        positions.forEach { pos ->
            setBlock(pos, originalBlocks[pos] ?: Blocks.AIR.defaultBlockState(), 0)
            originalBlockEntities[pos]?.let { loadBlockEntity(pos, it.copy()) }
        }
    }

    fun loadBlockEntity(pos: BlockPos, tag: CompoundTag) {
        val current = getBlockEntity(pos) ?: return
        val input = current.saveWithFullMetadata(registryAccess()).merge(tag)
        input.putInt("x", pos.x)
        input.putInt("y", pos.y)
        input.putInt("z", pos.z)
        val replacement =
            BlockEntity.loadStatic(pos, getBlockState(pos), input, registryAccess())
                ?: error("Invalid block entity at $pos")
        current.setRemoved()
        renderedBlockEntities.remove(current)
        onBEadded(replacement, pos)
        blockEntities[pos] = replacement
        renderedBlockEntities.add(replacement)
        revision++
    }

    fun saveEntity(entity: Entity): CompoundTag {
        val output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registryAccess())
        entity.save(output)
        return output.buildResult()
    }

    fun loadEntity(tag: CompoundTag): Entity {
        val entity =
            EntityType.create(
                    TagValueInput.create(ProblemReporter.DISCARDING, registryAccess(), tag),
                    this,
                    EntitySpawnReason.LOAD,
                )
                .orElseThrow { IllegalArgumentException("Invalid scene entity") }
        addFreshEntity(entity)
        return entity
    }

    fun tickEntities(excluded: Set<Entity>) {
        entities.toList().forEach { entity ->
            if (entity !in excluded) {
                entity.xOld = entity.x
                entity.yOld = entity.y
                entity.zOld = entity.z
                entity.tickCount++
                entity.tick()
            }
            if (!entity.isAlive || entity.y < -128) {
                entity.discard()
                entities.remove(entity)
            }
        }
        particles.tick()
    }

    override fun addParticle(
        data: ParticleOptions,
        x: Double,
        y: Double,
        z: Double,
        mx: Double,
        my: Double,
        mz: Double,
    ) {
        ModClientHooksHelper.INSTANCE.createParticleFromData(data, clientWorld, x, y, z, mx, my, mz)
            ?.let(particles::add)
    }

    override fun addAlwaysVisibleParticle(
        data: ParticleOptions,
        x: Double,
        y: Double,
        z: Double,
        mx: Double,
        my: Double,
        mz: Double,
    ) = addParticle(data, x, y, z, mx, my, mz)

    fun destroyEffects(pos: BlockPos, state: BlockState) {
        if (state.isAir) return
        for (x in 0..3) for (y in 0..3) for (z in 0..3) {
            val dx = (x + .5) / 4
            val dy = (y + .5) / 4
            val dz = (z + .5) / 4
            addParticle(
                BlockParticleOption(ParticleTypes.BLOCK, state),
                pos.x + dx,
                pos.y + dy,
                pos.z + dz,
                dx - .5,
                dy - .5,
                dz - .5,
            )
        }
    }

    override fun getBlockState(pos: BlockPos): BlockState =
        if (mask?.contains(pos) == false) Blocks.AIR.defaultBlockState()
        else super.getBlockState(pos)

    override fun setBlock(pos: BlockPos, state: BlockState, flags: Int): Boolean {
        val changed = super.setBlock(pos, state, flags)
        if (changed) revision++
        return changed
    }

    override fun setBlock(pos: BlockPos, state: BlockState, flags: Int, limit: Int): Boolean =
        setBlock(pos, state, flags)

    override fun getGameTime(): Long = sceneTicks

    override fun getBrightness(layer: LightLayer, pos: BlockPos): Int = light

    override fun getRawBrightness(pos: BlockPos, darkening: Int): Int = light

    override fun getMaxLocalRawBrightness(pos: BlockPos): Int = light

    override fun cardinalLighting(): CardinalLighting = SCENE_LIGHTING

    override fun getBlockTint(pos: BlockPos, color: ColorResolver): Int =
        (level as? BlockAndTintGetter)?.getBlockTint(pos, color) ?: -1

    override fun getChunkForCollisions(x: Int, z: Int) = this

    override fun processBlockStateForPrinting(state: BlockState): BlockState = state

    override fun hasChunkAt(pos: BlockPos): Boolean = true

    override fun hasChunk(x: Int, z: Int): Boolean = true

    override fun isLoaded(pos: BlockPos): Boolean = true

    override fun hasNearbyAlivePlayer(x: Double, y: Double, z: Double, distance: Double): Boolean =
        true

    fun clear() {
        blockEntities.values.forEach { it.setRemoved() }
        originalBlocks.clear()
        originalBlockEntities.clear()
        originalEntities.clear()
        breaking.clear()
        particles.close()
        entities.forEach { it.discard() }
        blocks.clear()
        blockEntities.clear()
        renderedBlockEntities.clear()
        entities.clear()
        mask = null
    }

    companion object {
        private val SCENE_LIGHTING = CardinalLighting(0.4f, 1f, 0.7f, 1f, 0.5f, 0.6f)
    }
}
