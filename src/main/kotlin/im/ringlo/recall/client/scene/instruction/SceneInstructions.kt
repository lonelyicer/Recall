package im.ringlo.recall.client.scene.instruction

import im.ringlo.recall.api.client.scene.RegisterSceneInstructionsEvent
import im.ringlo.recall.api.client.scene.SceneInstruction
import im.ringlo.recall.api.scene.SceneActions
import im.ringlo.recall.content.ContentSnapshot
import net.minecraft.resources.Identifier
import net.neoforged.fml.ModLoader

object SceneInstructions {
    private var handlers: Map<Identifier, (ContentSnapshot.SceneStep) -> SceneInstruction>? = null

    private fun registry(): Map<Identifier, (ContentSnapshot.SceneStep) -> SceneInstruction> {
        handlers?.let {
            return it
        }
        val factories = linkedMapOf<Identifier, (ContentSnapshot.SceneStep) -> SceneInstruction>()
        SceneActions.builtins.forEach { type ->
            val name = type.id.path
            factories[type.id] = { step ->
                when {
                    name.startsWith("world/") &&
                        name.substringAfter('/') in WorldInstruction.ACTIONS ->
                        WorldInstruction(step)
                    name.startsWith("world/") &&
                        name.substringAfter('/') in EntityInstruction.ACTIONS ->
                        EntityInstruction(step)
                    name.startsWith("special/") -> SpecialInstruction(step)
                    name.startsWith("overlay/") ||
                        name in setOf("caption", "tooltip", "highlight") -> OverlayInstruction(step)
                    name.startsWith("effects/") -> EffectInstruction(step)
                    name in SectionInstruction.ACTIONS -> SectionInstruction(step)
                    else -> SceneControlInstruction(step)
                }
            }
        }
        ModLoader.postEvent(RegisterSceneInstructionsEvent(factories))
        handlers = factories.toMap()
        return handlers!!
    }

    fun create(step: ContentSnapshot.SceneStep): SceneInstruction =
        checkNotNull(registry()[step.type]) { "No client instruction handler for ${step.type}" }(
            step
        )
}
