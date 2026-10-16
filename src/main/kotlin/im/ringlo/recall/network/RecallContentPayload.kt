package im.ringlo.recall.network

import im.ringlo.recall.Recall
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

data class RecallContentPayload(
    val epoch: Long,
    val revision: Long,
    val chunkIndex: Int,
    val chunkCount: Int,
    val json: String,
) : CustomPacketPayload {
    init {
        require(chunkCount in 1..MAX_CHUNKS)
        require(chunkIndex in 0 until chunkCount)
        require(json.length <= MAX_CHUNK_LENGTH)
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    private fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(epoch)
        buffer.writeVarLong(revision)
        buffer.writeVarInt(chunkIndex)
        buffer.writeVarInt(chunkCount)
        buffer.writeUtf(json, MAX_CHUNK_LENGTH)
    }

    companion object {
        const val MAX_CHUNK_LENGTH = 30_000
        const val MAX_CHUNKS = 256
        val TYPE = CustomPacketPayload.Type<RecallContentPayload>(Recall.id("content_snapshot"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, RecallContentPayload> =
            StreamCodec.ofMember(RecallContentPayload::encode, ::decode)

        private fun decode(buffer: RegistryFriendlyByteBuf): RecallContentPayload =
            RecallContentPayload(
                buffer.readVarLong(),
                buffer.readVarLong(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readUtf(MAX_CHUNK_LENGTH),
            )
    }
}
