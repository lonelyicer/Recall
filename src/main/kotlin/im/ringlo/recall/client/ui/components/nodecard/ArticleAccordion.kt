package im.ringlo.recall.client.ui.components.nodecard

import im.ringlo.recall.client.ui.RecallSpring
import net.minecraft.resources.Identifier

class ArticleAccordion {
    private val springs = linkedMapOf<Identifier, RecallSpring>()

    fun update(selected: Identifier?, seconds: Float) {
        if (selected != null) springs.getOrPut(selected) { RecallSpring(0.32f) }
        springs.forEach { (id, spring) ->
            spring.setTarget(if (id == selected) 1f else 0f)
            spring.tick(seconds)
        }
    }

    fun progress(id: Identifier): Float = springs[id]?.value?.coerceIn(0f, 1f) ?: 0f

    fun clear() {
        springs.clear()
    }

    fun settled(): Boolean = springs.values.all { it.settled() }
}
