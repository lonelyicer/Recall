package im.ringlo.recall.api.scene

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.Vec3

/**
 * Common-side helpers for reading, encoding, and range-checking action parameters.
 *
 * Readers use defaults only for missing fields. Explicit JSON `null` and incorrect JSON types are
 * not guaranteed to fall back; conversion failures propagate the corresponding Gson exceptions.
 * These helpers do not enforce required fields. Custom actions should check them in
 * [SceneActionType.validate].
 */
object SceneArguments {
    /**
     * Reads a finite float and checks its absolute limit. The default value is also validated.
     *
     * @param json action parameter object.
     * @param key numeric field name.
     * @param fallback value used when the field is missing; defaults to `0`.
     * @param limit inclusive maximum absolute value; defaults to `1024`.
     * @return the validated value.
     * @throws IllegalArgumentException if the value is non-finite or its absolute value exceeds
     *   [limit].
     */
    fun number(json: JsonObject, key: String, fallback: Float = 0f, limit: Float = 1024f): Float {
        val value = json.get(key)?.asFloat ?: fallback
        require(value.isFinite() && kotlin.math.abs(value) <= limit) {
            "Invalid scene $key: $value"
        }
        return value
    }

    /**
     * Reads a vector from an `[x, y, z]` array.
     *
     * @param json action parameter object.
     * @param key vector field name.
     * @param fallback vector returned unchanged when the field is missing; it is not range-checked.
     * @return a vector with finite coordinates whose absolute values are at most `4096`, or
     *   [fallback].
     * @throws IllegalArgumentException if the array has a length other than three or contains
     *   invalid coordinates.
     */
    fun vector(json: JsonObject, key: String, fallback: Vec3 = Vec3.ZERO): Vec3 {
        val value = json.getAsJsonArray(key) ?: return fallback
        require(value.size() == 3) { "$key requires three coordinates" }
        val values = value.map { it.asDouble }
        require(values.all { it.isFinite() && kotlin.math.abs(it) <= 4096 }) {
            "Invalid scene $key"
        }
        return Vec3(values[0], values[1], values[2])
    }

    /**
     * Reads integer block coordinates without flooring fractional values.
     *
     * @param json action parameter object.
     * @param key coordinate field name; defaults to `block`.
     * @return the specified position, or the origin when the field is missing.
     * @throws IllegalArgumentException if the coordinates violate [vector]'s constraints or any
     *   coordinate is not an integer.
     */
    fun position(json: JsonObject, key: String = "block"): BlockPos {
        val value = vector(json, key)
        require(listOf(value.x, value.y, value.z).all { it == it.toInt().toDouble() }) {
            "$key requires integers"
        }
        return BlockPos(value.x.toInt(), value.y.toInt(), value.z.toInt())
    }

    /**
     * Reads text using Gson's string conversion and checks its length, including the default value.
     *
     * @param json action parameter object.
     * @param key text field name.
     * @param fallback text used when the field is missing; defaults to an empty string.
     * @return text containing at most `8192` UTF-16 code units.
     * @throws IllegalArgumentException if the text exceeds the length limit.
     */
    fun text(json: JsonObject, key: String, fallback: String = ""): String =
        (json.get(key)?.asString ?: fallback).also {
            require(it.length <= 8192) { "Scene $key is too long" }
        }

    /**
     * Parses an action ID, using `recall` rather than Minecraft's default namespace when omitted.
     *
     * This is intended for decoding resource data. Prefer typed [SceneActions] entries when writing
     * Datagen code.
     *
     * @param value an ID such as `reveal`, `world/show_section`, or `example:pulse`.
     * @return the parsed ID; this method does not check whether it is registered.
     */
    fun id(value: String): Identifier =
        Identifier.parse(if (':' in value) value else "recall:$value")

    /**
     * Reads an explicit block selection and deduplicates overlapping positions.
     *
     * The `selection` array takes precedence. Each object defines an inclusive box using `from` and
     * an optional `to`; omitting `to` selects one block. The array must contain `1..128` boxes,
     * with coordinates in `-128..128`. Neither a single box nor the deduplicated selection may
     * exceed `32768` blocks.
     *
     * Without `selection`, a single `block` is read using only [position]'s constraints. If neither
     * field exists, this method returns `null`. It does not resolve runtime selection rules such as
     * `group`, `link`, or the entire scene.
     *
     * @param json action parameter object.
     * @return a separate set of immutable positions, or `null` when no explicit selection exists;
     *   `null` is not an empty selection.
     * @throws IllegalArgumentException if box count, coordinates, endpoint order, or size is
     *   invalid.
     */
    fun selection(json: JsonObject): Set<BlockPos>? {
        val boxes =
            json.getAsJsonArray("selection")
                ?: return json.get("block")?.let { setOf(position(json)) }
        require(boxes.size() in 1..128) { "Invalid scene selection count" }
        val result = linkedSetOf<BlockPos>()
        boxes.forEach { element ->
            val box = element.asJsonObject
            val from = position(box, "from")
            val to = if (box.has("to")) position(box, "to") else from
            require(from.x <= to.x && from.y <= to.y && from.z <= to.z) {
                "Reversed scene selection"
            }
            require(listOf(from.x, from.y, from.z, to.x, to.y, to.z).all { it in -128..128 }) {
                "Scene selection exceeds coordinate limits"
            }
            require((to.x - from.x + 1L) * (to.y - from.y + 1L) * (to.z - from.z + 1L) <= 32768) {
                "Scene selection is too large"
            }
            BlockPos.betweenClosed(from, to).forEach { result.add(it.immutable()) }
            require(result.size <= 32768) { "Scene selection is too large" }
        }
        return result
    }

    /**
     * Encodes a vector as a new `[x, y, z]` JSON array without range-checking its coordinates.
     *
     * @param vector vector to encode.
     * @return a separate three-element array.
     */
    fun array(vector: Vec3) =
        JsonArray().apply {
            add(vector.x)
            add(vector.y)
            add(vector.z)
        }
}
