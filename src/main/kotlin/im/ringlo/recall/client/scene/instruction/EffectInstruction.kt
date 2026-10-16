package im.ringlo.recall.client.scene.instruction

import com.mojang.brigadier.StringReader
import im.ringlo.recall.api.client.scene.SceneInstruction
import im.ringlo.recall.api.scene.SceneArguments
import im.ringlo.recall.client.scene.SceneInstance
import im.ringlo.recall.content.ContentSnapshot
import net.minecraft.commands.arguments.ParticleArgument
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.phys.Vec3

class EffectInstruction(private val step: ContentSnapshot.SceneStep) : SceneInstruction() {
    private var emitted = 0
    private var remainder = 0f
    private var particle: ParticleOptions? = null

    override fun start(scene: SceneInstance) {
        emitted = 0
        remainder = 0f
        val p = step.parameters
        particle =
            when (step.action.substringAfter('/')) {
                "emit_particles" ->
                    ParticleArgument.readParticle(
                        StringReader(p.get("particle").asString),
                        scene.world.registryAccess(),
                    )
                "indicate_success" -> ParticleTypes.HAPPY_VILLAGER
                else -> DustParticleOptions(p.get("color")?.asInt ?: 0xff0000, 1f)
            }
    }

    override fun tick(scene: SceneInstance, progress: Float) {
        val p = step.parameters
        val cycles =
            (p.get("cycles")?.asInt ?: if (step.duration == 0f) 1 else (step.duration * 20).toInt())
                .coerceIn(1, 12000)
        if (emitted++ >= cycles) return
        val amount =
            SceneArguments.number(
                    p,
                    "amount",
                    if (step.action.endsWith("emit_particles")) 1f else 10f,
                )
                .coerceIn(0f, 256f)
        remainder += amount
        val location =
            if (p.has("position")) SceneArguments.vector(p, "position")
            else Vec3.atCenterOf(SceneArguments.position(p))
        val motion = SceneArguments.vector(p, "motion")
        while (remainder >= 1f) {
            val inside =
                p.get("within_block")?.asBoolean == true || !step.action.endsWith("emit_particles")
            val position =
                if (inside)
                    location.add(
                        scene.random.nextDouble() - .5,
                        scene.random.nextDouble() - .5,
                        scene.random.nextDouble() - .5,
                    )
                else location
            scene.world.addParticle(
                particle!!,
                position.x,
                position.y,
                position.z,
                motion.x,
                motion.y,
                motion.z,
            )
            remainder--
        }
    }
}
