package im.ringlo.recall.client.ui.components.scene

interface ScenePlayback {
    val currentTime: Float
    val totalTime: Float
    val keyframes: List<Float>

    fun seekToTime(seconds: Float)
}
