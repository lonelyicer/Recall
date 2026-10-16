package im.ringlo.recall.content

import java.util.concurrent.atomic.AtomicReference

object ClientContentStore {
    private val current = AtomicReference(ContentSnapshot.empty())
    @Volatile private var epoch = 0L

    fun snapshot(): ContentSnapshot = current.get()

    fun connectionEpoch(): Long = epoch

    fun installIfNew(incomingEpoch: Long, snapshot: ContentSnapshot) {
        if (
            incomingEpoch > epoch ||
                incomingEpoch == epoch && snapshot.revision >= current.get().revision
        ) {
            install(incomingEpoch, snapshot)
        }
    }

    fun install(connectionEpoch: Long, snapshot: ContentSnapshot) {
        epoch = connectionEpoch
        current.set(snapshot)
    }

    fun clear() {
        epoch++
        current.set(ContentSnapshot.empty())
    }
}
