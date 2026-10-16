package im.ringlo.recall.client.scene.instruction

import im.ringlo.recall.api.client.scene.SceneInstruction
import im.ringlo.recall.client.scene.SceneInstance
import im.ringlo.recall.content.ContentSnapshot
import kotlin.math.ceil

class ScheduledInstruction(val step: ContentSnapshot.SceneStep, val instruction: SceneInstruction) {
    val startTick = ceil(step.time * 20.0 - 0.00001).toLong().coerceAtLeast(0)
    private val durationTicks = ceil(step.duration * 20.0 - 0.00001).toLong().coerceAtLeast(0)
    private var started = false
    private var finished = false

    fun tick(scene: SceneInstance, tick: Long) {
        if (finished || tick < startTick) return
        if (!started) {
            started = true
            instruction.start(scene)
        }
        val progress =
            if (durationTicks == 0L) 1f
            else ((tick - startTick).toFloat() / durationTicks).coerceIn(0f, 1f)
        instruction.tick(scene, progress)
        if (progress >= 1) {
            instruction.finish(scene)
            finished = true
        }
    }

    fun reset(scene: SceneInstance) {
        instruction.reset(scene)
        started = false
        finished = false
    }
}
