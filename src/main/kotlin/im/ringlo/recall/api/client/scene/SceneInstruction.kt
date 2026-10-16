package im.ringlo.recall.api.client.scene

import im.ringlo.recall.client.scene.SceneInstance

/**
 * Client execution instance for one JSON timeline action, created by a factory registered through
 * [RegisterSceneInstructionsEvent].
 *
 * Each step in each scene must have a separate instance. Playback advances at `20 Hz`; seeking
 * forward may execute multiple logical ticks within one rendered frame. Seeking backward restores
 * the initial world state, resets instructions, and replays tick by tick. Implementations must
 * support repeated execution without relying on wall-clock time or stale playback caches.
 *
 * The normal order is [reset], [start], one or more [tick] calls, then [finish]. Initial scene
 * construction also calls [reset]. A zero-duration instruction runs `start`, `tick(progress = 1)`,
 * and `finish` in the same logical tick. All callbacks are no-ops by default.
 *
 * Only modify the supplied scene's isolated state. Do not modify the real world or send packets
 * that change game state.
 */
abstract class SceneInstruction {
    /**
     * Called once when the starting tick is reached, immediately before that tick's [tick]
     * callback.
     *
     * Capture animation starting values or create scene elements here. Called again during replay
     * after seeking backward.
     *
     * @param scene the isolated scene currently being played.
     */
    open fun start(scene: SceneInstance) = Unit

    /**
     * Called every logical tick while the instruction is active, not every rendered frame.
     *
     * @param scene the current scene instance.
     * @param progress normalized progress in `0..1`, quantized to logical ticks; zero-duration
     *   instructions receive `1` immediately.
     */
    open fun tick(scene: SceneInstance, progress: Float) = Unit

    /**
     * Called after [tick] returns with `progress = 1`, at most once per playback run.
     *
     * Commit the final animation state here. Closing a scene early does not guarantee this
     * callback, so it must not be used as a general resource cleanup hook.
     *
     * @param scene the current scene instance.
     */
    open fun finish(scene: SceneInstance) = Unit

    /**
     * Clears instruction-local playback caches during initialization or backward seeking, after the
     * world and built-in scene state have been restored.
     *
     * Must work even if [start] was never called. Discard references to entities, sections, or
     * other objects from the previous world state. Replay reuses this instruction instance instead
     * of calling the factory again. This is not a scene-close callback.
     *
     * @param scene the scene restored to its initial state, ready for replay.
     */
    open fun reset(scene: SceneInstance) = Unit
}
