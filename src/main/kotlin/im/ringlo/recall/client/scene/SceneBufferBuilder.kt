package im.ringlo.recall.client.scene

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.MeshData
import net.createmod.catnip.api.client.render.SuperByteBufferBuilder
import net.createmod.catnip.api.client.render.model.ShadeSeparatedResultConsumer

class SceneBufferBuilder(private val pipeline: RenderPipeline) :
    SuperByteBufferBuilder(), ShadeSeparatedResultConsumer {
    init {
        prepare()
    }

    override fun accept(pipeline: RenderPipeline, shaded: Boolean, data: MeshData) {
        if (pipeline == this.pipeline) add(data, shaded)
    }
}
