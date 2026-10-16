package im.ringlo.recall.network

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

object RecallNetwork {
    private const val PROTOCOL_VERSION = "1"

    @JvmStatic
    fun register(event: RegisterPayloadHandlersEvent) {
        event
            .registrar(PROTOCOL_VERSION)
            .optional()
            .playToClient(RecallContentPayload.TYPE, RecallContentPayload.STREAM_CODEC)
    }
}
