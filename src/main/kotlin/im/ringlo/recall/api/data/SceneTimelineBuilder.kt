package im.ringlo.recall.api.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import im.ringlo.recall.api.scene.SceneActionType
import im.ringlo.recall.api.scene.SceneActions
import net.minecraft.resources.Identifier
import net.neoforged.neoforge.registries.DeferredHolder

/**
 * Builds scene timeline JSON for Datagen, not runtime scene construction or execution.
 *
 * Add registered actions using [action], with all times expressed in seconds. Up to `2048`
 * instructions are accepted. [build] sorts by absolute starting time without advancing a cursor
 * based on preceding durations. Parameter constraints are checked on insertion; structure, groups,
 * references, and final duration are validated by [RecallDataOutput.scene].
 *
 * ```kotlin
 * val timeline = SceneTimelineBuilder(zeroLayer = 1)
 * timeline.action(SceneActions.REVEAL, duration = .6f)
 * timeline.action(ExampleActions.PULSE, at = 1f, duration = .5f) {
 *     addProperty("strength", 2f)
 * }
 * ```
 *
 * `ExampleActions.PULSE` is the holder returned by the extension's own action deferred register.
 *
 * @property zeroLayer NBT layer treated as scene `y = 0`; must be in `0..127` and below the actual
 *   structure height.
 * @throws IllegalArgumentException if [zeroLayer] is outside `0..127`.
 */
class SceneTimelineBuilder(val zeroLayer: Int = 0) {
    private val entries = mutableListOf<JsonObject>()

    init {
        require(zeroLayer in 0..127) { "Invalid scene zeroLayer $zeroLayer" }
    }

    /**
     * Adds an action using its typed registry entry, avoiding hand-written action IDs.
     *
     * Accepts built-in [SceneActions] constants and holders returned by an extension's own
     * `DeferredRegister`. Timing and parameter constraints match the ID-based overload.
     *
     * @param type the action's deferred registry entry.
     * @param at absolute starting time in seconds from the scene's beginning; defaults to `0`.
     * @param duration authored duration in seconds; `null` omits the field for loader-side
     *   defaults.
     * @param parameters builds action parameters in a new [JsonObject]; reserved timeline fields
     *   must be set through method arguments instead.
     * @throws IllegalArgumentException if the holder is from a different registry, timing or
     *   parameters are invalid, reserved fields are set, or the instruction limit is reached.
     * @throws IllegalStateException if the action is not registered.
     */
    fun action(
        type: DeferredHolder<SceneActionType, out SceneActionType>,
        at: Float = 0f,
        duration: Float? = null,
        parameters: JsonObject.() -> Unit = {},
    ) {
        require(type.key.isFor(SceneActions.KEY)) { "Not a scene action holder: ${type.key}" }
        action(type.id, at, duration, parameters)
    }

    /**
     * Adds an action identified dynamically and immediately validates its common-side parameters.
     * Prefer the typed overload for statically known actions.
     *
     * [at] must be finite and in `0..600`. A non-null [duration] must be finite, non-negative, and
     * satisfy `at + duration <= 600`. Omitting a duration differs from explicitly setting `0`. The
     * loader may still add overlay fade time, so passing these checks does not guarantee a valid
     * final scene duration.
     *
     * Parameters are deep-copied after validation; later changes to JSON objects retained by the
     * parameter block do not alter the inserted entry. There is no string overload: resource text
     * should be parsed explicitly before using this dynamic-ID entry point.
     *
     * @param type fully qualified action ID registered in [SceneActions].
     * @param at absolute starting time in seconds from the scene's beginning; defaults to `0`.
     * @param duration authored resource duration; `null` preserves the absent field instead of
     *   calculating the effective runtime duration early.
     * @param parameters action-specific parameter builder; must not set `type`, `at`, or
     *   `duration`.
     * @throws IllegalArgumentException if timing or parameters are invalid, reserved fields are
     *   set, or `2048` instructions have already been added.
     * @throws IllegalStateException if the action is not registered.
     */
    fun action(
        type: Identifier,
        at: Float = 0f,
        duration: Float? = null,
        parameters: JsonObject.() -> Unit = {},
    ) {
        require(at.isFinite() && at in 0f..600f) { "Invalid scene action time $at" }
        require(
            duration == null || duration.isFinite() && duration >= 0f && at + duration <= 600f
        ) {
            "Invalid scene action duration $duration at $at"
        }
        require(entries.size < 2048) { "Scene exceeds 2048 instructions" }
        val json = JsonObject().apply(parameters)
        require(json.keySet().none { it in setOf("type", "at", "duration") }) {
            "Set type, at and duration through action arguments, not action parameters"
        }
        SceneActions.require(type).validate(json)
        entries +=
            json.deepCopy().apply {
                addProperty("type", type.toString())
                addProperty("at", at)
                duration?.let { addProperty("duration", it) }
            }
    }

    /**
     * Creates resource-format JSON containing `zeroLayer` and `timeline`, without structure or
     * network snapshot fields.
     *
     * Entries are ordered by ascending `at`, preserving insertion order for equal times. Every call
     * returns a fresh deep copy; modifying it does not affect this builder or later results, and
     * existing entries are not cleared. This method does not read NBT, apply default durations, or
     * validate structure references.
     *
     * @return an independent timeline resource object.
     */
    fun build(): JsonObject =
        JsonObject().apply {
            addProperty("zeroLayer", zeroLayer)
            add(
                "timeline",
                JsonArray().apply {
                    entries.sortedBy { it.get("at").asFloat }.forEach { add(it.deepCopy()) }
                },
            )
        }
}
