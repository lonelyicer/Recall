package im.ringlo.recall.server

import im.ringlo.recall.Recall
import im.ringlo.recall.content.ContentCodec
import im.ringlo.recall.network.RecallContentPayload
import net.neoforged.neoforge.event.AddServerReloadListenersEvent
import net.neoforged.neoforge.event.OnDatapackSyncEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.resource.ListenerKey

object RecallServerEvents {
    private val sessionEpoch = System.nanoTime()
    val LISTENER_KEY: ListenerKey<RecallServerReloadListener> =
        ListenerKey.create(Recall.id("server_content"))

    @JvmStatic
    fun addReloadListener(event: AddServerReloadListenersEvent) {
        event.addRetainedListener(LISTENER_KEY, RecallServerReloadListener())
    }

    @JvmStatic
    fun syncDatapackContent(event: OnDatapackSyncEvent) {
        val listener = event.playerList.server.serverResources.managers().getListener(LISTENER_KEY)
        val snapshot = listener.snapshot()
        val chunks =
            ContentCodec.encodeSnapshot(snapshot).chunked(RecallContentPayload.MAX_CHUNK_LENGTH)
        require(chunks.size <= RecallContentPayload.MAX_CHUNKS) {
            "Recall snapshot exceeds the chunk limit"
        }
        event.relevantPlayers.forEach { player ->
            val payloads = chunks.mapIndexed { index, chunk ->
                RecallContentPayload(sessionEpoch, snapshot.revision, index, chunks.size, chunk)
            }
            PacketDistributor.sendToPlayer(
                player,
                payloads.first(),
                *payloads.drop(1).toTypedArray(),
            )
        }
    }
}
