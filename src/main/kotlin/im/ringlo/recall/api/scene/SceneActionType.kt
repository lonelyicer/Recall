package im.ringlo.recall.api.scene

import com.google.gson.JsonObject

/**
 * Common-side parameter contract for a scene action; does not execute client animations.
 *
 * Register instances through your own NeoForge `DeferredRegister` using [SceneActions.KEY]. Keep
 * the returned holder to reference the action from Datagen and client handler registration without
 * repeating its ID. Client handlers are registered separately through
 * `RegisterSceneInstructionsEvent`.
 *
 * Validation runs during resource loading, snapshot decoding, and Datagen, and may run repeatedly
 * on the same data. Validators should only read parameters and throw on invalid input. They must
 * not mutate parameters, access client-only classes, or change game state. The loader handles
 * `type`, `at`, and `duration` separately; these fields are not part of the parameter object.
 *
 * @property validate parameter validator. The default adds no constraints; custom actions should
 *   check their own required fields and value ranges.
 */
class SceneActionType(val validate: (JsonObject) -> Unit = {})
