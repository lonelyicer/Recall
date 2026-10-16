package im.ringlo.recall.api.client.scene

import im.ringlo.recall.api.scene.SceneActionType
import im.ringlo.recall.api.scene.SceneActions
import im.ringlo.recall.content.ContentSnapshot
import net.minecraft.resources.Identifier
import net.neoforged.bus.api.Event
import net.neoforged.fml.event.IModBusEvent
import net.neoforged.neoforge.registries.DeferredHolder

/**
 * Mod event bus event for installing client instruction factories for registered scene actions.
 *
 * Subscribe on the client only. This event is posted lazily when scene instructions are first
 * created, after the built-in factories are installed. The resulting factory map is cached rather
 * than rebuilt each time a scene opens. Action types must already be registered on the common side
 * using [SceneActions.KEY], or [register] will fail.
 *
 * Existing built-in or extension factories cannot be replaced. This event neither registers action
 * types nor executes client instructions during Datagen.
 */
class RegisterSceneInstructionsEvent
internal constructor(
    private val handlers: MutableMap<Identifier, (ContentSnapshot.SceneStep) -> SceneInstruction>
) : Event(), IModBusEvent {
    /**
     * Registers a factory using the holder returned by the action's `DeferredRegister`.
     *
     * Prefer this overload to avoid repeating action IDs. Factory lifetime and parameter handling
     * are the same as in the ID-based overload.
     *
     * @param type the action's deferred registry entry.
     * @param factory creates a separate client instruction for each supplied step.
     * @throws IllegalArgumentException if the holder belongs to a different registry.
     * @throws IllegalStateException if the action is unknown or already has a client factory.
     */
    fun register(
        type: DeferredHolder<SceneActionType, out SceneActionType>,
        factory: (ContentSnapshot.SceneStep) -> SceneInstruction,
    ) {
        require(type.key.isFor(SceneActions.KEY)) { "Not a scene action holder: ${type.key}" }
        register(type.id, factory)
    }

    /**
     * Registers a factory for a dynamically supplied action ID.
     *
     * The factory receives a decoded, validated step. Starting time and effective duration are in
     * seconds; parameters do not include reserved timeline fields. Treat parameters as read-only
     * and create a separate [SceneInstruction] for every invocation. Factories are called per scene
     * instance and step, not again when seeking backward.
     *
     * @param id fully qualified ID matching the common-side [SceneActions] entry.
     * @param factory creates a client instruction from step data.
     * @throws IllegalStateException if the action is unknown or already has a client factory.
     */
    fun register(id: Identifier, factory: (ContentSnapshot.SceneStep) -> SceneInstruction) {
        SceneActions.require(id)
        check(handlers.putIfAbsent(id, factory) == null) {
            "Duplicate scene instruction handler $id"
        }
    }
}
