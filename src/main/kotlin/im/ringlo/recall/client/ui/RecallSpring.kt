package im.ringlo.recall.client.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp

class RecallSpring(private val response: Float) {
    var value = 0f
        private set

    var target = 0f
        private set

    private var velocity = 0f

    fun snap(value: Float) {
        this.value = value
        target = value
        velocity = 0f
    }

    fun setTarget(value: Float) {
        target = value.coerceIn(0f, 1f)
    }

    fun tick(deltaSeconds: Float) {
        if (value == target && velocity == 0f) return
        val omega = (PI * 2.0 / response).toFloat()
        val displacement = value - target
        val impulse = velocity + omega * displacement
        val dt = deltaSeconds.coerceIn(0f, 0.1f)
        val decay = exp(-omega * dt)
        value = target + (displacement + impulse * dt) * decay
        velocity = (velocity - omega * impulse * dt) * decay
        if (settled()) snap(target)
    }

    fun settled(): Boolean = abs(target - value) < 0.001f && abs(velocity) < 0.001f
}
