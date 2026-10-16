package im.ringlo.recall.network

import im.ringlo.recall.Recall
import im.ringlo.recall.content.ClientContentStore
import im.ringlo.recall.content.ContentCodec

object RecallTransferAssembler {
    private var pending: Pending? = null

    @Synchronized
    fun accept(payload: RecallContentPayload) {
        val current = pending
        val transfer =
            if (
                current == null ||
                    current.epoch != payload.epoch ||
                    current.revision != payload.revision ||
                    current.count != payload.chunkCount
            ) {
                Pending(
                        payload.epoch,
                        payload.revision,
                        payload.chunkCount,
                        arrayOfNulls(payload.chunkCount),
                    )
                    .also { pending = it }
            } else current
        transfer.parts[payload.chunkIndex] = payload.json
        if (transfer.parts.all { it != null }) {
            val json = transfer.parts.joinToString(separator = "") { it!! }
            try {
                ClientContentStore.installIfNew(transfer.epoch, ContentCodec.decodeSnapshot(json))
                pending = null
            } catch (exception: Exception) {
                Recall.LOGGER.error("Rejected assembled Recall content snapshot", exception)
                pending = null
            }
        }
    }

    fun clear() {
        synchronized(this) { pending = null }
    }

    private data class Pending(
        val epoch: Long,
        val revision: Long,
        val count: Int,
        val parts: Array<String?>,
    )
}
