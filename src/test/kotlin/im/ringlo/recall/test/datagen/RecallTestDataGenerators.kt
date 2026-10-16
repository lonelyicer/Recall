package im.ringlo.recall.test.datagen

import im.ringlo.recall.test.RecallTestMod
import net.minecraft.server.packs.PackType
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.data.event.GatherDataEvent

@EventBusSubscriber(modid = RecallTestMod.ID)
object RecallTestDataGenerators {
    @SubscribeEvent
    fun gather(event: GatherDataEvent.Server) {
        event.addProvider(
            RecallTestContentProvider(
                event.generator.packOutput,
                event.getResourceManager(PackType.SERVER_DATA),
            )
        )
    }
}
