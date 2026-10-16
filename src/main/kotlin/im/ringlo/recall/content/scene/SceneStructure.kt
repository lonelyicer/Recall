package im.ringlo.recall.content.scene

import im.ringlo.recall.content.ContentSnapshot
import java.io.InputStream
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.DoubleTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.resources.Identifier
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.util.datafix.DataFixers

class SceneStructure(
    val size: List<Int>,
    val blocks: List<ContentSnapshot.SceneBlock>,
    val entities: List<String>,
) {
    companion object {
        const val MAX_BLOCKS = 32768

        fun read(input: InputStream, zeroLayer: Int): SceneStructure {
            var tag = NbtIo.readCompressed(input, NbtAccounter.create(8L * 1024 * 1024))
            tag.getInt("DataVersion").ifPresent { version ->
                tag =
                    DataFixTypes.STRUCTURE.updateToCurrentVersion(
                        DataFixers.getDataFixer(),
                        tag,
                        version,
                    )
            }
            val size = coordinates(tag.getList("size").orElseThrow())
            require(size.all { it in 1..128 } && zeroLayer in 0 until size[1]) {
                "Invalid structure size or zeroLayer"
            }
            val palette =
                tag.getList("palette").orElseGet {
                    tag.getList("palettes").orElseThrow().getList(0).orElseThrow()
                }
            require(palette.size in 1..MAX_BLOCKS) { "Invalid structure palette size" }
            val states = palette.map { element ->
                val state = element as CompoundTag
                val name = Identifier.parse(state.getString("Name").orElseThrow()).toString()
                val properties = state.getCompoundOrEmpty("Properties")
                val values =
                    properties.keySet().sorted().map { key ->
                        val value = properties.getString(key).orElseThrow()
                        require(
                            key.matches(Regex("[a-z0-9_]+")) && value.matches(Regex("[a-z0-9_-]+"))
                        ) {
                            "Invalid structure state property"
                        }
                        "$key=$value"
                    }
                name + if (values.isEmpty()) "" else values.joinToString(",", "[", "]")
            }
            val occupied = hashSetOf<List<Int>>()
            val entries = tag.getList("blocks").orElseThrow()
            require(entries.size <= 128 * 128 * 128) { "Structure placement list is too large" }
            val blocks = entries.mapNotNull { element ->
                val entry = element as CompoundTag
                val pos = coordinates(entry.getList("pos").orElseThrow())
                require(pos.indices.all { pos[it] in 0 until size[it] }) {
                    "Block outside structure bounds"
                }
                require(occupied.add(pos)) { "Duplicate structure position $pos" }
                val state =
                    states.getOrNull(entry.getInt("state").orElseThrow())
                        ?: error("Invalid structure palette index")
                if (
                    state in
                        setOf(
                            "minecraft:air",
                            "minecraft:cave_air",
                            "minecraft:void_air",
                            "minecraft:structure_void",
                        )
                )
                    null
                else
                    ContentSnapshot.SceneBlock(
                        pos[0],
                        pos[1] - zeroLayer,
                        pos[2],
                        state,
                        nbt = entry.getCompound("nbt").map { it.toString() }.orElse(null),
                    )
            }
            require(blocks.size <= MAX_BLOCKS) { "Structure exceeds $MAX_BLOCKS non-air blocks" }
            val entityTags = tag.getListOrEmpty("entities")
            require(entityTags.size <= 256) { "Structure exceeds 256 entities" }
            val entities = entityTags.map { element ->
                val entry = element as CompoundTag
                val nbt = entry.getCompound("nbt").orElseThrow().copy()
                val position = entry.getList("pos").orElseThrow()
                require(position.size == 3) { "Invalid structure entity position" }
                val xyz = (0..2).map { position.getDouble(it).orElseThrow() }
                require(xyz.all { it.isFinite() && kotlin.math.abs(it) <= 128 }) {
                    "Structure entity outside coordinate limits"
                }
                nbt.put(
                    "Pos",
                    ListTag().apply {
                        add(DoubleTag.valueOf(xyz[0]))
                        add(DoubleTag.valueOf(xyz[1] - zeroLayer))
                        add(DoubleTag.valueOf(xyz[2]))
                    },
                )
                nbt.remove("UUID")
                nbt.toString()
            }
            return SceneStructure(size, blocks, entities)
        }

        private fun coordinates(list: ListTag): List<Int> {
            require(list.size == 3) { "Expected three structure coordinates" }
            return (0..2).map { list.getInt(it).orElseThrow() }
        }
    }
}
