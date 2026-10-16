package im.ringlo.recall.client

import im.ringlo.recall.Recall
import im.ringlo.recall.client.scene.SceneRenderState
import im.ringlo.recall.client.scene.SceneRenderer
import im.ringlo.recall.client.ui.components.nodecard.markdown.MarkdownImages
import im.ringlo.recall.content.ClientContentStore
import im.ringlo.recall.network.RecallContentPayload
import im.ringlo.recall.network.RecallTransferAssembler
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent

@EventBusSubscriber(modid = Recall.ID, value = [Dist.CLIENT])
object RecallClientEvents {
    @SubscribeEvent
    fun registerKeys(event: RegisterKeyMappingsEvent) {
        RecallKeyMappings.register(event)
    }

    @SubscribeEvent
    fun registerReloadListeners(event: AddClientReloadListenersEvent) {
        event.addListener(Recall.id("markdown_images"), MarkdownImages)
    }

    @SubscribeEvent
    fun registerSceneRenderer(event: RegisterPictureInPictureRenderersEvent) {
        event.register(SceneRenderState::class.java, ::SceneRenderer)
    }

    @SubscribeEvent
    fun registerPayloads(event: RegisterClientPayloadHandlersEvent) {
        event.register(RecallContentPayload.TYPE) { payload, context ->
            try {
                RecallTransferAssembler.accept(payload)
            } catch (exception: Exception) {
                Recall.LOGGER.error("Rejected Recall content chunk", exception)
            }
        }
    }

    @SubscribeEvent
    fun loggingOut(event: ClientPlayerNetworkEvent.LoggingOut) {
        ClientContentStore.clear()
        RecallTransferAssembler.clear()
    }
}
