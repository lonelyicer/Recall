package im.ringlo.recall.content.scene

import im.ringlo.recall.content.ContentSnapshot

object SceneTimeline {
    private val fadingOverlays =
        setOf(
            "overlay/show_text",
            "overlay/show_outline_with_text",
            "overlay/show_controls",
            "caption",
            "tooltip",
        )

    fun fadeDuration(type: net.minecraft.resources.Identifier): Float =
        if (type.namespace == "recall" && type.path in fadingOverlays) .5f else 0f

    fun validateReferences(scene: ContentSnapshot.SceneDefinition) {
        val sections = scene.blocks.mapTo(hashSetOf()) { it.group }.apply { add("main") }
        val entities = hashSetOf<String>()
        scene.steps
            .sortedBy { it.time }
            .filter { it.type.namespace == "recall" }
            .forEach { step ->
                val p = step.parameters
                val action = step.action
                val link = p.get("link")?.asString
                if (
                    action in
                        setOf(
                            "world/show_independent_section",
                            "world/show_independent_section_immediately",
                            "world/make_section_independent",
                        )
                ) {
                    require(!link.isNullOrBlank()) { "$action requires a link" }
                    require(sections.add(link)) { "Duplicate scene section link $link" }
                }
                if (
                    action in
                        setOf(
                            "world/create_entity",
                            "world/create_item_entity",
                            "special/create_birb",
                            "special/create_cart",
                        ) && link != null
                )
                    require(entities.add(link)) { "Duplicate entity link $link" }
                if (
                    action in setOf("reveal", "hide", "move", "rotate", "highlight") ||
                        action.startsWith("world/") && p.has("group")
                )
                    require(step.group in sections) { "Unknown scene group ${step.group}" }
                if (
                    action in
                        setOf(
                            "world/move_section",
                            "world/rotate_section",
                            "world/hide_independent_section",
                            "world/configure_center_of_rotation",
                            "world/configure_stabilization",
                            "world/show_section_and_merge",
                            "world/glue_block_onto",
                        )
                )
                    require((link ?: step.group) in sections) { "Unknown section link $link" }
                if (
                    action in
                        setOf(
                            "world/modify_entity",
                            "special/change_birb_pose",
                            "special/move_parrot",
                            "special/rotate_parrot",
                            "special/move_cart",
                            "special/rotate_cart",
                        )
                )
                    require(link in entities) { "Unknown entity link $link" }
                if (action == "special/hide_element")
                    require(link in entities || link in sections) { "Unknown element link $link" }
                if (action == "show_base_plate") sections.add("base_plate")
                if (action == "debug/schematic") sections.add("debug")
            }
    }
}
