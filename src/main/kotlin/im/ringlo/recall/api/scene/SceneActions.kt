package im.ringlo.recall.api.scene

import com.google.gson.JsonObject
import im.ringlo.recall.Recall
import net.minecraft.core.Registry
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.NewRegistryEvent
import net.neoforged.neoforge.registries.RegistryBuilder

/**
 * Common-side NeoForge registry and typed references for scene actions.
 *
 * Use constants such as [REVEAL] or [OVERLAY_SHOW_TEXT] when building timelines. Each constant is
 * the actual deferred registry entry, not a separate list of string aliases. JSON resources still
 * store namespaced action IDs, so the data format remains unchanged.
 *
 * Extensions register their own action types on their own mod event bus, rather than calling
 * [register]. Keep the returned holder and pass it directly to a timeline or client handler
 * registry:
 * ```kotlin
 * val actions = DeferredRegister.create(SceneActions.KEY, "example")
 * val pulse = actions.register("pulse") { _ ->
 *     SceneActionType { parameters ->
 *         require(SceneArguments.number(parameters, "strength", 1f) >= 0f)
 *     }
 * }
 * actions.register(modBus)
 * ```
 *
 * Registering an action type defines its parameter contract only. Playback also requires a client
 * instruction factory registered under the same ID through `RegisterSceneInstructionsEvent`.
 */
object SceneActions {
    /** Registry key used to create a `DeferredRegister` for custom action types. */
    val KEY: ResourceKey<Registry<SceneActionType>> =
        ResourceKey.createRegistryKey(Recall.id("scene_action"))

    /**
     * Action registry whose numeric entry IDs are synchronized with clients. Query it only after
     * NeoForge has completed registration.
     *
     * Synchronization does not transmit validators or client handlers; both sides must still
     * install the mods that provide the actions.
     */
    val REGISTRY: Registry<SceneActionType> = RegistryBuilder(KEY).sync(true).create()

    private val entries = DeferredRegister.create(KEY, Recall.ID)

    /** Keeps the timeline active for the specified duration without changing scene state. */
    val WAIT = action("wait")

    /** Adds a seekable progress-bar keyframe. */
    val KEYFRAME = action("keyframe")

    /** Adds a keyframe after the action's delay. */
    val LAZY_KEYFRAME = action("lazy_keyframe")

    /** Marks scene playback as finished. */
    val MARK_AS_FINISHED = action("mark_as_finished")

    /** Hides all scene sections. */
    val HIDE_ALL = action("hide_all")

    /** Disables the scene shadow. */
    val REMOVE_SHADOW = action("remove_shadow")

    /** Reveals the scene base plate. */
    val SHOW_BASE_PLATE = action("show_base_plate")

    /** Reveals the structure for debugging. */
    val DEBUG_SCHEMATIC = action("debug/schematic")

    /** Sets the scene title; requires `text`. */
    val TITLE = action("title", "text")

    /** Displays a caption overlay; requires `text`. */
    val CAPTION = action("caption", "text")

    /** Displays a tooltip overlay; requires `text`. */
    val TOOLTIP = action("tooltip", "text")

    /** Displays a text overlay; requires `text`. */
    val OVERLAY_SHOW_TEXT = action("overlay/show_text", "text")

    /** Displays text with an outline; requires `text`. */
    val OVERLAY_SHOW_OUTLINE_WITH_TEXT = action("overlay/show_outline_with_text", "text")

    /** Reveals a section, optionally declaring its selected blocks. */
    val REVEAL = action("reveal")

    /** Hides a section, optionally declaring its selected blocks. */
    val HIDE = action("hide")

    /** Moves a section by its `offset`. */
    val MOVE = action("move")

    /** Rotates a section by its `rotation`. */
    val ROTATE = action("rotate")

    /** Highlights the selected scene blocks. */
    val HIGHLIGHT = action("highlight")

    /** Animates the scene camera using its camera parameters. */
    val CAMERA = action("camera")

    /** Configures the base plate; requires `size`. */
    val CONFIGURE_BASE_PLATE = action("configure_base_plate", "size")

    /** Sets the scene view scale; requires `scale`. */
    val SCALE_SCENE_VIEW = action("scale_scene_view", "scale")

    /** Sets the vertical scene offset; requires `offset_y`. */
    val SET_SCENE_OFFSET_Y = action("set_scene_offset_y", "offset_y")

    /** Controls the next-scene affordance; requires `enabled`. */
    val SET_NEXT_UP_ENABLED = action("set_next_up_enabled", "enabled")

    /** Rotates camera yaw; requires `degrees`. */
    val ROTATE_CAMERA_Y = action("rotate_camera_y", "degrees")

    /** Reveals a selected world section. */
    val WORLD_SHOW_SECTION = action("world/show_section")

    /** Reveals selected blocks and merges them into an existing section. */
    val WORLD_SHOW_SECTION_AND_MERGE = action("world/show_section_and_merge")

    /** Reveals an independent section identified by `link`. */
    val WORLD_SHOW_INDEPENDENT_SECTION = action("world/show_independent_section")

    /** Immediately reveals an independent section identified by `link`. */
    val WORLD_SHOW_INDEPENDENT_SECTION_IMMEDIATELY =
        action("world/show_independent_section_immediately")

    /** Hides a selected world section. */
    val WORLD_HIDE_SECTION = action("world/hide_section")

    /** Hides an existing independent section. */
    val WORLD_HIDE_INDEPENDENT_SECTION = action("world/hide_independent_section")

    /** Detaches selected blocks into an independent section identified by `link`. */
    val WORLD_MAKE_SECTION_INDEPENDENT = action("world/make_section_independent")

    /** Restores selected blocks from the initial structure. */
    val WORLD_RESTORE_BLOCKS = action("world/restore_blocks")

    /** Attaches a block to a section; requires `block`. */
    val WORLD_GLUE_BLOCK_ONTO = action("world/glue_block_onto", "block")

    /** Destroys a scene block; requires `block`. */
    val WORLD_DESTROY_BLOCK = action("world/destroy_block", "block")

    /** Advances a block's breaking overlay; requires `block`. */
    val WORLD_INCREMENT_BLOCK_BREAKING_PROGRESS =
        action("world/increment_block_breaking_progress", "block")

    /** Moves a world section; requires `offset`. */
    val WORLD_MOVE_SECTION = action("world/move_section", "offset")

    /** Moves a linked parrot; requires `offset`. */
    val SPECIAL_MOVE_PARROT = action("special/move_parrot", "offset")

    /** Moves a linked minecart; requires `offset`. */
    val SPECIAL_MOVE_CART = action("special/move_cart", "offset")

    /** Rotates a world section; requires `rotation`. */
    val WORLD_ROTATE_SECTION = action("world/rotate_section", "rotation")

    /** Rotates a linked parrot; requires `rotation`. */
    val SPECIAL_ROTATE_PARROT = action("special/rotate_parrot", "rotation")

    /** Rotates a linked minecart; requires `rotation`. */
    val SPECIAL_ROTATE_CART = action("special/rotate_cart", "rotation")

    /** Sets a section's rotation center; requires `anchor`. */
    val WORLD_CONFIGURE_CENTER_OF_ROTATION = action("world/configure_center_of_rotation", "anchor")

    /** Configures a section's stabilization anchor; requires `anchor`. */
    val WORLD_CONFIGURE_STABILIZATION = action("world/configure_stabilization", "anchor")

    /** Sets a single block state; requires `state`. */
    val WORLD_SET_BLOCK = action("world/set_block", "state")

    /** Sets selected block states; requires `state`. */
    val WORLD_SET_BLOCKS = action("world/set_blocks", "state")

    /** Replaces matching block states; requires `state`. */
    val WORLD_REPLACE_BLOCKS = action("world/replace_blocks", "state")

    /** Changes a block's state properties; requires `properties`. */
    val WORLD_MODIFY_BLOCK = action("world/modify_block", "properties")

    /** Changes selected blocks' state properties; requires `properties`. */
    val WORLD_MODIFY_BLOCKS = action("world/modify_blocks", "properties")

    /** Cycles a block-state property; requires `property`. */
    val WORLD_CYCLE_BLOCK_PROPERTY = action("world/cycle_block_property", "property")

    /** Toggles redstone power on selected blocks. */
    val WORLD_TOGGLE_REDSTONE_POWER = action("world/toggle_redstone_power")

    /** Applies block-entity NBT changes; requires `nbt`. */
    val WORLD_MODIFY_BLOCK_ENTITY_NBT = action("world/modify_block_entity_nbt", "nbt")

    /** Applies block-entity changes; requires `nbt`. */
    val WORLD_MODIFY_BLOCK_ENTITY = action("world/modify_block_entity", "nbt")

    /** Modifies a linked scene entity; requires `nbt`. */
    val WORLD_MODIFY_ENTITY = action("world/modify_entity", "nbt")

    /** Modifies matching scene entities; requires `nbt`. */
    val WORLD_MODIFY_ENTITIES = action("world/modify_entities", "nbt")

    /** Modifies scene entities inside the selected area; requires `nbt`. */
    val WORLD_MODIFY_ENTITIES_INSIDE = action("world/modify_entities_inside", "nbt")

    /** Creates a scene entity; requires `entity`. */
    val WORLD_CREATE_ENTITY = action("world/create_entity", "entity")

    /** Creates an item entity; requires `item` and `position`. */
    val WORLD_CREATE_ITEM_ENTITY = action("world/create_item_entity", "item", "position")

    /** Displays an input-control hint; requires `position`. */
    val OVERLAY_SHOW_CONTROLS = action("overlay/show_controls", "position")

    /** Displays a scroll-input hint; requires `position`. */
    val OVERLAY_SHOW_SCROLL_INPUT = action("overlay/show_scroll_input", "position")

    /** Displays a filter-slot hint; requires `position`. */
    val OVERLAY_SHOW_FILTER_SLOT_INPUT = action("overlay/show_filter_slot_input", "position")

    /** Displays a block-centered scroll-input hint; requires `block`. */
    val OVERLAY_SHOW_CENTERED_SCROLL_INPUT = action("overlay/show_centered_scroll_input", "block")

    /** Displays a repeater scroll-input hint; requires `block`. */
    val OVERLAY_SHOW_REPEATER_SCROLL_INPUT = action("overlay/show_repeater_scroll_input", "block")

    /** Animates a bounding-box outline; requires `from` and `to`. */
    val OVERLAY_CHASE_BOUNDING_BOX_OUTLINE =
        action("overlay/chase_bounding_box_outline", "from", "to")

    /** Displays an outline for selected scene blocks. */
    val OVERLAY_SHOW_OUTLINE = action("overlay/show_outline")

    /** Displays a line; requires `from` and `to`. */
    val OVERLAY_SHOW_LINE = action("overlay/show_line", "from", "to")

    /** Displays a thick line; requires `from` and `to`. */
    val OVERLAY_SHOW_BIG_LINE = action("overlay/show_big_line", "from", "to")

    /** Emits scene particles; requires `particle` and `position`. */
    val EFFECTS_EMIT_PARTICLES = action("effects/emit_particles", "particle", "position")

    /** Displays a redstone indicator; requires `block`. */
    val EFFECTS_INDICATE_REDSTONE = action("effects/indicate_redstone", "block")

    /** Displays a success indicator; requires `block`. */
    val EFFECTS_INDICATE_SUCCESS = action("effects/indicate_success", "block")

    /** Creates redstone particles; requires `block`. */
    val EFFECTS_CREATE_REDSTONE_PARTICLES = action("effects/create_redstone_particles", "block")

    /** Creates a scene parrot; requires `position`. */
    val SPECIAL_CREATE_BIRB = action("special/create_birb", "position")

    /** Creates a scene minecart; requires `position`. */
    val SPECIAL_CREATE_CART = action("special/create_cart", "position")

    /** Moves the shared point of interest; requires `position`. */
    val SPECIAL_MOVE_POINT_OF_INTEREST = action("special/move_point_of_interest", "position")

    /** Changes a linked parrot's pose; requires `pose`. */
    val SPECIAL_CHANGE_BIRB_POSE = action("special/change_birb_pose", "pose")

    /** Hides a linked special element. */
    val SPECIAL_HIDE_ELEMENT = action("special/hide_element")

    /**
     * Unmodifiable view of the built-in action holders in declaration order. Custom actions belong
     * in their own mod's deferred register, not in this collection.
     */
    val builtins: Collection<DeferredHolder<SceneActionType, out SceneActionType>>
        get() = entries.entries

    private fun action(
        name: String,
        vararg required: String,
    ): DeferredHolder<SceneActionType, SceneActionType> =
        entries.register(name) { _ ->
            SceneActionType { parameters ->
                required.forEach {
                    require(parameters.has(it)) { "Action recall:$name requires $it" }
                }
                validateParameters(parameters)
            }
        }

    private fun validateParameters(json: JsonObject) {
        require(json.toString().length <= 32768) { "Scene action parameters are too large" }
        listOf("offset", "rotation", "anchor", "position", "motion", "from", "to").forEach {
            SceneArguments.vector(json, it)
        }
        if (json.has("block")) SceneArguments.position(json)
        SceneArguments.selection(json)
        listOf("yaw", "pitch", "degrees", "scale", "offset_y", "amount", "cycles", "size", "width")
            .forEach { SceneArguments.number(json, it) }
        listOf("text", "group", "link", "slot", "state", "pose", "property").forEach {
            SceneArguments.text(json, it)
        }
    }

    /**
     * Attaches registry creation and built-in action registration to Recall's mod event bus.
     *
     * Called once by Recall during mod construction. Extension mods must not call this method
     * again.
     *
     * @param bus Recall's mod event bus, not the global game event bus.
     */
    fun register(bus: IEventBus) {
        bus.addListener { event: NewRegistryEvent -> event.register(REGISTRY) }
        entries.register(bus)
    }

    /**
     * Looks up a registered action type.
     *
     * @param id fully qualified action ID.
     * @return the action's common-side parameter contract.
     * @throws IllegalStateException if the ID is unknown or queried before registration completes.
     */
    fun require(id: Identifier): SceneActionType =
        checkNotNull(REGISTRY.getValue(id)) {
            "Unknown scene action $id; install the mod which registers it"
        }
}
