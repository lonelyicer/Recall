package im.ringlo.recall

import im.ringlo.recall.api.scene.SceneActions
import im.ringlo.recall.compat.CatnipArguments
import im.ringlo.recall.network.RecallNetwork
import im.ringlo.recall.server.RecallServerEvents
import net.minecraft.resources.Identifier
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

@Mod(Recall.ID)
class Recall(modBus: IEventBus) {
    init {
        SceneActions.register(modBus)
        // Temporary
        CatnipArguments.register(modBus)
        modBus.addListener(RecallNetwork::register)
        NeoForge.EVENT_BUS.addListener(RecallServerEvents::addReloadListener)
        NeoForge.EVENT_BUS.addListener(RecallServerEvents::syncDatapackContent)
        LOGGER.info("Recall initialized")
    }

    companion object {
        const val ID = "recall"
        val LOGGER: Logger = LogManager.getLogger(ID)

        fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(ID, path)
    }
}
